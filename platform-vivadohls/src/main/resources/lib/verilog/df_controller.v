`timescale 1ns / 1ps
`define RETURN_IDLE 0
`define RETURN_WAIT_PREDICATE 1
`define RETURN_WAIT_INPUT 2
`define RETURN_WAIT_OUTPUT 3
`define RETURN_WAIT_GUARD 4
`define RETURN_EXECUTED 5

module df_controller
(
    input  clk,
    input  rst_n,
    input  ap_start,
    input  ap_done,
    input  wire[31:0] ap_return,
    input  available_data,
    input  others_executing,
    output reg start
);

localparam [2:0]
    s0 = 0,
    s1 = 1,
    s2 = 2,
    s3 = 3,
    s4 = 4;

    reg[2:0] state_reg, state_next;


always @(posedge clk, posedge rst_n)
begin
    if (rst_n == 1'b0) begin
        start <= 0;
        state_reg <= s0;
    end
    else begin
        state_reg <= state_next;
    end
end


always @(ap_start, ap_done, state_reg, available_data) begin
    state_next = state_reg; // default state_next
    // default outputs
    start = 1'b0;

    case (state_reg)
        // -- Wait for ap_start
        s0 : begin
            if(ap_start) begin
                state_next = s1;
            end
            else begin
                state_next = s0;
            end
        end

        // -- Start the Actor
        s1 : begin
            start = 1'b1;
            if(ap_done) begin
                state_next = s1;
            end
            else begin
                state_next = s2;
            end
        end

        // -- Wait for done
        s2 : begin
            if(ap_done) begin
                start = 1'b1;
                state_next = s3;
            end
            else begin
                start = 1'b0;
                state_next = s2;
            end

        end

        // -- Check Actors return value
        s3 : begin
            if(ap_return == `RETURN_EXECUTED) begin
               start = 1'b1;
               state_next = s2;
            end
            else if ( ap_return == `RETURN_WAIT_INPUT )begin
               start = 1'b0;
               state_next = s4;
            end
            else begin
                if(available_data || others_executing) begin
                   start = 1'b1;
                   state_next = s2;
                end
                else begin
                   state_next = s0;
                end
            end
        end

        // -- Wait for input
        s4 : begin
            if (~available_data) begin
                state_next = s4;
            end
            else begin
                start = 1'b1;
                state_next = s1;
            end
        end

    endcase
end

endmodule