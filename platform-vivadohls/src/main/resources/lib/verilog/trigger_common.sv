`ifndef __TRIGGER_COMMON_SV__
`define __TRIGGER_COMMON_SV__

package TriggerCommon;

  typedef enum logic[1:0] {
    IDLE, WAIT, TEST, EXECUTED
  } ReturnStatus;
  typedef enum logic[2:0] {
    IDLE_STATE, LAUNCH, FLUSH, SLEEP, SYNC_LAUNCH, SYNC_FLUSH, SYNC_SLEEP
  } State;

endpackage
`endif