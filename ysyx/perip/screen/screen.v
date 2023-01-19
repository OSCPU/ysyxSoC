module screen(
    input clock,
    input dat_hsync,
    input dat_vsync,
    input[3:0] dat_vga_r,
    input[3:0] dat_vga_g,
    input[3:0] dat_vga_b
);

localparam RES = 400 * 300 - 1;
reg [31:0] rf[RES:0];
import "DPI-C" function void set_gpr_ptr(input logic [31:0] a []);
initial set_gpr_ptr(rf);  // rf为通用寄存器的二维数组变量

// always@(posedge clock) begin
//     // $display("screen");
//     if(dat_hsync == 1'b1) begin
        
//     end
// end

endmodule