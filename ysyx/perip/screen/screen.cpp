#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>
#include <assert.h>
#include <stdlib.h>
#include <cstring>
#include "svdpi.h"
#include "verilated_dpi.h"

uint32_t *frame_buf = NULL;
extern "C" void set_gpr_ptr(const svOpenArrayHandle r) {
  frame_buf = (uint32_t *)(((VerilatedDpiOpenVar*)r)->datap());
}

void dump_gpr() {
//   for (int i = 0; i < 400 * 300; ++i) {
    // printf("frame_buf[%d] = 0x%x\n", i, frame_buf[i]);
//   }
}