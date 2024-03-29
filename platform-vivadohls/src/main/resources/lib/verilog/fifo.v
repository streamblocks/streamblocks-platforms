// ----------------------------------------------------------------------------
// -- This file is generated automatically by StreamBlocks
// -- FIFO Queue from used by Vivado HLS streaming interface
// -- Modified to include count output, removed clock enables in write & read
// -- Copyright (C) 2015 Xilinx Inc
// ----------------------------------------------------------------------------
`timescale 1ns/1ps

module FIFO
#(parameter
    MEM_STYLE  = "auto",
    DATA_WIDTH = 32,
    ADDR_WIDTH = 9
)
(
    // system signal
    input  wire                  clk,
    input  wire                  reset_n,

    // write
    output wire                  if_full_n,
    input  wire                  if_write,
    input  wire [DATA_WIDTH-1:0] if_din,

    // read
    output wire                  if_empty_n,
    input  wire                  if_read,
    output wire [DATA_WIDTH-1:0] if_dout,

    // peek
    output wire [DATA_WIDTH-1:0] peek,

    // size
    output wire [31:0] size,

    // count
    output [31:0] count
);

localparam DEPTH = 1 << ADDR_WIDTH;

//------------------------Local signal-------------------
(* ram_style = MEM_STYLE *)
reg  [DATA_WIDTH-1:0] mem[0:DEPTH-1];
reg  [DATA_WIDTH-1:0] q_buf = 1'b0;
reg  [ADDR_WIDTH-1:0] waddr = 1'b0;
reg  [ADDR_WIDTH-1:0] raddr = 1'b0;
wire [ADDR_WIDTH-1:0] wnext;
wire [ADDR_WIDTH-1:0] rnext;
wire                  push;
wire                  pop;
reg  [ADDR_WIDTH-1:0] usedw = 1'b0;
reg                   full_n = 1'b1;
reg                   empty_n = 1'b0;
reg  [DATA_WIDTH-1:0] q_tmp = 1'b0;
reg                   show_ahead = 1'b0;
reg  [DATA_WIDTH-1:0] dout_buf = 1'b0;
reg                   dout_valid = 1'b0;

reg [31:0] count_r;

//------------------------Body---------------------------
assign if_full_n  = full_n;
assign if_empty_n = dout_valid;
assign if_dout    = dout_buf;
assign push       = full_n & if_write;
assign pop        = empty_n & (~dout_valid | if_read);
assign wnext      = !push                ? waddr :
                    (waddr == DEPTH - 1) ? 1'b0  :
                    waddr + 1'b1;
assign rnext      = !pop                 ? raddr :
                    (raddr == DEPTH - 1) ? 1'b0  :
                    raddr + 1'b1;
assign peek       = dout_buf;
assign size       = DEPTH;

assign count = (dout_valid) ? count_r : 32'b0;

// waddr
always @(posedge clk) begin
    if (reset_n == 1'b0)
        waddr <= 1'b0;
    else
        waddr <= wnext;
end

// raddr
always @(posedge clk) begin
    if (reset_n == 1'b0)
        raddr <= 1'b0;
    else
        raddr <= rnext;
end

// usedw
always @(posedge clk) begin
    if (reset_n == 1'b0)
        usedw <= 1'b0;
    else if (push & ~pop)
        usedw <= usedw + 1'b1;
    else if (~push & pop)
        usedw <= usedw - 1'b1;
end

// count
always @(posedge clk) begin
    if (reset_n == 1'b0)
        count_r <= 1'b0;
    else if((if_full_n & if_write) & (if_empty_n & if_read))
    	count_r <= count_r;
    else if (if_full_n & if_write)
        count_r <= count_r + 1'b1;
    else if (if_empty_n & if_read)
        count_r <= count_r - 1'b1;
    else
    	count_r <= count_r;
end

// full_n
always @(posedge clk) begin
    if (reset_n == 1'b0)
        full_n <= 1'b1;
    else if (push & ~pop)
        full_n <= (usedw != DEPTH - 1);
    else if (~push & pop)
        full_n <= 1'b1;
end

// empty_n
always @(posedge clk) begin
    if (reset_n == 1'b0)
        empty_n <= 1'b0;
    else if (push & ~pop)
        empty_n <= 1'b1;
    else if (~push & pop)
        empty_n <= (usedw != 1'b1);
end

// mem
always @(posedge clk) begin
    if (push)
        mem[waddr] <= if_din;
end

// q_buf
always @(posedge clk) begin
    q_buf <= mem[rnext];
end

// q_tmp
always @(posedge clk) begin
    if (reset_n == 1'b0)
        q_tmp <= 1'b0;
    else if (push)
        q_tmp <= if_din;
end

// show_ahead
always @(posedge clk) begin
    if (reset_n == 1'b0)
        show_ahead <= 1'b0;
    else if (push && usedw == pop)
        show_ahead <= 1'b1;
    else
        show_ahead <= 1'b0;
end

// dout_buf
always @(posedge clk) begin
    if (reset_n == 1'b0)
        dout_buf <= 1'b0;
    else if (pop)
        dout_buf <= show_ahead? q_tmp : q_buf;
end

// dout_valid
always @(posedge clk) begin
    if (reset_n == 1'b0)
        dout_valid <= 1'b0;
    else if (pop)
        dout_valid <= 1'b1;
    else if (if_read)
        dout_valid <= 1'b0;
end

endmodule