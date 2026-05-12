// Copyright (c) 2023-2026 Yuchi Miao <miaoyuchi@ict.ac.cn>
// retroSoC is licensed under Mulan PSL v2.
// You can use this software according to the terms and conditions of the Mulan PSL v2.
// You may obtain a copy of Mulan PSL v2 at:
//             http://license.coscl.org.cn/MulanPSL2
// THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
// EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
// MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
// See the Mulan PSL v2 for more details.
//
// NOTE: for supporting cross page xfer, the max freq of 'sclk' is 84MHz
// when 'sclk' is 144MHz(max, ~6.94ns), according to TRM:
//
// tCLK(min: 6.94ns) is meet, dont have 8'h03 rd oper(min: 30.3ns)
//
// tCH/tCL(min:0.45%, max: 0.55%) tCLK is meet
//
// tKHKL(max: 1.5ns) is meet
//
// tCPH(min: 50ns) -> 50 / 6.94 = ~7.2 so wait cycles need to >= ceil(7.2) = 8,
// so 'r_cfg_wait' = 8 * 2 >= 16 in default, reset value is '18'
// for lower freq, can modify this value for performance tuning
//
// tCEM(max: 8us) is enough long for just 32bits xfer:
// QPI mode: (32(cmd+addr) + 32(data)) / 4 = 16 cycles + 6 wait cycles = 22 cycles
// for min sclk 12MHz, ~83ns * 22 = 1826ns = 1.826us
//
// tCSP(min: 2.5ns) is meet
// sclk keeps 6.94 / 2 = 3.47ns low at least after ce activing
//
// tCHD(min: 20ns) > tACLK + tCLK
// sclk keep 6.94 * 1.5 = 10.41 at least(no meet!)
// need to set 'r_cfg_chd' = ceil(ceil(20 / 6.94) - 1.5) * 2 = 3, reset value is '4'
// for lower freq, can modify this value for performance tuning
//
// tSP(min: 2ns) is meet
// data keeps 6.94 / 2 = 3.47ns low at least befer sclk

`ifndef NMI_PSRAM_DEF_SV
`define NMI_PSRAM_DEF_SV

// verilog_format: off
`define NMI_PSRAM_WAIT 8'h00
`define NMI_PSRAM_CHD  8'h04
`define NMI_PSRAM_INIT 8'h08
// verilog_format: on

`endif

