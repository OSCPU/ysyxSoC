`include "amba_define.v"

// define this macro to enable fast behavior simulation
// for flash by skipping SPI transfers
//`define FAST_FLASH

`define SPI_CTL_DIV_4         32'h00010000 //23:16 (divide by 4)
`define SPI_CTL_DIV_2         32'h00000000 //23:16 (divide by 2)

`define SPI_CTL_SS            32'h01000000 //31:24
`define SPI_CTL_DIV           `SPI_CTL_DIV_2
`define SPI_CTL_CPOL          32'h00008000 //cuckoo(clock polar)
`define SPI_CTL_RD_ENDIAN     32'h00004000 //14
`define SPI_CTL_ASS           32'h00002000 //13
`define SPI_CTL_IE            32'h00001000 //12
`define SPI_CTL_LSB           32'h00000800 //11
`define SPI_CTL_TX_NEGEDGE    32'h00000400 //10
`define SPI_CTL_RX_NEGEDGE    32'h00000200 //9
`define SPI_CTL_GO            32'h00000100 //8
`define SPI_CTL_RES_1         32'h00000080 //7
`define SPI_CTL_CHAR_LEN      32'h00000040 //6:0(0x40=64bit)

`define SPI_ADDR_RXD0   5'h00
`define SPI_ADDR_RXD1   5'h04
`define SPI_ADDR_RXD2   5'h08
`define SPI_ADDR_RXD3   5'h0c

`define SPI_ADDR_TXD0   5'h00
`define SPI_ADDR_TXD1   5'h04
`define SPI_ADDR_TXD2   5'h08
`define SPI_ADDR_TXD3   5'h0c

`define SPI_ADDR_CTL    5'h10
`define SPI_ADDR_DIV    5'h14
`define SPI_ADDR_CS     5'h18

module spi
#( 
parameter   flash_addr_start = 32'h30000000,
parameter   flash_addr_end   = 32'h3fffffff,
parameter   spi_cs_num       = 2
)
(
  input                      clk,
  input                      resetn,
  input   [`P_ADDR_W-1:0]    in_paddr,
  input                      in_psel,
  input                      in_penable,
  input   [2:0]              in_pprot,
  input                      in_pwrite,
  input   [`P_DATA_W-1:0]    in_pwdata,
  input   [`P_STRB_W-1:0]    in_pstrb,
  output                     in_pready,
  output  [`P_DATA_W-1:0]    in_prdata,
  output                     in_pslverr,

  output                     spi_clk,
  output  [spi_cs_num-1:0]   spi_cs,
  output                     spi_mosi,
  input                      spi_miso,
  output                     spi_irq_out
);

`ifdef FAST_FLASH
  wire [63:0] data;
  wire ren = in_psel && !in_pwrite;
  wire wen = in_penable && in_psel &&  in_pwrite;
  FlashRead flashRead (
    .clock(clk),
    .ren(ren),
    .addr({36'b0, in_paddr[27:2], 2'b0}),
    .data(data)
  );
  assign spi_clk    = 1'b0;
  assign spi_cs     = 2'b0;
  assign spi_mosi   = 1'b1;
  assign in_pslverr = 1'b0;
  assign in_pready  = in_penable && ren;
  assign in_prdata  = data[31:0];

  always @(posedge clk) begin
    if (wen) begin
      $fwrite(32'h80000002, "Assertion failed: only support flash reading. \n");
      $fatal;
    end
  end

`else

