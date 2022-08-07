/*
 * Copyright (c) EPFL VLSC, 2021
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

`include "trigger_common.sv"
import TriggerCommon::*;

module Trigger (
  input wire ap_clk,
  input wire ap_rst_n,
  input wire ap_start,
  output wire ap_done,
  output wire ap_idle,
  output wire ap_ready,

  input wire all_sleep,
  input wire all_sync_sleep,
  input wire all_waited,

  output wire sleep,
  output wire sync_sleep,
  output wire waited,

  input wire [1:0] actor_return,
  input wire actor_done,
  input wire actor_ready,
  input wire actor_idle,
  output wire actor_start
);

  timeunit 1ps;
  timeprecision 1ps;

  State pstate, nstate;
  logic last_waited;

  // set state
  always_ff @(posedge ap_clk) begin
    if (ap_rst_n == 1'b0)
      pstate <= IDLE_STATE;
    else
      pstate <= nstate;
  end

  // set the last waited registers
  always_ff @(posedge ap_clk) begin
    if (ap_rst_n == 1'b0) begin
      last_waited <= 1'b0;
    end
    else begin
        if (actor_done == 1'b1 && actor_return[1:0] == WAIT)
            last_waited <= 1'b1;
        else if (actor_done == 1'b1 && actor_return[1:0] != WAIT)
            last_waited <= 1'b0;
        else
            last_waited <= last_waited;
    end
  end


  always_comb begin
    case (pstate)
      IDLE_STATE: begin
    
        if (ap_start == 1'b1)
          nstate = LAUNCH;
        else
          nstate = IDLE_STATE;

      end
      LAUNCH: begin
        if (actor_done == 1'b1) begin
          if (actor_return != WAIT || all_waited == 1'b0)
            nstate = LAUNCH;
          else
            nstate = SLEEP;
        end else
          nstate = LAUNCH;
      end
      FLUSH: begin
        // UNUSED STATE
        nstate = IDLE_STATE;
      end
      SLEEP: begin
    
        if (all_sleep == 1'b1)
          nstate = SYNC_LAUNCH;
        else if (all_waited == 1'b0)
          nstate = LAUNCH;
        else
          nstate = SLEEP;
      end
      SYNC_LAUNCH: begin
    
        if (actor_done == 1'b1)
          nstate = SYNC_SLEEP;
        else
          nstate = SYNC_LAUNCH;
      end
      SYNC_FLUSH: begin
        // UNUSED STATE
        nstate = IDLE_STATE;
      end
      SYNC_SLEEP: begin
       
        if (all_sync_sleep == 1'b1)
          if (all_waited == 1'b1)
            nstate = IDLE_STATE;
          else
            nstate = LAUNCH;
        else
          nstate = SYNC_SLEEP;
      end
      default: begin
        nstate = IDLE_STATE;
      end
    endcase
  end

  assign actor_start = (pstate == LAUNCH) | (pstate == SYNC_LAUNCH);
  assign waited = last_waited;
  assign ap_idle = (pstate == IDLE_STATE);
  assign sleep = (pstate == SLEEP) | (pstate == IDLE_STATE);
  assign sync_sleep = (pstate == SYNC_SLEEP) | (pstate == IDLE_STATE);
  assign ap_done = (nstate == IDLE_STATE);
  assign ap_ready = (nstate == IDLE_STATE);

endmodule

