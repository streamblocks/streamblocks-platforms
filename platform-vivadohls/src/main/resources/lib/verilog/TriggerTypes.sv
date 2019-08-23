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
									input logic can_launch,
									input logic can_relaunch);
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
				else
					next_state = TRY_LAUNCH;
			end
			CHECK_RETURN: begin
				if (can_finish)
					next_state = STAND_BY;
				else if (can_sleep)
					if(can_relaunch)
						next_state = LAUNCH;
					else
						next_state = TRY_LAUNCH;
				else
					next_state = CHECK_RETURN;
			end
		endcase
		return next_state;
	endfunction
endpackage
`endif