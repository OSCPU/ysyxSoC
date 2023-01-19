//APB
`define P_ADDR_W 32
`define P_DATA_W 32
`define P_STRB_W `P_DATA_W/8

//AXI
//`define A_ID_W    1
`define A_ID_W    5
`define A_ADDR_W  32
`define A_DATA_W  64
`define A_STRB_W  `A_DATA_W/8
`define A_SIZE_W  3
`define A_BURST_W 2
`define A_LEN_W   8
`define A_RESP_W  2
`define A_QOS_W   4
`define A_CACHE_W 4
`define A_LOCK_W  1
`define A_PROT_W  3
`define A_USER_W  1
`define A_LAST_W  1
