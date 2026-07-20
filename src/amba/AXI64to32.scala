package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.amba.axi4._
import chisel3.experimental._

object MyAXI4BundleParameters {
  def apply(dataBits: Int) = AXI4BundleParameters(addrBits = 32, dataBits = dataBits, idBits = Config.idBits)
}

class AXI64to32 extends Module {
  val io = IO(new Bundle {
    val out = AXI4Bundle(MyAXI4BundleParameters(32))
    val in = Flipped(AXI4Bundle(MyAXI4BundleParameters(64)))
  })

  io.out <> io.in
  io.in.r.bits.data := Fill(2, io.out.r.bits.data)

  // for read
  prefix("r") {
    val over_size = (io.in.ar.bits.size >= 3.U)
    val over_len = (io.in.ar.bits.len > 0.U)
    val run_flag = RegInit(UInt(3.W), 0.U)
    val ram = Reg(Vec(16, UInt(64.W)))
    val ram_index = RegInit(UInt(4.W), 0.U)
    when (io.in.ar.valid && (over_size || over_len) && (run_flag === 0.U)) {
      // for 64 bits
      assert(io.in.ar.bits.size === 3.U, "ar.bits.size must be 3.U")
      io.out.ar.bits.len := io.in.ar.bits.len ## 1.U(1.W)
      io.out.ar.bits.size := 2.U

      when (io.in.ar.fire) {
        run_flag := 1.U
        ram_index := 0.U
      }
    }

    val r_data_q = RegInit(UInt(32.W), 0.U)
    val out_r_ready = RegInit(false.B)
    val cnt = RegInit(UInt(4.W), 0.U)
    out_r_ready := Mux(run_flag === 1.U, !io.out.r.fire, false.B)
    io.out.r.ready := Mux(run_flag === 1.U, out_r_ready, Mux(run_flag === 2.U, false.B, io.in.r.ready))
    when (run_flag === 1.U) {
      when(io.out.r.fire) {
        cnt := Mux(io.out.r.bits.last, 0.U, cnt + 1.U)
        r_data_q := io.out.r.bits.data
        when ((cnt(1, 0) === 1.U) || (cnt(1, 0) === 3.U)) {
          when (!io.out.r.bits.last) { ram_index := ram_index + 1.U }
          ram(ram_index) := io.out.r.bits.data ## r_data_q
        }
        when (io.out.r.bits.last) { run_flag := 2.U }
      }
    } .otherwise { r_data_q := 0.U }

    // forward read data
    val in_r_valid = RegInit(false.B)
    in_r_valid := Mux(run_flag === 2.U, !io.in.r.fire, false.B)
    io.in.r.valid := Mux(run_flag === 2.U, in_r_valid, Mux(run_flag === 1.U, false.B, io.out.r.valid))
    when (run_flag === 2.U) {
      io.in.r.bits.data := ram(cnt)
      io.in.r.bits.last := io.in.r.fire && (ram_index === cnt)
      when (io.in.r.fire) {
        cnt := Mux(ram_index === cnt, 0.U, cnt + 1.U)
        when (ram_index === cnt) {
          run_flag := 0.U
        }
      }
    }
  }

  // for write
  prefix("w") {
    val over_size = (io.in.aw.bits.size >= 3.U)
    val over_len = (io.in.aw.bits.len > 0.U)

    val run_flag = RegInit((UInt(3.W)), 0.U)
    val len_cnt = RegInit(UInt(9.W), 0.U)
    val ram  = Reg(Vec(16,(UInt(64.W))))
    val ram_index = RegInit(UInt(4.W), 0.U)
    val id = RegInit(UInt(4.W), 0.U)
    val addr = RegInit(UInt(32.W), 0.U)
    val burst = RegInit(UInt(2.W), 0.U)
    when (io.in.aw.valid && (over_size || over_len) && (run_flag === 0.U)) {
      // for 64 bits
      assert(io.in.aw.bits.size === 3.U, "aw.bits.size must be 3.U")
      assert(io.in.w.bits.strb === 255.U, "w.bits.strb must be 11111111 now")
      len_cnt := io.in.aw.bits.len ## 1.U(1.W)
      io.out.aw.valid := false.B
      io.out.w.valid := false.B
      id := io.in.aw.bits.id
      addr := io.in.aw.bits.addr
      burst := io.in.aw.bits.burst
      ram_index := ram_index + 1.U
      ram(ram_index) := io.in.w.bits.data
      run_flag := 1.U
    }

    // accept write data
    val in_w_ready = RegInit(false.B)
    in_w_ready := Mux(run_flag === 1.U, !io.in.w.fire, false.B)
    io.in.w.ready := Mux(run_flag === 1.U, in_w_ready, Mux(run_flag === 2.U, false.B, io.out.w.ready))
    when (run_flag === 1.U) {
      when (io.in.w.fire) {
        ram_index := Mux(io.in.w.bits.last, 0.U, ram_index + 1.U)
        ram(ram_index) := io.in.w.bits.data
        when (io.in.w.bits.last) {
          run_flag := 2.U
        }
      }
    }

    // forward AW transaction
    val out_aw_valid = RegInit(false.B)
    out_aw_valid := Mux(run_flag =/= 2.U, true.B, Mux(io.out.aw.fire, false.B, out_aw_valid))
    when (run_flag === 2.U) {
      io.out.aw.valid := out_aw_valid
      io.out.aw.bits.id := id
      io.out.aw.bits.addr := addr
      io.out.aw.bits.burst := burst
      io.out.aw.bits.size := 2.U
      io.out.aw.bits.len := len_cnt
    } .otherwise {
      when (run_flag =/= 0.U) {
        io.out.aw.valid := false.B
      }
    }

    // forward W transaction
    val ready_in_data = RegInit(false.B)
    val out_w_valid = RegInit(false.B)
    out_w_valid := Mux(run_flag === 2.U, !io.out.w.fire, false.B)
    when (run_flag === 2.U) {
      io.out.w.valid := out_w_valid
      io.out.w.bits.last := io.out.w.fire && (len_cnt === 0.U)
      io.out.w.bits.strb := 15.U

      val ram_data = ram(ram_index)
      io.out.w.bits.data := Mux(!len_cnt(0),ram_data(63, 32), ram_data(31, 0))
      when (io.out.w.fire) {
        len_cnt := len_cnt - 1.U
        when ((len_cnt(1,0) === 0.U) || (len_cnt(1,0) === 2.U)) {
          ram_index := ram_index + 1.U
        }
        when (len_cnt === 0.U) {
          run_flag := 0.U
          ram_index := 0.U
        }
      }
    } .otherwise {
      when (run_flag =/= 0.U) {
        io.out.w.valid := false.B
      }
    }
  }
}
