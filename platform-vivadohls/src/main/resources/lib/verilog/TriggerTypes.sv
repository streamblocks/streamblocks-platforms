`ifndef __TRIGGER_TYPES__
`define __TRIGGER_TYPES__
package TriggerTypes;
	typedef enum logic[31:0] {
			IDLE,
			WAIT_PREDICATE,
			WAIT_INPUT,
			WAIT_OUTPUT,
			WAIT_GAURD,
			EXECUTED
	} return_t;
	typedef enum logic[1:0] {
		STAND_BY,
		TRY_LAUNCH,
		LAUNCH,
		CHECK_RETURN
	}state_t;
	typedef enum integer{
		ACTOR_TRIGGER,
		INPUT_TRIGGER,
		OUTPUT_TRIGGER
	}mode_t;

	function state_t
		nextState(
									input state_t state,
									input logic ap_start,
									input logic actor_done,
									input logic can_finish,
									input logic can_sleep,
									input logic can_launch);
		state_t next_state;
		case (state)
			STAND_BY: begin
				if (ap_start)
					next_state = TRY_LAUNCH;
				else
					next_state = STAND_BY;
			end
			TRY_LAUNCH: begin
				if (can_launch)
					next_state = LAUNCH;
				else
					next_state = TRY_LAUNCH;
			end
			LAUNCH: begin
				if(~actor_done)
					next_state = CHECK_RETURN;
				else if (can_finish)
					next_state = STAND_BY;
				else if (can_sleep)
					next_state = LAUNCH;
			end
			CHECK_RETURN: begin
				if (can_finish)
					next_state = STAND_BY;
				else if (can_sleep)
					next_state = LAUNCH;
				else
					next_state = CHECK_RETURN;
			end
		endcase
		return next_state;
	endfunction
endpackage
`endif