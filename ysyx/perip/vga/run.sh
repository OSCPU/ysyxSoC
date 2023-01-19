#!/bin/zsh

iverilog vga_ctrl.v vga_ctrl_tb.v -o vga_ctrl.out
vvp -n vga_ctrl.out -fst
