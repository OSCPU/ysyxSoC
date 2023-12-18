module spi_top_apb #(
  parameter flash_addr_start = 32'h30000000,
  parameter flash_addr_end   = 32'h3fffffff,
  parameter spi_cs_num       = 2
) (
  input         clk,
  input         resetn,
  input  [31:0] in_paddr,
  input         in_psel,
  input         in_penable,
  input  [2:0]  in_pprot,
  input         in_pwrite,
  input  [31:0] in_pwdata,
  input  [3:0]  in_pstrb,
  output        in_pready,
  output [31:0] in_prdata,
  output        in_pslverr,

  output                  spi_clk,
  output [spi_cs_num-1:0] spi_cs,
  output                  spi_mosi,
  input                   spi_miso,
  output                  spi_irq_out
);

wire [7:0] ss_pad_o;
assign spi_cs = ss_pad_o[spi_cs_num-1:0];

spi_top u0_spi_top (
  .wb_clk_i(clk),
  .wb_rst_i(!resetn),
  .wb_adr_i(in_paddr[4:0]),
  .wb_dat_i(in_pwdata),
  .wb_dat_o(in_prdata),
  .wb_sel_i(in_pstrb),
  .wb_we_i (in_pwrite),
  .wb_stb_i(in_psel),
  .wb_cyc_i(in_penable),
  .wb_ack_o(in_pready),
  .wb_err_o(in_pslverr),
  .wb_int_o(),

  .ss_pad_o(ss_pad_o),
  .sclk_pad_o(spi_clk),
  .mosi_pad_o(spi_mosi),
  .miso_pad_i(spi_miso)
);

endmodule
