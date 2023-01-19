module ps2(
    input ps2_clk,
    input ps2_dat,
    input resetn,
    input clock,
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
    output [3:0]  io_slave_rid
    );

    reg [7:0] fifo[7:0];
    reg [2:0] w_ptr, r_ptr;
    reg [9:0] buffer;
    // reg [10:0] fake_dat;
    reg [3:0] count;
    reg [2:0] ps2_clk_sync;
    always @(posedge clock) begin
        ps2_clk_sync <= {ps2_clk_sync[1:0],ps2_clk};
    end
    wire sampling = ps2_clk_sync[2] & ~ps2_clk_sync[1];
    always @(posedge clock) begin
        if (!resetn) begin
            count <= 0; w_ptr <= 0; r_ptr <= 0;
            // fake_dat <= 11'b11101110000;
        end
        else begin
            if (sampling) begin
                if (count == 4'd10) begin // cond check to filtrate unactive sign
                    if ((buffer[0] == 0) && // start bit
                        (ps2_dat) && // stop bit
                        // (fake_dat[count]) && // stop bit
                        (^buffer[9:1])) begin // odd parity
                        fifo[w_ptr] <= buffer[8:1]; // kbd scan code
                        // $display("fifo[w_ptr]: %0h", buffer[8:1]);
                        // $display("count: %d dat: %b", count, ps2_dat);
                        // $display("w_ptr: %d", w_ptr + 3'b1);
                        w_ptr <= w_ptr+3'b1;
                    end
                    count <= 0; // for next
                end else begin
                    buffer[count] <= ps2_dat; // store ps2_data
                    // buffer[count] <= fake_dat[count];
                    // $display("count: %d dat: %b", count, ps2_dat);
                    // NOTE: hack for some no std dat
                    if (count == 4'b0 && ps2_dat == 1'b1)
                        count <= 4'b0;
                    else
                        count <= count + 3'b1;
                end
            end
        end
    end

    parameter [1:0] sIdle = 0, sWdata = 1, sWresp = 2, sRaddr = 1, sRdata = 2;
    reg [1:0] srstate = sIdle;
    reg sraddrEn = 1;
    reg srdataEn = 0;
    reg [31: 0] srdata = 0;
    reg srlast = 0;
    reg [3:0] srid = 0;
    
    always @(posedge clock) begin // slave read channel
        if(!resetn) begin
            srstate <= sIdle;
            sraddrEn <= 1;
            srdataEn <= 0;
            srlast <= 0;
            // $display("[rst]idle");
        end else if (srstate == sIdle) begin
            if(sraddrEn & io_slave_arvalid) begin
                srstate <= sRdata;
                sraddrEn <= 0;
                srdataEn <= 1;
                srdata <= r_ptr == w_ptr? 32'b0 : {24'b0, fifo[r_ptr]};
                r_ptr <= r_ptr == w_ptr? r_ptr : r_ptr + 1;
                srlast <= 1;
                srid <= io_slave_arid;
                // $display("idle->rdata");
                // $display("r_ptr: %d w_prt: %d", r_ptr, w_ptr);
                // $display("fifo[r_ptr]: %0h", fifo[r_ptr]);
                // $display("srid: %0h", io_slave_arid);
            end
        end else if (srstate == sRdata) begin
            if(srdataEn & io_slave_rready) begin
                srstate <= sIdle;
                sraddrEn <= 1;
                srdataEn <= 0;
                srlast <= 0;
                // $display("rdata->idle");
            end
        end
    end

    // always@(*) begin
        // if(io_slave_araddr >= 32'h1000_3000) begin
            // $display("ps2 addr: %0h", io_slave_araddr);
        // end
        // else if(sampling) begin
        //     $display("poseedge of sampling");
        // end
    // end

    assign io_slave_awready = 0;
    assign io_slave_wready  = 0;
    assign io_slave_bvalid  = 0;
    assign io_slave_bresp   = 0;
    assign io_slave_bid     = 0;
    assign io_slave_arready = sraddrEn;
    assign io_slave_rvalid  = srdataEn;
    assign io_slave_rresp   = 1;
    assign io_slave_rdata   = {32'b0, srdata};
    assign io_slave_rlast   = srlast;
    assign io_slave_rid     = srid;
    
endmodule
