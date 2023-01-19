module sdr_top(
    input clk,
    input cke,
    input cs,
    input ras,
    input cas,
    input we,
    input [1:0] dqm,
    input [12:0] addr,
    input [1:0] ba,
    output [15:0] data_input,
    input [15:0] data_output,
    input data_out_en
);

    // always@(posedge clk) begin
        // if(cke == 1'b1) begin
            // $display("cs: %b ras: %b cas: %b we: %b", cke, ras, cas, we);
            // $display("ba: %0b", ba);
            // if(addr != 13'd0) begin
                // $display("addr: %h data: %h we: %b ba: %b cs: %b ras: %b cas: %b dqm: %b en: %b", addr, data_output, we, ba, cs, ras, cas, dqm, data_out_en);
            // end
            
            // $display("data_out_en: %b", data_out_en);
            // $display("data_input: %0x", data_input);
            // $display("data_output: %0x", data_output);
        // end
    // end

wire [15:0] dq_bus = (data_out_en) ? data_output : 16'dz;
assign data_input = dq_bus;

sdr_sync u_sdr(
    .Clk(clk),
    .Cke(cke),
    .Cs_n(cs),
    .Ras_n(ras),
    .Cas_n(cas),
    .We_n(we),
    .Addr(addr),
    .Ba(ba),
    .Dq(dq_bus),
    .Dqm(dqm)
);

endmodule