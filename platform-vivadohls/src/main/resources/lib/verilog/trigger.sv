/*
 * Copyright (c) EPFL VLSC, 2019
 * Author: Mahyar Emami (mahyar.emami@epfl.ch)
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
	state_t state = STAND_BY;
	wire can_finish;
	wire can_launch;
	wire can_relaunch;
	wire can_sleep;
	wire executed;
	always_ff @(posedge ap_clk) begin
        if (~ap_rst_n)
                state <= STAND_BY;
        else
            state <= nextState(state, ap_start, actor_done, can_finish, can_sleep, can_launch, can_relaunch);
	end


	assign executed = (actor_return == EXECUTED);
	generate
		if (mode == ACTOR_TRIGGER) begin
			assign can_launch = actor_idle && ~ap_start;
			assign can_finish = (~executed) && network_idle && actor_done;
			assign can_sleep = ((executed) || (~network_idle)) && actor_done;
			assign can_relaunch = can_sleep;
		end
		else if (mode == INPUT_TRIGGER || mode == OUTPUT_TRIGGER) begin
			assign can_launch = actor_idle && ~ap_start && actor_launch_predicate;
			assign can_finish = ~executed && actor_done;
			assign can_sleep = executed && actor_done;
			assign can_relaunch = can_sleep && actor_launch_predicate;
		end
	endgenerate


	assign actor_start = (state == LAUNCH);
	assign ap_idle = (state == STAND_BY);
	assign ap_done = (state == CHECK_RETURN && can_finish);

endmodule : trigger