//-- Definition of actor class: Source
cal.actor @source ()
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	%l_counter__17 = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_counter__17: !cal.state_ref<i32>, %tmp_1: i32)
	%l_$state = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_2 = arith.constant 0 : i1
	%tmp_3 = arith.extui %tmp_2 : i1 to i32
	cal.set(%l_$state: !cal.state_ref<i32>, %tmp_3: i32)
	// Generation action: transmit
	cal.action "transmit" priority=0 {
		// Guard: Start
		cal.predicate {
			%tmp_4 = cal.get(%l_counter__17: !cal.state_ref<i32>) : i32
			// Evaluate global variable $eval1.
			%tmp_5 = arith.constant 2 : i2
			%tmp_6 = arith.extui %tmp_5 : i2 to i32
			// Evaluate global variable $eval1 done: assigned to tmp_6 above in this context.
			%tmp_7 = arith.cmpi slt, %tmp_4, %tmp_6 : i32
			cal.predicate_result %tmp_7 : i1
		}
		// Guard: End
		// Action Local Variable Decl: Start
		%tmp_8 = cal.get(%l_counter__17: !cal.state_ref<i32>) : i32
		// l_t__18_d1_0 aliased to tmp_8
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		%tmp_9 = arith.constant 1 : i1
		%tmp_10 = arith.extui %tmp_9 : i1 to i32
		%tmp_11 = arith.addi %tmp_8, %tmp_10 : i32
		// l_t__18_d1_1 aliased to tmp_11
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("Tx: %i\n\00", %tmp_11) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_12 = cal.get(%l_counter__17: !cal.state_ref<i32>) : i32
		%tmp_13 = arith.constant 1 : i1
		%tmp_14 = arith.extui %tmp_13 : i1 to i32
		%tmp_15 = arith.addi %tmp_12, %tmp_14 : i32
		cal.set(%l_counter__17: !cal.state_ref<i32>, %tmp_15: i32)
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_16 = arith.constant 0 : i1
		%tmp_17 = arith.extui %tmp_16 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_17: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_11: i32)
		// Output Expression: End
	}
}

func.func @DUT1_l_processRxItem__7(%l_temp__5: !cal.state_ref<i32>, %l_temp1__6: !cal.state_ref<i32>, %l_$state: !cal.state_ref<i32>, %l_dataItem_d0_0: i32) {
	// Block Statement: Begin
	//     Variable declarations attached to block statement: Begin
	%tmp_0 = arith.constant 1 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	// l_tempOut_d1_0 aliased to tmp_1
	//     Variable declarations attached to block statement: End
	// Assignment Statement: Start
	%tmp_2 = cal.get(%l_temp__5: !cal.state_ref<i32>) : i32
	%tmp_3 = arith.addi %tmp_2, %tmp_1 : i32
	%tmp_4 = arith.addi %tmp_3, %l_dataItem_d0_0 : i32
	// Evaluate global variable $eval3.
	%tmp_5 = arith.constant 1 : i1
	%tmp_6 = arith.extui %tmp_5 : i1 to i32
	// Evaluate global variable $eval3 done: assigned to tmp_6 above in this context.
	%tmp_7 = arith.addi %tmp_4, %tmp_6 : i32
	cal.set(%l_temp__5: !cal.state_ref<i32>, %tmp_7: i32)
	// Assignment Statement: End
	// Call Statement: Start
	fifo.print("Here!\n\00")
	// Call Statement: End
	//     dfg.push operations deferred from StmtWrites: Begin
	//     dfg.push operations deferred from StmtWrites: End
	// Block Statement: End
	func.return
}

//-- Definition of actor class: ProcedureCaller
cal.actor @DUT1 ()
	ports_in(%In: !fifo.output_port<i32>)
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	%l_temp__5 = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_temp__5: !cal.state_ref<i32>, %tmp_1: i32)
	%l_temp1__6 = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_2 = arith.constant 10 : i4
	%tmp_3 = arith.extui %tmp_2 : i4 to i32
	cal.set(%l_temp1__6: !cal.state_ref<i32>, %tmp_3: i32)
	%l_$state = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_4 = arith.constant 0 : i1
	%tmp_5 = arith.extui %tmp_4 : i1 to i32
	cal.set(%l_$state: !cal.state_ref<i32>, %tmp_5: i32)
	// Generation action: $untagged0
	cal.action "$untagged0" priority=0 {
		// Input Pattern: Start
		%l_t__9_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Call Statement: Start
		%tmp_6 = cal.get(%l_temp__5: !cal.state_ref<i32>) : i32
		fifo.print("TempIn: %i %i\n\00", %tmp_6, %l_t__9_d1_0) : (i32, i32)
		// Call Statement: End
		// Call Statement: Start
		%tmp_7 = cal.get(%l_temp1__6: !cal.state_ref<i32>) : i32
		func.call @DUT1_l_processRxItem__7(%l_temp__5, %l_temp1__6, %l_$state, %tmp_7) : (!cal.state_ref<i32>, !cal.state_ref<i32>, !cal.state_ref<i32>, i32) -> ()
		// Call Statement: End
		// Call Statement: Start
		%tmp_8 = cal.get(%l_temp__5: !cal.state_ref<i32>) : i32
		fifo.print("TempOut: %i %i\n\00", %tmp_8, %l_t__9_d1_0) : (i32, i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_9 = arith.constant 0 : i1
		%tmp_10 = arith.extui %tmp_9 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_10: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %l_t__9_d1_0: i32)
		// Output Expression: End
	}
}

