module kdb(
    input clock,
    input resetn,
    output ps2_clk,
    output ps2_dat
);

// 25M -> 6.25M
parameter NUM_DIV = 4;
// 25M -> 10k
// parameter NUM_DIV = 2500;
reg [12:0]cnt;
reg clk_div;
always @(posedge clock or negedge resetn)
    if(!resetn) begin
        cnt     <= 13'd0;
        clk_div <= 1'b0;
    end
    else if(cnt < NUM_DIV / 2 - 1) begin
        cnt     <= cnt + 1'b1;
        clk_div <= clk_div;
    end
    else begin
        cnt <= 13'd0;
        clk_div <= ~clk_div;
    end

parameter sIdle = 0, sSend = 1;
reg [1:0] state;

wire[7:0] dat;
reg[7:0] rec_dat;
reg send_val;
reg[3:0] send_cnt;

PS2Read ps2Read(.clock(clock), .resetn(resetn), .dat(dat));
wire have_dat = dat != 8'b0;
always@(posedge clock or negedge resetn) begin
  if(!resetn) begin
    rec_dat <= 8'b0;
  end
  else if (have_dat && state == sIdle) begin
    rec_dat <= dat;
  end
end

reg [1:0] kdb_clk_sync;
always @(posedge clock or negedge resetn) begin
  if(!resetn) begin
    kdb_clk_sync <= 2'b00;
  end
  else begin
    kdb_clk_sync <= {kdb_clk_sync[0], clk_div};
  end
end

wire sampling = kdb_clk_sync[1] & ~kdb_clk_sync[0];

always@(posedge clock or negedge resetn) begin
  if(!resetn) begin
    state <= sIdle;
    send_cnt <= 4'b0;
    send_val <= 1'b1;
  end else if (state == sIdle) begin
    if(have_dat) begin
      state <= sSend;
    end
  end else if (state == sSend && kdb_clk_sync == 2'b11) begin
    if (send_cnt == 4'd0) begin
      send_val <= 1'b0;
      send_cnt <= send_cnt + 1'b1;
    end else if (send_cnt == 4'd9) begin
      send_val <= ~(^rec_dat);
      send_cnt <= send_cnt + 1'b1;
    end else if (send_cnt == 4'd10) begin
      send_val <= 1'b1;
      state <= sIdle;
      send_cnt <= 4'd0;
      rec_dat <= 8'b0;
    end
    else begin
      // verilator lint_off WIDTH
      send_val <= rec_dat[send_cnt-1'b1]; // NOTE: bit width
      // verilator lint_on WIDTH
      send_cnt <= send_cnt + 1'b1;
    end
  end
end

assign ps2_clk = state == sSend ? clk_div : 1'b1;
assign ps2_dat = state == sSend ? send_val: 1'b1;
endmodule

import "DPI-C" function void ps2_read(output byte dat);

module PS2Read (
  input       clock,
  input       resetn,
  output reg[7:0] dat
);

// reg[2:0] cnt;
always@(posedge clock or negedge resetn) begin
  if(!resetn) begin
    dat = 8'b0000_0000; // NOTE: only for verilator
    // dat <= 8'b0000_0000;
    // cnt <= 3'b000;
  end
  else begin
    ps2_read(dat);
    // if (dat != 8'b0)
      // $display("PS2Read dat: %0h", dat);
      // if (cnt == 3'b001) dat <= 8'hB8;
      // else if (cnt == 3'b011) dat <= 8'h1A;
      // else dat <= 8'h00;
      // cnt <= cnt + 3'b1;
  end

end
endmodule