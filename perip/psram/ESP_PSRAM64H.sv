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
// Behavioral Verilog simulation model for a 64Mbit Quad SPI (QSPI) PSRAM.
// This model simulates the external interface and functionality of the PSRAM,
// allowing a controller (e.g., ESP32) to interact with it for read and write
// operations. It does not model internal proprietary logic or exact timing
// parameters beyond basic clock edge synchronization.
//
// Assumptions:
// - Memory Size: 64 Mbits (8 MBytes)
// - Interface: Quad SPI (QSPI)
// - Supported Commands:
//   - Quad Read (0xEB)
//   - Quad Write (0x38)
// - Dummy Cycles: Assumes 6 dummy cycles for Quad Read (common for many QSPI devices).
// - Address Width: 23 bits (for 8 MBytes).
// - Data Width: 8 bits (byte-addressable).
// - Clock Polarity/Phase: Assumes CPOL=0, CPHA=0 (SPI Mode 0), where data is
//   sampled on the rising edge of sclk and driven on the falling edge.
// - Tri-state Logic: Proper handling of IO lines for input and output.
//
// Usage:
// Connect this module to your QSPI master controller in your simulation environment.
//

/* verilator lint_off WIDTHTRUNC */
module ESP_PSRAM64H #(
    parameter ID = 0
) (
    input wire       sclk,  // Serial Clock (SPI Clock)
    input wire       csn,   // Chip Select (Active Low)
    inout wire [3:0] sio    // Quad SPI Data Line 0 (MOSI/MISO)
    // Add other pins like RST_N if your specific application uses it and you want to model it.
);

  // --- Parameters ---
  // Memory depth in bytes (64 Mbits / 8 bits/byte = 8 MBytes)
  localparam MEM_DEPTH_BYTES = 8 * 1024 * 1024;
  // localparam MEM_DEPTH_BYTES = 72 * 1024; // for debug
  // Address width required for MEM_DEPTH_BYTES
  localparam ADDR_BITS = 24;

  // --- Internal Memory Array ---
  // mem_array: Stores the 8-bit (byte) data. Indexed by address.
  reg [7:0] mem_array[0:MEM_DEPTH_BYTES-1];

  // --- State Machine Definitions ---
  parameter S_IDLE = 3'd0;
  parameter S_CMD_SPI_RECV = 3'd1;
  parameter S_CMD_QPI_RECV = 3'd2;
  parameter S_ADDR_RECEIVE = 3'd3;
  parameter S_DUMMY_CYCLES = 3'd4;
  parameter S_DATA_XFER_READ = 3'd5;
  parameter S_DATA_XFER_WRITE = 3'd6;

  reg [2:0] r_fsm_state;

  reg [7:0] command_byte;  // Stores the received command byte
  reg [ADDR_BITS-1:0] current_address;  // Stores the current address for read/write
  reg [5:0] bit_counter;        // Counter for bits/nibbles within a state (e.g., 2 nibbles for command, 6 for address)
  reg [2:0] byte_counter;
  reg [5:0] dummy_cycle_counter;  // Counter for dummy cycles

  reg io_output_enable;         // Controls whether the PSRAM drives the IO lines (1'b1 for output, 1'b0 for input)
  reg [3:0] qspi_data_in;  // Buffer for 4-bit data input from controller
  reg [3:0] qspi_data_out;  // Buffer for 4-bit data output to controller
  reg qspi_mode;

  always @(posedge sclk or negedge sclk or negedge csn or posedge csn) begin
    case (r_fsm_state)
      S_IDLE: begin
        // csn == 0 sclk == 0
        if (csn == 0) begin
          bit_counter         <= '0;
          byte_counter        <= '0;
          dummy_cycle_counter <= '0;
          current_address     <= '0;
          command_byte        <= '0;
          io_output_enable    <= '0;
          if (~qspi_mode) r_fsm_state <= S_CMD_SPI_RECV;
          else r_fsm_state <= S_CMD_QPI_RECV;
        end
      end
      S_CMD_SPI_RECV: begin
        if (csn == 0 && sclk == 1) begin
          command_byte[7-bit_counter] <= sio[0];
          bit_counter                 <= bit_counter + 1;
          if (bit_counter == 7) begin
            bit_counter <= 0;
            // verilog_format: off
          case ({command_byte[7:1], sio[0]})
          // verilog_format: on
              8'h66:   r_fsm_state <= S_IDLE;  // reset ena
              8'h99:   r_fsm_state <= S_IDLE;  // reset
              8'h35: begin
                r_fsm_state <= S_IDLE;  // enter QPI mode
                qspi_mode   <= 1'b1;
              end
              default: r_fsm_state <= S_IDLE;
            endcase
          end
        end
      end
      S_CMD_QPI_RECV: begin
        if (csn == 0 && sclk == 1) begin
          qspi_data_in = {sio[3], sio[2], sio[1], sio[0]};
          if (bit_counter == 0) begin
            command_byte[7:4] <= qspi_data_in;
          end else begin
            command_byte[3:0] <= qspi_data_in;
          end
          bit_counter <= bit_counter + 1;

          if (bit_counter == 1) begin
            bit_counter <= 0;
            case ({
              command_byte[7:4], qspi_data_in
            })
              8'hEB:   r_fsm_state <= S_ADDR_RECEIVE;  // Quad Read command
              8'h38:   r_fsm_state <= S_ADDR_RECEIVE;  // Quad Write command
              default: r_fsm_state <= S_IDLE;
            endcase
          end
        end
      end

      S_ADDR_RECEIVE: begin
        if (csn == 0 && sclk == 1) begin
          // Sample 4 bits for address. Address is 23 bits, so 6 nibbles + 1 bit.
          // For simplicity, we'll assume 6 nibbles (24 bits) and ignore the highest bit if 23-bit address.
          qspi_data_in = {sio[3], sio[2], sio[1], sio[0]};
          // Store nibbles into current_address (MSB first)
          current_address[ADDR_BITS-1-(bit_counter*4)-:4] <= qspi_data_in;
          bit_counter                                     <= bit_counter + 1;

          if (bit_counter == ((ADDR_BITS + 3) / 4) - 1) begin // After all address bits (e.g., 23 bits -> 6 nibbles)
            bit_counter <= 0;  // Reset for next stage
            // Transition to Dummy cycles or data transfer based on command
            if (command_byte == 8'hEB) begin  // Quad Read
              r_fsm_state <= S_DUMMY_CYCLES;  // PSRAM64H typically has dummy cycles
            end else if (command_byte == 8'h38) begin  // Quad Write
              r_fsm_state <= S_DATA_XFER_WRITE;
            end else begin
              r_fsm_state <= S_IDLE;  // Should not happen if command was valid
            end
          end
        end
      end

      S_DUMMY_CYCLES: begin
        if (csn == 0 && sclk == 1) begin
          dummy_cycle_counter <= dummy_cycle_counter + 1;
          if (dummy_cycle_counter == 5) begin
            dummy_cycle_counter <= 0;
            bit_counter         <= 0;
            io_output_enable    <= 1'b1;
            r_fsm_state         <= S_DATA_XFER_READ;
          end
        end else begin
          // Check datasheet for exact number of dummy cycles (e.g., 6 cycles for Quad Read)
          if (dummy_cycle_counter == 6) begin
          end
          if (dummy_cycle_counter <= 5) begin
            // $display("time: %t", $time);
          end
        end
      end

      S_DATA_XFER_READ: begin
        if (csn == 1) begin
          r_fsm_state      <= S_IDLE;
          io_output_enable <= 1'b0;
        end else if (csn == 0 && sclk == 0) begin
          // Read 4 bits from mem_array and drive onto IO lines
          // Data is read byte by byte, then split into nibbles
          qspi_data_out <= mem_array[current_address][7-(bit_counter*4)-:4];  // MSB first nibble
          bit_counter   <= bit_counter + 1;

          if (bit_counter == 1) begin  // After 2 nibbles (1 byte)
            bit_counter     <= 0;
            byte_counter    <= byte_counter + 1;
            current_address <= current_address + 1;  // Increment address for burst read
            // Handle address overflow if needed, though for simulation, it might wrap around.
            if (current_address == MEM_DEPTH_BYTES - 1) begin
              current_address <= 0;  // Wrap around if end of memory
            end
          end else begin
            if (byte_counter == 3'd4) begin
              byte_counter     <= 3'd0;
              r_fsm_state      <= S_IDLE;
              io_output_enable <= 1'b0;
            end
          end
        end
      end

      S_DATA_XFER_WRITE: begin
        if (csn == 1) begin
          r_fsm_state      <= S_IDLE;
          io_output_enable <= 1'b0;
        end else if (csn == 0 && sclk == 1) begin
          // Sample 4 bits from IO lines and write to mem_array
          qspi_data_in = {sio[3], sio[2], sio[1], sio[0]};
          // Write nibbles into mem_array (MSB first)
          mem_array[current_address][7-(bit_counter*4)-:4] <= qspi_data_in;
          bit_counter                                      <= bit_counter + 1;
          if (bit_counter == 1) begin  // After 2 nibbles (1 byte)
            bit_counter     <= 0;
            current_address <= current_address + 1;  // Increment address for burst write
            if (current_address == MEM_DEPTH_BYTES - 1) begin
              current_address <= 0;  // Wrap around if end of memory
            end
          end
        end
      end
      default: begin
        r_fsm_state      <= S_IDLE;
        io_output_enable <= 1'b0;
      end
    endcase
  end

  // --- Tri-state Buffer for IO Lines ---
  assign sio[0] = io_output_enable ? qspi_data_out[0] : 1'bz;
  assign sio[1] = io_output_enable ? qspi_data_out[1] : 1'bz;
  assign sio[2] = io_output_enable ? qspi_data_out[2] : 1'bz;
  assign sio[3] = io_output_enable ? qspi_data_out[3] : 1'bz;

  // --- Initial Memory Content (Optional) ---
  initial begin
    integer i;
    for (i = 0; i < MEM_DEPTH_BYTES; i = i + 1) begin
      mem_array[i] = i[7:0];  // Initialize with address lower 8 bits
    end
    qspi_mode   = 1'b0;
    r_fsm_state = S_IDLE;
    io_output_enable = 1'b0;
    $display("PSRAM Model: Initialized memory with address pattern for device %0d.", ID);
  end

  // --- Debugging (Optional) ---
  // Use $display statements to monitor state changes and data transfers.
  // initial begin
  //     $monitor("Time: %0t, csn: %b, sclk: %b, sio[0]: %b, sio[1]: %b, sio[2]: %b, sio[3]: %b, State: %s, Cmd: %h, Addr: %h, BitCnt: %d, DummyCnt: %d, IO_OE: %b",
  //              $time, csn, sclk, sio[0], sio[1], sio[2], sio[3],
  //              (r_fsm_state == S_IDLE) ? "IDLE" :
  //              (r_fsm_state == S_CMD_QPI_RECV) ? "CMD_REC" :
  //              (r_fsm_state == S_ADDR_RECEIVE) ? "ADDR_REC" :
  //              (r_fsm_state == S_DUMMY_CYCLES) ? "DUMMY" :
  //              (r_fsm_state == S_DATA_XFER_READ) ? "READ" :
  //              (r_fsm_state == S_DATA_XFER_WRITE) ? "WRITE" : "UNKNOWN",
  //              command_byte, current_address, bit_counter, dummy_cycle_counter, io_output_enable);
  // end

endmodule
