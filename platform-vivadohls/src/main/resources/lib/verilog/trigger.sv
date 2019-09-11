/*
 * Copyright (c) EPFL VLSC, 2019
 * Author: Mahyar Emami (mahyar.emami@epfl.ch)
           Endri Bezati (endri.bezati@epfl.ch)
 * All rights reserved.
 *
 * License terms:
 *
 * Redistribution and use in source and binary forms,
 * with or without modification, are permitted provided
 * that the following conditions are met:
 *     * Redistributions of source code must retain the above
 *       copyright notice, this list of conditions and the
 *       following disclaimer.
 *     * Redistributions in binary form must reproduce the
 *       above copyright notice, this list of conditions and
 *       the following disclaimer in the documentation and/or
 *       other materials provided with the distribution.
 *     * Neither the name of the copyright holder nor the names
 *       of its contributors may be used to endorse or promote
 *       products derived from this software without specific
 *       prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
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
		input wire has_tokens,

    input wire[31:0] actor_return,
    input wire actor_done,
    input wire actor_ready,
    input wire actor_idle,
		input wire actor_launch_predicate,
    output wire actor_start,
		output wire sleeping

);
	timeunit 1ps;
	timeprecision 1ps;
	parameter mode_t mode = ACTOR_TRIGGER;
	state_t state = STAND_BY;
	state_t next_state;

	wire can_launch;

	always_ff @(posedge ap_clk) begin
        if (~ap_rst_n)
                state <= STAND_BY;
        else
            state <= next_state;
	end

	state_t RE_LAUNCH = (mode == ACTOR_TRIGGER) ? LAUNCH : TRY_LAUNCH;

	generate
		if (mode == ACTOR_TRIGGER) begin
			assign can_launch = actor_idle && ~ap_start;

		end
		else if (mode == INPUT_TRIGGER || mode == OUTPUT_TRIGGER) begin
			assign can_launch = actor_idle && ~ap_start && actor_launch_predicate;

		end
	endgenerate


	assign actor_start = (state == LAUNCH);
	assign ap_idle = (state == STAND_BY);
	assign ap_done = (state == CHECK_RETURN | state == PROBE_INPUT) & (next_state == STAND_BY);
	assign sleeping = (state == PROBE_INPUT) || (state == STAND_BY);


	always_comb begin
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
				else if (actor_done && actor_return == EXECUTED)
					next_state = RE_LAUNCH;
				else if (actor_done && (actor_return == WAIT_INPUT || ~network_idle))
					next_state = PROBE_INPUT;
				else
					next_state = TRY_LAUNCH;
			end
			CHECK_RETURN: begin
				if (~actor_done)
					next_state = CHECK_RETURN;
				else if (actor_done && actor_return == EXECUTED)
					next_state = RE_LAUNCH;
				else if (actor_done && actor_return == WAIT_INPUT)
					next_state = PROBE_INPUT;
				else if (actor_done && ~network_idle) begin
					next_state = PROBE_INPUT;
				end
				else
					next_state = STAND_BY;

			end
			PROBE_INPUT:begin
				if(has_tokens && ~network_idle)
					next_state = RE_LAUNCH;
				else if (network_idle)
					next_state = STAND_BY;
				else
					next_state = PROBE_INPUT;
			end
			default:
				next_state = state;
		endcase

	end

endmodule : trigger