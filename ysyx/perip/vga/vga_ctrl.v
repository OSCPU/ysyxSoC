module vga_ctrl(
    input clock,
    input resetn,
    input         io_master_awready,
    output        io_master_awvalid,
    output [31:0] io_master_awaddr,
    output [3:0]  io_master_awid,
    output [7:0]  io_master_awlen,
    output [2:0]  io_master_awsize,
    output [1:0]  io_master_awburst,
    input         io_master_wready,
    output        io_master_wvalid,
    output [63:0] io_master_wdata,
    output [7:0]  io_master_wstrb,
    output        io_master_wlast,
    output        io_master_bready,
    input         io_master_bvalid,
    input  [1:0]  io_master_bresp,
    input  [3:0]  io_master_bid,
    input         io_master_arready,
    output        io_master_arvalid,
    output [31:0] io_master_araddr,
    output [3:0]  io_master_arid,
    output [7:0]  io_master_arlen,
    output [2:0]  io_master_arsize,
    output [1:0]  io_master_arburst,
    output        io_master_rready,
    input         io_master_rvalid,
    input  [1:0]  io_master_rresp,
    input  [63:0] io_master_rdata,
    input         io_master_rlast,
    input  [3:0]  io_master_rid,

    output        io_slave_awready,
    input         io_slave_awvalid,
    input  [31:0] io_slave_awaddr,
    input  [3:0]  io_slave_awid,
    input  [7:0]  io_slave_awlen,
    input  [2:0]  io_slave_awsize,
    input  [1:0]  io_slave_awburst,
    output        io_slave_wready,
    input         io_slave_wvalid,
    input  [63:0] io_slave_wdata,
    input  [7:0]  io_slave_wstrb,
    input         io_slave_wlast,
    input         io_slave_bready,
    output        io_slave_bvalid,
    output [1:0]  io_slave_bresp,
    output [3:0]  io_slave_bid,
    output        io_slave_arready,
    input         io_slave_arvalid,
    input  [31:0] io_slave_araddr,
    input  [3:0]  io_slave_arid,
    input  [7:0]  io_slave_arlen,
    input  [2:0]  io_slave_arsize,
    input  [1:0]  io_slave_arburst,
    input         io_slave_rready,
    output        io_slave_rvalid,
    output [1:0]  io_slave_rresp,
    output [63:0] io_slave_rdata,
    output        io_slave_rlast,
    output [3:0]  io_slave_rid,

    // output [31:0] io_offset,

    output hsync,
    output vsync,
    output [3:0]vga_r,
    output [3:0]vga_g,
    output [3:0]vga_b
);

endmodule


module preg #(parameter w = 1, parameter reset_val = 0)(
    input clock,
    input reset,
    input [w-1:0] din,
    output [w-1:0] dout,
    input wen
);

reg [w-1:0] data;
assign dout = data;

always @(posedge clock) begin
    if(reset)
        data <= reset_val;
    else if(wen)
        data <= din;
end

endmodule