wire  [`P_ADDR_W-1:0]    paddr_spi;
wire                     psel_spi;
wire                     penable_spi;
wire                     pwrite_spi;
wire  [`P_DATA_W-1:0]    pwdata_spi;
wire                     pready_spi;
wire  [`P_DATA_W-1:0]    prdata_spi;
wire                     pslverr_spi;

wire                     is_flash;

reg   [4:0]              cmd_state;
reg   [4:0]              cmd_state_next;
reg   [4:0]              spi_state;
reg   [4:0]              spi_state_next;

reg   [`P_ADDR_W-1:0]    paddr_reg;
wire  [`P_ADDR_W-1:0]    paddr_in;

wire                     spi_fire;
wire  [31:0]             spi_tx_data0;
wire  [31:0]             spi_tx_data1;
wire  [31:0]             spi_ctl_data;

wire  spi_irq;

wire [`P_ADDR_W-1:0] paddr_align = {in_paddr[`P_ADDR_W-1:2], 2'b00};

`define CMD_IDLE       5'h0
`define CMD_SPI_CSR    5'h1
`define CMD_WR_TXD0    5'h2
`define CMD_WR_TXD1    5'h3
`define CMD_WR_CTL     5'h4
`define CMD_WAIT_IRQ   5'h5
`define CMD_RD_RXD0    5'h6

`define SPI_IDLE       5'h0
`define SPI_ENABLE     5'h1
`define SPI_WAIT_READY 5'h2

always@(posedge clk or negedge resetn) begin
  if(!resetn)
    paddr_reg <= 32'h0;
  if(in_psel && in_penable)
    paddr_reg <= paddr_align;
end

assign paddr_in = paddr_align | paddr_reg;

assign is_flash = paddr_in >= flash_addr_start && paddr_in <= flash_addr_end;


assign psel_spi    = spi_state == `SPI_WAIT_READY;
assign penable_spi = spi_state == `SPI_ENABLE || spi_state == `SPI_WAIT_READY;

assign spi_irq_out = cmd_state == `CMD_SPI_CSR   ? spi_irq : 1'b0;

assign pwrite_spi = cmd_state == `CMD_SPI_CSR   ? in_pwrite :
                    cmd_state == `CMD_RD_RXD0   ? 1'b0   : 1'b1;
assign pwdata_spi = cmd_state == `CMD_SPI_CSR   ? in_pwdata : 
                    cmd_state == `CMD_WR_TXD0   ? spi_tx_data0 : 
                    cmd_state == `CMD_WR_TXD1   ? spi_tx_data1 :
                    cmd_state == `CMD_WR_CTL    ? spi_ctl_data : 32'h0;

assign in_prdata = prdata_spi;

assign paddr_spi  = {27'b0,
                    cmd_state == `CMD_SPI_CSR   ? paddr_align[4:0]:
                    cmd_state == `CMD_WR_TXD0   ? `SPI_ADDR_TXD0  :
                    cmd_state == `CMD_WR_TXD1   ? `SPI_ADDR_TXD1  :
                    cmd_state == `CMD_WR_CTL    ? `SPI_ADDR_CTL   :
                    cmd_state == `CMD_RD_RXD0   ? `SPI_ADDR_RXD0  : 5'h0};

assign in_pready = cmd_state == `CMD_SPI_CSR  && spi_fire || 
                cmd_state == `CMD_RD_RXD0  && spi_fire; 

assign in_pslverr = pslverr_spi;

assign spi_tx_data1 = 32'h03000000 | {8'b0, paddr_in[23:0]}; //addr and readd cmd
assign spi_tx_data0 = 32'h00000000;   //spi read 32bit data
assign spi_ctl_data = `SPI_CTL_SS | `SPI_CTL_DIV | `SPI_CTL_RD_ENDIAN | `SPI_CTL_ASS | 
                      `SPI_CTL_IE | `SPI_CTL_TX_NEGEDGE | `SPI_CTL_GO | `SPI_CTL_CHAR_LEN;
                      //`SPI_CTL_IE | `SPI_CTL_GO | `SPI_CTL_CHAR_LEN;

always@(posedge clk or negedge resetn) begin
  if(!resetn)
    cmd_state <= `CMD_IDLE;
  else
    cmd_state <= cmd_state_next;
end

always@(cmd_state or in_psel or in_penable or spi_fire or spi_irq )begin
  case(cmd_state)
    `CMD_IDLE: begin
      if(in_psel && in_penable) begin
        if(is_flash && !in_pwrite) //read only!!!
          cmd_state_next = `CMD_WR_TXD0;
        else
          cmd_state_next = `CMD_SPI_CSR;
      end
      else
        cmd_state_next = `CMD_IDLE;
    end
    `CMD_SPI_CSR : begin
      if(spi_fire)
        cmd_state_next = `CMD_IDLE;
      else
        cmd_state_next = `CMD_SPI_CSR;
    end
    `CMD_WR_TXD0:begin
      if(spi_fire)
        cmd_state_next = `CMD_WR_TXD1;
      else
        cmd_state_next = `CMD_WR_TXD0;
    end
    `CMD_WR_TXD1:begin
      if(spi_fire)
        cmd_state_next = `CMD_WR_CTL;
      else
        cmd_state_next = `CMD_WR_TXD1;
    end
    `CMD_WR_CTL:begin
      if(spi_fire)
        cmd_state_next = `CMD_WAIT_IRQ;
      else
        cmd_state_next = `CMD_WR_CTL;
    end
    `CMD_WAIT_IRQ:begin
      if(spi_irq)
        cmd_state_next = `CMD_RD_RXD0;
      else
        cmd_state_next = `CMD_WAIT_IRQ;
    end
    default:begin //`CMD_RD_RXD0
      if(spi_fire)
        cmd_state_next = `CMD_IDLE;
      else
        cmd_state_next = `CMD_RD_RXD0;
    end
  endcase
end

wire spi_apb_start;

assign spi_apb_start = cmd_state != `CMD_IDLE && cmd_state != `CMD_WAIT_IRQ ;

always@(posedge clk or negedge resetn) begin
  if(!resetn)
    spi_state <= 5'h0;
  else
    spi_state <= spi_state_next;
end

always@(spi_state or spi_apb_start or pready_spi) begin
  case(spi_state)
    `SPI_IDLE: begin
      if(spi_apb_start)
        spi_state_next = `SPI_ENABLE;
      else
        spi_state_next = `SPI_IDLE;
    end
    `SPI_ENABLE: begin
      spi_state_next = `SPI_WAIT_READY;
    end
    default: begin //SPI_WAIT_READY
      if(pready_spi)
        spi_state_next = `SPI_IDLE;
      else
        spi_state_next = `SPI_WAIT_READY;
    end
  endcase
end

assign spi_fire = spi_state == `SPI_WAIT_READY && pready_spi;

wire [7:0] ss_pad_o;

assign spi_cs = ss_pad_o[1:0];

spi_top u0_spi_top
(
  .PCLK(clk),
  .PRESETN(resetn),

  .PADDR(paddr_spi[4:0]),
  .PWDATA(pwdata_spi),
  .PRDATA(prdata_spi),
  .PSEL(psel_spi),
  .PWRITE(pwrite_spi),
  .PENABLE(penable_spi),
  .PREADY(pready_spi),
  .PSLVERR(pslverr_spi),

  .ss_pad_o(ss_pad_o),
  .sclk_pad_o(spi_clk),
  .mosi_pad_o(spi_mosi),
  .miso_pad_i(spi_miso),
  .IRQ(spi_irq)
);

`endif // FAST_FLASH

endmodule
