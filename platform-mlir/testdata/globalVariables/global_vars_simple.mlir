//-- Definition of actor class: Source
cal.actor @Source ()
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	%l_counter__2 = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_counter__2: !cal.state_ref<i32>, %tmp_1: i32)
	// Generation action: transmit
	cal.action "transmit" priority=0 {
		// Guard: Start
		cal.predicate {
			%tmp_2 = cal.get(%l_counter__2: !cal.state_ref<i32>) : i32
			// Evaluate global variable $eval1.
			%tmp_3 = arith.constant 1 : i1
			%tmp_4 = arith.extui %tmp_3 : i1 to i32
			// Evaluate global variable $eval1 done: assigned to tmp_4 above in this context.
			%tmp_5 = arith.cmpi slt, %tmp_2, %tmp_4 : i32
			cal.predicate_result %tmp_5 : i1
		}
		// Guard: End
		// Action Local Variable Decl: Start
		%tmp_6 = cal.get(%l_counter__2: !cal.state_ref<i32>) : i32
		// l_t__3_d1_0 aliased to tmp_6
		// Action Local Variable Decl: End
		fifo.print("Tx: %i\n\00", %tmp_6) : (i32)
		// Assignment Statement: Start
		%tmp_7 = cal.get(%l_counter__2: !cal.state_ref<i32>) : i32
		%tmp_8 = arith.constant 1 : i1
		%tmp_9 = arith.extui %tmp_8 : i1 to i32
		%tmp_10 = arith.addi %tmp_7, %tmp_9 : i32
		cal.set(%l_counter__2: !cal.state_ref<i32>, %tmp_10: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_6: i32)
		// Output Expression: End
	}
}

//-- Definition of actor class: GlobalVariables
cal.actor @GlobalVariables ()
	ports_in(%In: !fifo.output_port<i32>)
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	// Generation action: $untagged0
	cal.action "$untagged0" priority=0 {
		// Input Pattern: Start
		%l_t__5_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Action Local Variable Decl: Start
		%tmp_0 = arith.constant 0 : i1
		%tmp_1 = arith.extui %tmp_0 : i1 to i32
		// l_x__7_d1_0 aliased to tmp_1
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		// Evaluate global variable P.
		%tmp_2 = arith.constant 1 : i1
		// Evaluate global variable Q.
		%tmp_3 = arith.constant 4 : i3
		%tmp_4 = arith.constant 1 : i1
		%tmp_5 = arith.extui %tmp_3 : i3 to i4
		%tmp_6 = arith.extui %tmp_4 : i1 to i4
		%tmp_7 = arith.addi %tmp_5, %tmp_6 : i4
		%tmp_8 = arith.extui %tmp_7 : i4 to i32
		// Evaluate global variable Q done: assigned to tmp_8 above in this context.
		%tmp_9 = arith.extui %tmp_2 : i1 to i32
		%tmp_10 = arith.addi %tmp_9, %tmp_8 : i32
		// Evaluate global variable P done: assigned to tmp_10 above in this context.
		%tmp_11 = arith.addi %tmp_1, %tmp_10 : i32
		// l_x__7_d1_1 aliased to tmp_11
		// Assignment Statement: End
		// Output Expression: Start
		%tmp_12 = arith.addi %l_t__5_d1_0, %tmp_11 : i32
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_12: i32)
		// Output Expression: End
	}
}

//-- Definition of actor class: Sink
cal.actor @Sink ()
	ports_in(%In: !fifo.output_port<i32>)
{
	// -- Actor body
	// Generation action: $untagged0
	cal.action "$untagged0" priority=0 {
		// Input Pattern: Start
		%l_t__9_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		fifo.print("Rx: %i\n\00", %l_t__9_d1_0) : (i32)
	}
}

// -- Top Network: Defines structure of actor application
cal.network
{

	// -- Instantiate channels between actors
	%queue_from_pass_Out, %queue_to_sink_In = fifo.create<i32>(3) : !fifo.input_port<i32>, !fifo.output_port<i32>
	%queue_from_source_Out, %queue_to_pass_In = fifo.create<i32>(3) : !fifo.input_port<i32>, !fifo.output_port<i32>

	// -- Instantiate actors (also known as nodes/instances)
	cal.create_instance @Source "source" ()
		ports_out(%queue_from_source_Out: !fifo.input_port<i32>)
	cal.create_instance @GlobalVariables "pass" ()
		ports_in(%queue_to_pass_In: !fifo.output_port<i32>)
		ports_out(%queue_from_pass_Out: !fifo.input_port<i32>)
	cal.create_instance @Sink "sink" ()
		ports_in(%queue_to_sink_In: !fifo.output_port<i32>)

}