`include "mmap_define.svh"

interface psram_if ();
  logic       sck_o;
  logic [3:0] nss_o;
  logic [3:0] io_oe_o;
  logic [3:0] io_di_i;
  logic [3:0] io_do_o;
  logic       irq_o;

  modport dut(
      output sck_o,
      output nss_o,
      output io_oe_o,
      input io_di_i,
      output io_do_o,
      output irq_o
  );

endinterface


module nmi_psram (
    // verilog_format: off
    input logic  clk_i,
    input logic  rst_n_i,
    nmi_if.slave nmi,
    psram_if.dut psram
    // verilog_format: on
);

  // verilog_format: off
  localparam FSM_IDLE  = 6'd0;
  localparam FSM_WE_ST = 6'd1;
  localparam FSM_WE    = 6'd2;
  localparam FSM_RD_ST = 6'd3;
  localparam FSM_RD    = 6'd4;
  // verilog_format: on

  // reg
  logic [ 4:0] r_cfg_wait;
  logic [ 2:0] r_cfg_chd;
  logic        r_cfg_init;
  logic        s_cfg_reg_sel;
  logic        s_mem_sel;
  logic        s_mem_ready;
  logic [31:0] s_mem_rdata;

  logic        r_rd_st;
  logic        r_wr_st;
  logic [ 7:0] r_xfer_data_bit_cnt;
  logic [ 5:0] r_fsm_state;
  logic [23:0] r_mem_addr;
  logic [31:0] r_mem_wdata;

  logic        s_psram_ce;
  logic        s_psram_sio_oen;
  logic [ 1:0] s_disp_addr_ofst;
  logic [ 7:0] s_disp_xfer_bit_cnt;
  logic [31:0] s_disp_wdata;
  logic        s_init_done;
  logic        s_core_idle;
  logic        s_mem_valid_re;


  // verilog_format: off
  assign psram.nss_o[0]   = (~s_init_done) || (s_init_done && nmi.addr[24:23] == 2'd0) ? s_psram_ce : 1'b1;
  assign psram.nss_o[1]   = (~s_init_done) || (s_init_done && nmi.addr[24:23] == 2'd1) ? s_psram_ce : 1'b1;
  assign psram.nss_o[2]   = (~s_init_done) || (s_init_done && nmi.addr[24:23] == 2'd2) ? s_psram_ce : 1'b1;
  assign psram.nss_o[3]   = (~s_init_done) || (s_init_done && nmi.addr[24:23] == 2'd3) ? s_psram_ce : 1'b1;
  assign psram.io_oe_o[0] = ~s_psram_sio_oen;
  assign psram.io_oe_o[1] = ~s_psram_sio_oen;
  assign psram.io_oe_o[2] = ~s_psram_sio_oen;
  assign psram.io_oe_o[3] = ~s_psram_sio_oen;
  assign psram.irq_o      = 1'b0;
  // verilog_format: on


  assign s_mem_sel     = nmi.addr[31:24] == `PSRAM_START;
  assign s_cfg_reg_sel = nmi.addr[31:28] == `NMI_IP_START && nmi.addr[15:8] == `NMI_PSRAM_START;
  assign nmi.ready     = s_mem_sel ? s_mem_ready : 1'b1;
  always_comb begin
    nmi.rdata = '0;
    if (s_mem_sel) begin
      nmi.rdata = s_mem_rdata;
    end else if (nmi.addr[7:0] == `NMI_PSRAM_WAIT) begin
      nmi.rdata = {27'd0, r_cfg_wait};
    end else if (nmi.addr[7:0] == `NMI_PSRAM_CHD) begin
      nmi.rdata = {29'd0, r_cfg_chd};
    end else if (nmi.addr[7:0] == `NMI_PSRAM_INIT) begin
      nmi.rdata = {31'd0, r_cfg_init};
    end
  end

  // wait cycles(mmio)
  always_ff @(posedge clk_i, negedge rst_n_i) begin
    if (~rst_n_i) r_cfg_wait <= 5'd18;
    else if (nmi.valid && nmi.wstrb[0] && s_cfg_reg_sel && nmi.addr[7:0] == `NMI_PSRAM_WAIT) begin
      r_cfg_wait <= nmi.wdata[4:0];
    end
  end
  // extra cycle for tCHD(mmio)
  always_ff @(posedge clk_i, negedge rst_n_i) begin
    if (~rst_n_i) r_cfg_chd <= 3'd4;
    else if (nmi.valid && nmi.wstrb[0] && s_cfg_reg_sel && nmi.addr[7:0] == `NMI_PSRAM_CHD) begin
      r_cfg_chd <= nmi.wdata[2:0];
    end
  end
  // init device/switch qpi mode
  always_ff @(posedge clk_i, negedge rst_n_i) begin
    if (~rst_n_i) r_cfg_init <= '0;
    else if (nmi.valid && nmi.wstrb[0] && s_cfg_reg_sel && nmi.addr[7:0] == `NMI_PSRAM_INIT) begin
      r_cfg_init <= nmi.wdata[0];
    end
  end

  edge_det_sync_re #(1) u_mem_valid_edge_det_sync_re (
      clk_i,
      rst_n_i,
      nmi.valid,
      s_mem_valid_re
  );

  always_ff @(posedge clk_i, negedge rst_n_i) begin
    if (~rst_n_i) begin
      r_rd_st             <= '0;
      r_wr_st             <= '0;
      r_xfer_data_bit_cnt <= '0;
      r_fsm_state         <= FSM_IDLE;
      r_mem_addr          <= '0;
      r_mem_wdata         <= '0;
    end else begin
      if (s_mem_sel) begin
        /* verilator lint_off CASEINCOMPLETE */
        case (r_fsm_state)
          FSM_IDLE: begin
            if (s_mem_valid_re && (|nmi.wstrb)) begin
              r_fsm_state         <= FSM_WE_ST;
              r_xfer_data_bit_cnt <= s_disp_xfer_bit_cnt;
              r_mem_addr          <= {1'b0, nmi.addr[22:0]} + {22'd0, s_disp_addr_ofst};
              r_mem_wdata         <= s_disp_wdata;
            end else if (s_mem_valid_re && (~(|nmi.wstrb))) begin
              r_fsm_state         <= FSM_RD_ST;
              r_xfer_data_bit_cnt <= 8'd32;
              r_mem_addr          <= {1'b0, nmi.addr[22:0]};
              r_mem_wdata         <= nmi.wdata;  // NOTE: no used
            end
          end
          FSM_WE_ST: begin
            if (s_core_idle) begin
              r_wr_st     <= 1'b1;
              r_fsm_state <= FSM_WE;
            end
          end
          FSM_WE: begin
            r_wr_st <= 1'b0;
            if (s_mem_ready) r_fsm_state <= FSM_IDLE;
          end
          FSM_RD_ST: begin
            if (s_core_idle) begin
              r_rd_st     <= 1'b1;
              r_fsm_state <= FSM_RD;
            end
          end
          FSM_RD: begin
            r_rd_st <= 1'b0;
            if (s_mem_ready) r_fsm_state <= FSM_IDLE;
          end
        endcase
      end
    end
  end
  psram_core u_psram_core (
      .clk_i              (clk_i),
      .rst_n_i            (rst_n_i),
      .cfg_wait_i         (r_cfg_wait),
      .cfg_chd_i          (r_cfg_chd),
      .cfg_init_i         (r_cfg_init),
      .mem_ready_o        (s_mem_ready),
      .mem_addr_i         (r_mem_addr),
      .mem_wdata_i        (r_mem_wdata),
      .mem_rdata_o        (s_mem_rdata),
      .xfer_data_bit_cnt_i(r_xfer_data_bit_cnt),
      .rd_st_i            (r_rd_st),
      .wr_st_i            (r_wr_st),
      .init_done_o        (s_init_done),
      .idle_o             (s_core_idle),
      .psram_sclk_o       (psram.sck_o),
      .psram_ce_o         (s_psram_ce),
      .psram_mosi_i       (psram.io_di_i[0]),
      .psram_miso_i       (psram.io_di_i[1]),
      .psram_sio2_i       (psram.io_di_i[2]),
      .psram_sio3_i       (psram.io_di_i[3]),
      .psram_mosi_o       (psram.io_do_o[0]),
      .psram_miso_o       (psram.io_do_o[1]),
      .psram_sio2_o       (psram.io_do_o[2]),
      .psram_sio3_o       (psram.io_do_o[3]),
      .psram_sio_oen_o    (s_psram_sio_oen)
  );

  wr_dispatcher u_wr_dispatcher (
      .wstrb_i       (nmi.wstrb),
      .wdata_i       (nmi.wdata),
      .addr_ofst_o   (s_disp_addr_ofst),
      .xfer_bit_cnt_o(s_disp_xfer_bit_cnt),
      .wdata_o       (s_disp_wdata)
  );

endmodule


module wr_dispatcher (
    input  logic [ 3:0] wstrb_i,
    input  logic [31:0] wdata_i,
    output logic [ 1:0] addr_ofst_o,
    output logic [ 7:0] xfer_bit_cnt_o,
    output logic [31:0] wdata_o
);
  always_comb begin
    wdata_o = wdata_i;
    case (wstrb_i)
      4'b0001: begin
        addr_ofst_o    = 2'd0;
        xfer_bit_cnt_o = 8'd8;
        wdata_o[7:0]   = wdata_i[7:0];
      end
      4'b0010: begin
        addr_ofst_o    = 2'd1;
        xfer_bit_cnt_o = 8'd8;
        wdata_o[7:0]   = wdata_i[15:8];
      end
      4'b0100: begin
        addr_ofst_o    = 2'd2;
        xfer_bit_cnt_o = 8'd8;
        wdata_o[7:0]   = wdata_i[23:16];
      end
      4'b1000: begin
        addr_ofst_o    = 2'd3;
        xfer_bit_cnt_o = 8'd8;
        wdata_o[7:0]   = wdata_i[31:24];
      end
      4'b0011: begin
        addr_ofst_o    = 2'd0;
        xfer_bit_cnt_o = 8'd16;
        wdata_o[15:0]  = wdata_i[15:0];
      end
      4'b1100: begin
        addr_ofst_o    = 2'd2;
        xfer_bit_cnt_o = 8'd16;
        wdata_o[15:0]  = wdata_i[31:16];
      end
      4'b1111: begin
        addr_ofst_o    = 2'd0;
        xfer_bit_cnt_o = 8'd32;
        wdata_o[31:0]  = wdata_i[31:0];
      end
      default: begin
        addr_ofst_o    = 2'd0;
        xfer_bit_cnt_o = 8'd32;
        wdata_o        = wdata_i[31:0];
      end
    endcase
  end
endmodule
