`include "TriggerTypes.sv"

import TriggerTypes::*;

module trigger
(
    input wire ap_clk,
    input wire ap_rst_n,
    input wire ap_start,
    output wire ap_done,
    output wire ap_ready,
    output wire ap_idle,

    input wire network_idle,

    input wire[31:0] actor_return,
    input wire actor_done,
    input wire actor_ready,
    input wire actor_idle,
	input wire actor_launch_predicate,
    output wire actor_start

);
	timeunit 1ps;
	timeprecision 1ps;
	parameter mode_t mode = ACTOR_TRIGGER;
	state_t state = STAND_BY, next_state;
	wire can_finish;
	wire can_launch;
	wire can_sleep;
	wire executed;
	always_ff @(posedge ap_clk) begin
			if (~ap_rst_n)
					state <= STAND_BY;
			else
					state <= nextState(state, ap_start, actor_done, can_finish, can_sleep, can_launch);
	end


	assign executed = (actor_return == EXECUTED);
	generate
		if (mode == ACTOR_TRIGGER) begin
			assign can_launch = actor_idle && ~ap_start;
			assign can_finish = (~executed) && network_idle && actor_done;
			assign can_sleep = ((executed) || (~network_idle)) && actor_done;
		end
		else if (mode == INPUT_TRIGGER || mode == OUTPUT_TRIGGER) begin
			assign can_launch = actor_idle && ~ap_start && actor_launch_predicate;
			assign can_finish = ~executed && actor_done;
			assign can_sleep = executed && actor_done;
		end
	endgenerate


	assign actor_start = (state == LAUNCH);
	assign ap_idle = (state == STAND_BY);
	assign ap_done = (state == CHECK_RETURN && can_finish);

endmodule : actor_scheduler