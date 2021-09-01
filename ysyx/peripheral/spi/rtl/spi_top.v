`include "spi_defines.v"
module spi_top
(
  // APB Signals
  PCLK, PRESETN, PADDR, PWDATA, PRDATA, PSEL,
  PWRITE, PENABLE, PREADY, PSLVERR,
  // Interrupt
  IRQ,
  // SPI signals
  ss_pad_o, sclk_pad_o, mosi_pad_o, miso_pad_i
);

  parameter Tp = 1;
  
  input                            PCLK;            // APB System Clock
  input                            PRESETN;         // APB Reset - Active low
  input [4:0]                      PADDR;           // APB Address
  input [31:0]                     PWDATA;          // Write data
  output[31:0]                     PRDATA;          // Read data
  input                            PWRITE;
  input                            PSEL;
  input                            PENABLE;
  output                           PREADY;
  output                           PSLVERR;
  output                           IRQ;
    
  // SPI signals                                     
  output          [`SPI_SS_NB-1:0] ss_pad_o;         // slave select
  output                           sclk_pad_o;       // serial clock
  output                           mosi_pad_o;       // master out slave in
  input                            miso_pad_i;       // master in slave out
    
  reg                     [31:0]   PRDATA;
  reg                              PREADY;
  reg                              IRQ;

  // Internal signals
  //reg       [`SPI_DIVIDER_LEN-1:0] divider;          // Divider register
  //reg             [`SPI_SS_NB-1:0] ss;               // Slave select register
  reg       [`SPI_CTRL_BIT_NB-1:0] ctrl;             // Control and status register
  reg                     [32-1:0] wb_dat;           // wb data out
  wire                             cpol;             // clock polar(idel clock can be high(cpol==1) or low(cpol==0,default)
  wire         [`SPI_MAX_CHAR-1:0] rx;               // Rx register
  wire      [`SPI_DIVIDER_LEN-1:0] divider;          // Divider register
  wire            [`SPI_SS_NB-1:0] ss;               // Slave select register
  wire                             rx_negedge;       // miso is sampled on negative edge
  wire                             tx_negedge;       // mosi is driven on negative edge
  wire    [`SPI_CHAR_LEN_BITS-1:0] char_len;         // char len
  wire                             go;               // go
  wire                             lsb;              // lsb first on line
  wire                             ie;               // interrupt enable
  wire                             ass;              // automatic slave select
  wire                             rd_endian;        // apb read data endian
  wire                             spi_divider_sel;  // divider register select
  wire                             spi_ctrl_sel;     // ctrl register select
  wire                       [3:0] spi_tx_sel;       // tx_l register select
  wire                             spi_ss_sel;       // ss register select
  wire                             tip;              // transfer in progress
  wire                             pos_edge;         // recognize posedge of sclk
  wire                             neg_edge;         // recognize negedge of sclk
  wire                             last_bit;         // marks last character bit

  // Address decoder
  assign spi_divider_sel = PSEL & PENABLE & (PADDR[`SPI_OFS_BITS] == `SPI_DEVIDE);
  assign spi_ctrl_sel    = PSEL & PENABLE & (PADDR[`SPI_OFS_BITS] == `SPI_CTRL);
  assign spi_tx_sel[0]   = PSEL & PENABLE & (PADDR[`SPI_OFS_BITS] == `SPI_TX_0);
  assign spi_tx_sel[1]   = PSEL & PENABLE & (PADDR[`SPI_OFS_BITS] == `SPI_TX_1);
  assign spi_tx_sel[2]   = PSEL & PENABLE & (PADDR[`SPI_OFS_BITS] == `SPI_TX_2);
  assign spi_tx_sel[3]   = PSEL & PENABLE & (PADDR[`SPI_OFS_BITS] == `SPI_TX_3);
  assign spi_ss_sel      = PSEL & PENABLE & (PADDR[`SPI_OFS_BITS] == `SPI_SS);

  // Read from registers
  always @(PADDR or rx or ctrl or divider or ss)
  begin
    case (PADDR[`SPI_OFS_BITS])