func.func @DUT2_l_processRxItem__13(%l_temp__11: !cal.state_ref<i32>, %l_temp1__12: !cal.state_ref<i32>, %l_$state: !cal.state_ref<i32>, %l_dataItem_d0_0: i32) {
	// Block Statement: Begin
	//     Variable declarations attached to block statement: Begin
	%tmp_0 = arith.constant 1 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	// l_tempOut_d1_0 aliased to tmp_1
	//     Variable declarations attached to block statement: End
	// Assignment Statement: Start
	%tmp_2 = cal.get(%l_temp__11: !cal.state_ref<i32>) : i32
	%tmp_3 = arith.addi %tmp_2, %tmp_1 : i32
	%tmp_4 = arith.addi %tmp_3, %l_dataItem_d0_0 : i32
	// Evaluate global variable $eval1.
	%tmp_5 = arith.constant 2 : i2
	%tmp_6 = arith.extui %tmp_5 : i2 to i32
	// Evaluate global variable $eval1 done: assigned to tmp_6 above in this context.
	%tmp_7 = arith.addi %tmp_4, %tmp_6 : i32
	cal.set(%l_temp__11: !cal.state_ref<i32>, %tmp_7: i32)
	// Assignment Statement: End
	// Call Statement: Start
	fifo.print("Here!\n\00")
	// Call Statement: End
	//     dfg.push operations deferred from StmtWrites: Begin
	//     dfg.push operations deferred from StmtWrites: End
	// Block Statement: End
	func.return
}

//-- Definition of actor class: ProcedureCaller
cal.actor @DUT2 ()
	ports_in(%In: !fifo.output_port<i32>)
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	%l_temp__11 = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_temp__11: !cal.state_ref<i32>, %tmp_1: i32)
	%l_temp1__12 = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_2 = arith.constant 10 : i4
	%tmp_3 = arith.extui %tmp_2 : i4 to i32
	cal.set(%l_temp1__12: !cal.state_ref<i32>, %tmp_3: i32)
	%l_$state = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_4 = arith.constant 0 : i1
	%tmp_5 = arith.extui %tmp_4 : i1 to i32
	cal.set(%l_$state: !cal.state_ref<i32>, %tmp_5: i32)
	// Generation action: $untagged0
	cal.action "$untagged0" priority=0 {
		// Input Pattern: Start
		%l_t__15_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Call Statement: Start
		%tmp_6 = cal.get(%l_temp__11: !cal.state_ref<i32>) : i32
		fifo.print("TempIn: %i %i\n\00", %tmp_6, %l_t__15_d1_0) : (i32, i32)
		// Call Statement: End
		// Call Statement: Start
		%tmp_7 = cal.get(%l_temp1__12: !cal.state_ref<i32>) : i32
		func.call @DUT2_l_processRxItem__13(%l_temp__11, %l_temp1__12, %l_$state, %tmp_7) : (!cal.state_ref<i32>, !cal.state_ref<i32>, !cal.state_ref<i32>, i32) -> ()
		// Call Statement: End
		// Call Statement: Start
		%tmp_8 = cal.get(%l_temp__11: !cal.state_ref<i32>) : i32
		fifo.print("TempOut: %i %i\n\00", %tmp_8, %l_t__15_d1_0) : (i32, i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_9 = arith.constant 0 : i1
		%tmp_10 = arith.extui %tmp_9 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_10: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %l_t__15_d1_0: i32)
		// Output Expression: End
	}
}

//-- Definition of actor class: Sink
cal.actor @sink ()
	ports_in(%In: !fifo.output_port<i32>)
{
	// -- Actor body
	%l_$state = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_$state: !cal.state_ref<i32>, %tmp_1: i32)
	// Generation action: $untagged0
	cal.action "$untagged0" priority=0 {
		// Input Pattern: Start
		%l_t__20_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Call Statement: Start
		fifo.print("Rx: %i\n\00", %l_t__20_d1_0) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_2 = arith.constant 0 : i1
		%tmp_3 = arith.extui %tmp_2 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_3: i32)
		// Assignment Statement: End
	}
}

// -- Top Network: Defines structure of actor application
cal.network
{

	// -- Instantiate channels between actors
	%queue_from_DUT1_Out, %queue_to_DUT2_In = fifo.create<i32>(1) : !fifo.input_port<i32>, !fifo.output_port<i32>
	%queue_from_DUT2_Out, %queue_to_sink_In = fifo.create<i32>(1) : !fifo.input_port<i32>, !fifo.output_port<i32>
	%queue_from_source_Out, %queue_to_DUT1_In = fifo.create<i32>(1) : !fifo.input_port<i32>, !fifo.output_port<i32>

	// -- Instantiate actors (also known as nodes/instances)
	cal.create_instance @source "source" ()
		ports_out(%queue_from_source_Out: !fifo.input_port<i32>)
	cal.create_instance @DUT1 "DUT1" ()
		ports_in(%queue_to_DUT1_In: !fifo.output_port<i32>)
		ports_out(%queue_from_DUT1_Out: !fifo.input_port<i32>)
	cal.create_instance @DUT2 "DUT2" ()
		ports_in(%queue_to_DUT2_In: !fifo.output_port<i32>)
		ports_out(%queue_from_DUT2_Out: !fifo.input_port<i32>)
	cal.create_instance @sink "sink" ()
		ports_in(%queue_to_sink_In: !fifo.output_port<i32>)

}

