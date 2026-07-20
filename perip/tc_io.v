module tc_io_xtl_pad (
  input  xi_pad,
  output xo_pad,
  input  en,
  output clk
);
`ifdef PDK_BEHAV
  assign clk    = en ? xi_pad : 1'b0;
  assign xo_pad = xi_pad;
`elsif PDK_ICS55
  (* keep *) (* dont_touch = "true" *)
  P65_1233_PWE u_P65_1233_PWE (
    .E   (en),
    .XIN (xi_pad),
    .XOUT(xo_pad),
    .XC  (clk)
  );
`else
  always $fatal(1, "No PDK is specified");
`endif
endmodule

module tc_io_xtl_pad_V (
  input  xi_pad,
  output xo_pad,
  input  en,
  output clk
);
  tc_io_xtl_pad inst(.xi_pad(xi_pad), .xo_pad(xo_pad), .en(en), .clk(clk));
endmodule

module tc_io_xtl_pad_H (
  input  xi_pad,
  output xo_pad,
  input  en,
  output clk
);
  tc_io_xtl_pad inst(.xi_pad(xi_pad), .xo_pad(xo_pad), .en(en), .clk(clk));
endmodule

module tc_io_in_pad (
  input  pad,
  output p2c
);
`ifdef PDK_BEHAV
  assign p2c = pad;
`elsif PDK_ICS55
  tc_io_tri_pad pad(
    .pad(pad),
    .c2p(1'b0),
    .c2p_en(1'b0),
    .p2c(p2c)
  )
`else
  always $fatal(1, "No PDK is specified");
`endif
endmodule

module tc_io_in_pad_V (
  input  pad,
  output p2c
);
  tc_io_in_pad inst(.pad(pad), .p2c(p2c));
endmodule

module tc_io_in_pad_H (
  input  pad,
  output p2c
);
  tc_io_in_pad inst(.pad(pad), .p2c(p2c));
endmodule

module tc_io_out_pad (
  output pad,
  input  c2p
);
`ifdef PDK_BEHAV
  assign pad = c2p;
`elsif PDK_ICS55
  tc_io_tri_pad pad(
    .pad(pad),
    .c2p(c2p),
    .c2p_en(1'b1),
    .p2c()
  )
`else
  always $fatal(1, "No PDK is specified");
`endif
endmodule

module tc_io_out_pad_V (
  output pad,
  input  c2p
);
  tc_io_out_pad inst(.pad(pad), .c2p(c2p));
endmodule

module tc_io_out_pad_H (
  output pad,
  input  c2p
);
  tc_io_out_pad inst(.pad(pad), .c2p(c2p));
endmodule

module tc_io_tri_pad (
  inout  pad,
  input  c2p,
  input  c2p_en,
  output p2c
);
`ifdef PDK_BEHAV
  assign pad = c2p_en ? c2p : 1'bz;
  assign p2c = pad;
`elsif PDK_ICS55
  (* keep *) (* dont_touch = "true" *)
  P65_1233_PBMUX u_P65_1233_PBMUX (
    .C  (p2c),
    .A  (),
    .PAD(pad),
    .IE (~c2p_en),
    .CS (1'b1), // 1: CMOS 0: SCHMI
    .I  (c2p),
    .OE (c2p_en),
    .OD (1'b0),
    .PU (1'b0),
    .PD (1'b0),
    .DS0(1'b0),
    .DS1(1'b1) // 8mA
  );
`else
  always $fatal(1, "No PDK is specified");
`endif
endmodule

module tc_io_tri_pad_V (
  inout  pad,
  input  c2p,
  input  c2p_en,
  output p2c
);
  tc_io_tri_pad inst(.pad(pad), .c2p(c2p), .c2p_en(c2p_en), .p2c(p2c));
endmodule

module tc_io_tri_pad_H (
  inout  pad,
  input  c2p,
  input  c2p_en,
  output p2c
);
  tc_io_tri_pad inst(.pad(pad), .c2p(c2p), .c2p_en(c2p_en), .p2c(p2c));
endmodule

module tc_io_tri_schmitt_pad (
  inout  pad,
  input  c2p,
  input  c2p_en,
  output p2c
);
`ifdef PDK_BEHAV
  assign pad = c2p_en ? c2p : 1'bz;
  assign p2c = pad;
`elsif PDK_ICS55
  (* keep *) (* dont_touch = "true" *)
  P65_1233_PBMUX u_P65_1233_PBMUX (
    .C  (p2c),
    .A  (),
    .PAD(pad),
    .IE (~c2p_en),
    .CS (1'b0), // 1: CMOS 0: SCHMI
    .I  (c2p),
    .OE (c2p_en),
    .OD (1'b0),
    .PU (1'b0),
    .PD (1'b0),
    .DS0(1'b0),
    .DS1(1'b1) // 8mA
  );
`else
  always $fatal(1, "No PDK is specified");
`endif
endmodule
