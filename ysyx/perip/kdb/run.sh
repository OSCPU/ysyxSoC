#!/bin/zsh

iverilog kdb.v kdb_tb.v -o kdb.out
vvp -n kdb.out -fst