`ifdef SPI_MAX_CHAR_128
      `SPI_RX_0:    wb_dat = rx[31:0];
      `SPI_RX_1:    wb_dat = rx[63:32];
      `SPI_RX_2:    wb_dat = rx[95:64];
      `SPI_RX_3:    wb_dat = {{128-`SPI_MAX_CHAR{1'b0}}, rx[`SPI_MAX_CHAR-1:96]};
`else
`ifdef SPI_MAX_CHAR_64
      `SPI_RX_0:    wb_dat = rx[31:0];
      `SPI_RX_1:    wb_dat = {{64-`SPI_MAX_CHAR{1'b0}}, rx[`SPI_MAX_CHAR-1:32]};
      `SPI_RX_2:    wb_dat = 32'b0;
      `SPI_RX_3:    wb_dat = 32'b0;
`else
      `SPI_RX_0:    wb_dat = {{32-`SPI_MAX_CHAR{1'b0}}, rx[`SPI_MAX_CHAR-1:0]};
      `SPI_RX_1:    wb_dat = 32'b0;
      `SPI_RX_2:    wb_dat = 32'b0;
      `SPI_RX_3:    wb_dat = 32'b0;
`endif
`endif
      `SPI_CTRL:    wb_dat = {{32-`SPI_CTRL_BIT_NB{1'b0}}, ctrl};
      `SPI_DEVIDE:  wb_dat = {{32-`SPI_DIVIDER_LEN{1'b0}}, divider};
      `SPI_SS:      wb_dat = {{32-`SPI_SS_NB{1'b0}}, ss};
      default:      wb_dat = 32'bx;
    endcase
  end

  // Wb data out
 always @(posedge PCLK or negedge PRESETN)
  begin
    if (PRESETN == 0)
      PRDATA <=  32'b0;
    //else
    //  PRDATA <=  wb_dat;
    else if(rd_endian) //cuckoo
      PRDATA <=  {wb_dat[7:0],wb_dat[15:8],wb_dat[23:16],wb_dat[31:24]};
    else
      PRDATA <=  wb_dat;
  end

  // Wb acknowledge
  always @(posedge PCLK or negedge PRESETN)
  begin
    if (PRESETN == 0)
      PREADY <=  1'b0;
    else
      PREADY <=  PSEL & PENABLE & ~PREADY;
  end

  // Wb error
  assign PSLVERR = 1'b0;

  // Interrupt
  always @(posedge PCLK or negedge PRESETN)
  begin
    if (PRESETN == 0)
      IRQ <=  1'b0;
    else if (ie && tip && last_bit && pos_edge)
      IRQ <=  1'b1;
    else if (PREADY)
      IRQ <=  1'b0;
  end

/*
  // Divider register
  always @(posedge PCLK or negedge PRESETN)
  begin
    if (PRESETN == 0)
        divider <=  {`SPI_DIVIDER_LEN{1'b0}};
    else if (spi_divider_sel && PWRITE && !tip)
      begin
      `ifdef SPI_DIVIDER_LEN_8

          divider <=  PWDATA[`SPI_DIVIDER_LEN-1:0];
      `endif
      `ifdef SPI_DIVIDER_LEN_16
          divider[`SPI_DIVIDER_LEN-1:0] <=  PWDATA[`SPI_DIVIDER_LEN-1:0];
      `endif
      `ifdef SPI_DIVIDER_LEN_24
          divider[`SPI_DIVIDER_LEN-1:0] <=  PWDATA[`SPI_DIVIDER_LEN-1:0];
      `endif
      `ifdef SPI_DIVIDER_LEN_32
          divider[`SPI_DIVIDER_LEN-1:0] <=  PWDATA[`SPI_DIVIDER_LEN-1:0];
      `endif
      end
  end
*/

  // Ctrl register
  always @(posedge PCLK or negedge PRESETN)
  begin
    if (PRESETN == 0)
      ctrl <=  {`SPI_CTRL_BIT_NB{1'b0}};
    else if(spi_ctrl_sel && PWRITE && !tip)
      begin
          ctrl[`SPI_CTRL_BIT_NB-1:0] <=  PWDATA[`SPI_CTRL_BIT_NB-1:0];
      end
    else if(tip && last_bit && pos_edge)
    ctrl[`SPI_CTRL_GO] <=  1'b0;
  end

  assign cpol       = ctrl[`SPI_CTRL_CPOL];
  assign ss         = ctrl[`SPI_CTRL_SS];//cuckoo
  assign divider    = ctrl[`SPI_CTRL_DIV];//cuckoo
  assign rd_endian  = ctrl[`SPI_CTRL_RD_ENDIAN]; //cuckoo
  assign rx_negedge = ctrl[`SPI_CTRL_RX_NEGEDGE];
  assign tx_negedge = ctrl[`SPI_CTRL_TX_NEGEDGE];
  assign go         = ctrl[`SPI_CTRL_GO];
  assign char_len   = ctrl[`SPI_CTRL_CHAR_LEN];
  assign lsb        = ctrl[`SPI_CTRL_LSB];
  assign ie         = ctrl[`SPI_CTRL_IE];
  assign ass        = ctrl[`SPI_CTRL_ASS];

  /*
  // Slave select register
  always @(posedge PCLK or negedge PRESETN)
  begin
    if (PRESETN == 0)
      //ss <=  {`SPI_SS_NB{1'b0}};
      ss <=  {`SPI_SS_NB{1'b0}} | `SPI_SS_NB'h1; //cuckoo default 1(chip select0)
    else if(spi_ss_sel && PWRITE && !tip)
      begin
      `ifdef SPI_SS_NB_8
          ss <=  PWDATA[`SPI_SS_NB-1:0];
      `endif
      `ifdef SPI_SS_NB_16
          ss[`SPI_SS_NB-1:0] <=  PWDATA[`SPI_SS_NB-1:0];
      `endif
      `ifdef SPI_SS_NB_24
          ss[`SPI_SS_NB-1:0] <=  PWDATA[`SPI_SS_NB-1:0];
      `endif
      `ifdef SPI_SS_NB_32
          ss[`SPI_SS_NB-1:0] <=  PWDATA[`SPI_SS_NB-1:0];
      `endif
      end
  end
  */

  assign ss_pad_o = ~((ss & {`SPI_SS_NB{tip & ass}}) | (ss & {`SPI_SS_NB{!ass}}));

  spi_clgen clgen (.clk_in(PCLK), .rst(!PRESETN), .go(go), .enable(tip), .last_clk(last_bit),
                   .divider(divider), .clk_out(sclk_pad_o), .pos_edge(pos_edge),
                   .neg_edge(neg_edge), .cpol(cpol));

  spi_shift shift (.clk(PCLK), .rst(!PRESETN), .len(char_len[`SPI_CHAR_LEN_BITS-1:0]),
                   .latch(spi_tx_sel[3:0] & {4{PWRITE}}), .byte_sel(4'hF), .lsb(lsb),
                   .go(go), .pos_edge(pos_edge), .neg_edge(neg_edge),
                   .rx_negedge(rx_negedge), .tx_negedge(tx_negedge),
                   .tip(tip), .last(last_bit),
                   .p_in(PWDATA), .p_out(rx),
                   .s_clk(sclk_pad_o), .s_in(miso_pad_i), .s_out(mosi_pad_o));
endmodule

