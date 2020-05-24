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

    // Signal indicating whether there is external enqueue of buffers
    input wire external_enqueue,
    // signal indicating whether all actors are in a SYNC_{WAIT, EXEC} state
    input wire all_sync,
    // signal indicating whether all actors are in a SYNC_WAIT state
    input wire all_sync_wait,
    // signal indicating whether all actors are in SLEEP state
    input wire all_sleep,
    // signal indicating the last step taken by every other actor was a WAIT
    input wire all_waited,

    output wire sleep,	//actor is sleeping
    output wire sync_exec, //actor returned EXECUTED in a synced step
    output wire sync_wait,	//actor returned ~EXECUTED in a synced step
    output wire waited,     //actor returned WAIT or IDLE on last step

    input wire[1:0] actor_return,
    input wire actor_done,
    input wire actor_ready,
    input wire actor_idle,
    output wire actor_start
);
	timeunit 1ps;
	timeprecision 1ps;
	parameter mode_t mode = ACTOR_TRIGGER; // deprecated
	state_t state = IDLE_STATE;
	state_t next_state;

    logic waited_on_last_step = 1'b0;

    always_ff @(posedge ap_clk) begin
        if (~ap_rst_n)
            waited_on_last_step <= 1'b0;
        else if (actor_done) begin
            if (actor_return == WAIT || actor_return == IDLE)
                waited_on_last_step <= 1'b1;
            else
                waited_on_last_step <= 1'b0;
        end
    end
	always_ff @(posedge ap_clk) begin
        if (~ap_rst_n) 
            state <= IDLE_STATE;
        else
            state <= next_state;
	end

	always_comb begin
        case (state)
          IDLE_STATE: begin
              if (ap_start)
                  next_state = LAUNCH;
              else
                  next_state = IDLE_STATE;
          end
          LAUNCH: begin
              if (actor_done) begin
                  if (actor_return == EXECUTED ||  actor_return == TEST || external_enqueue)
                      next_state = LAUNCH;
                  else // (actor_return != EXECUTED && !external_enqueue)
                      next_state = SLEEP;
              end
              else begin // !actor_done
                  next_state = CHECK;
              end
          end
          CHECK: begin
              if (actor_done) begin
                  if (actor_return == EXECUTED || actor_return == TEST || external_enqueue)
                      next_state = LAUNCH;
                  else // (actor_return != EXECUTED && !external_enqueue)
                      next_state = SLEEP;
              end
              else begin
                  next_state = CHECK;
              end
          end
          SLEEP: begin
              if (all_sleep)
                  next_state = SYNC_LAUNCH;
              else if (!all_waited)
                  next_state = LAUNCH;
              else
                  next_state = SLEEP;
          end
          SYNC_LAUNCH: begin
              if (actor_done) begin
                  if (actor_return == EXECUTED)
                      next_state = SYNC_EXEC;
                  else if (actor_return == TEST)
                      next_state = SYNC_LAUNCH;
                  else
                      next_state = SYNC_WAIT;
              end
              else begin
                  next_state = SYNC_CHECK;
              end
          end
          SYNC_CHECK: begin
              if (actor_done) begin
                  if (actor_return == EXECUTED)
                      next_state = SYNC_EXEC;
                  else if (actor_return == TEST)
                      next_state = SYNC_LAUNCH;
                  else
                      next_state = SYNC_WAIT;
              end
              else begin
                  next_state = SYNC_CHECK;
              end
          end
          SYNC_WAIT: begin
              if (all_sync) begin
                  if(all_sync_wait)
                      next_state = IDLE_STATE;
                  else
                      next_state = LAUNCH;
              end
              else
                  next_state = SYNC_WAIT;
          end
          SYNC_EXEC: begin
              if (all_sync)
                  next_state = LAUNCH;
              else
                  next_state = SYNC_EXEC;
          end
        endcase
	end

	assign actor_start = (state == LAUNCH) | (state == SYNC_LAUNCH);
	assign ap_idle = (state == IDLE_STATE);
	assign sleep = (state == SLEEP || state == IDLE_STATE);
	assign sync_wait = (state == SYNC_WAIT | state == IDLE_STATE);
	assign sync_exec = (state == SYNC_EXEC);
	assign ap_done = (next_state == IDLE_STATE);
	assign ap_ready = ap_done;

    assign waited = waited_on_last_step;

endmodule : trigger