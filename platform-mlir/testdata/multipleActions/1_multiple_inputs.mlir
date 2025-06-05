//-- Definition of actor class: Source1
cal.actor @Source1 ()
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	%l_counter__4 = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_counter__4: !cal.state_ref<i32>, %tmp_1: i32)
	// Generation action: transmit
	cal.action "transmit" priority=0 {
		// Guard: Start
		cal.predicate {
			%tmp_2 = cal.get(%l_counter__4: !cal.state_ref<i32>) : i32
			// Evaluate global variable $eval1.
			%tmp_3 = arith.constant 4 : i3
			%tmp_4 = arith.extui %tmp_3 : i3 to i32
			// Evaluate global variable $eval1 done: assigned to tmp_4 above in this context.
			%tmp_5 = arith.cmpi slt, %tmp_2, %tmp_4 : i32
			cal.predicate_result %tmp_5 : i1
		}
		// Guard: End
		// Action Local Variable Decl: Start
		%tmp_6 = cal.get(%l_counter__4: !cal.state_ref<i32>) : i32
		%tmp_7 = arith.constant 1 : i1
		%tmp_8 = arith.constant 10 : i4
		%tmp_9 = arith.extui %tmp_7 : i1 to i5
		%tmp_10 = arith.extui %tmp_8 : i4 to i5
		%tmp_11 = arith.muli %tmp_9, %tmp_10 : i5
		%tmp_12 = arith.extui %tmp_11 : i5 to i32
		%tmp_13 = arith.addi %tmp_6, %tmp_12 : i32
		// l_t__5_d1_0 aliased to tmp_13
		// Action Local Variable Decl: End
		fifo.print("Src1: %i\n\00", %tmp_13) : (i32)
		// Assignment Statement: Start
		%tmp_14 = cal.get(%l_counter__4: !cal.state_ref<i32>) : i32
		%tmp_15 = arith.constant 1 : i1
		%tmp_16 = arith.extui %tmp_15 : i1 to i32
		%tmp_17 = arith.addi %tmp_14, %tmp_16 : i32
		cal.set(%l_counter__4: !cal.state_ref<i32>, %tmp_17: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_13: i32)
		// Output Expression: End
	}
}

//-- Definition of actor class: Source2
cal.actor @Source2 ()
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	%l_counter__6 = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_counter__6: !cal.state_ref<i32>, %tmp_1: i32)
	// Generation action: transmit
	cal.action "transmit" priority=0 {
		// Guard: Start
		cal.predicate {
			%tmp_2 = cal.get(%l_counter__6: !cal.state_ref<i32>) : i32
			// Evaluate global variable $eval1.
			%tmp_3 = arith.constant 4 : i3
			%tmp_4 = arith.extui %tmp_3 : i3 to i32
			// Evaluate global variable $eval1 done: assigned to tmp_4 above in this context.
			%tmp_5 = arith.cmpi slt, %tmp_2, %tmp_4 : i32
			cal.predicate_result %tmp_5 : i1
		}
		// Guard: End
		// Action Local Variable Decl: Start
		%tmp_6 = cal.get(%l_counter__6: !cal.state_ref<i32>) : i32
		%tmp_7 = arith.constant 2 : i2
		%tmp_8 = arith.constant 10 : i4
		%tmp_9 = arith.extui %tmp_7 : i2 to i6
		%tmp_10 = arith.extui %tmp_8 : i4 to i6
		%tmp_11 = arith.muli %tmp_9, %tmp_10 : i6
		%tmp_12 = arith.extui %tmp_11 : i6 to i32
		%tmp_13 = arith.addi %tmp_6, %tmp_12 : i32
		// l_t__7_d1_0 aliased to tmp_13
		// Action Local Variable Decl: End
		fifo.print("Src2: %i\n\00", %tmp_13) : (i32)
		// Assignment Statement: Start
		%tmp_14 = cal.get(%l_counter__6: !cal.state_ref<i32>) : i32
		%tmp_15 = arith.constant 1 : i1
		%tmp_16 = arith.extui %tmp_15 : i1 to i32
		%tmp_17 = arith.addi %tmp_14, %tmp_16 : i32
		cal.set(%l_counter__6: !cal.state_ref<i32>, %tmp_17: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_13: i32)
		// Output Expression: End
	}
}

//-- Definition of actor class: Source3
cal.actor @Source3 ()
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	%l_counter__8 = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_counter__8: !cal.state_ref<i32>, %tmp_1: i32)
	// Generation action: transmit
	cal.action "transmit" priority=0 {
		// Guard: Start
		cal.predicate {
			%tmp_2 = cal.get(%l_counter__8: !cal.state_ref<i32>) : i32
			// Evaluate global variable $eval1.
			%tmp_3 = arith.constant 4 : i3
			%tmp_4 = arith.extui %tmp_3 : i3 to i32
			// Evaluate global variable $eval1 done: assigned to tmp_4 above in this context.
			%tmp_5 = arith.cmpi slt, %tmp_2, %tmp_4 : i32
			cal.predicate_result %tmp_5 : i1
		}
		// Guard: End
		// Action Local Variable Decl: Start
		%tmp_6 = cal.get(%l_counter__8: !cal.state_ref<i32>) : i32
		%tmp_7 = arith.constant 3 : i2
		%tmp_8 = arith.constant 10 : i4
		%tmp_9 = arith.extui %tmp_7 : i2 to i6
		%tmp_10 = arith.extui %tmp_8 : i4 to i6
		%tmp_11 = arith.muli %tmp_9, %tmp_10 : i6
		%tmp_12 = arith.extui %tmp_11 : i6 to i32
		%tmp_13 = arith.addi %tmp_6, %tmp_12 : i32
		// l_t__9_d1_0 aliased to tmp_13
		// Action Local Variable Decl: End
		fifo.print("Src3: %i\n\00", %tmp_13) : (i32)
		// Assignment Statement: Start
		%tmp_14 = cal.get(%l_counter__8: !cal.state_ref<i32>) : i32
		%tmp_15 = arith.constant 1 : i1
		%tmp_16 = arith.extui %tmp_15 : i1 to i32
		%tmp_17 = arith.addi %tmp_14, %tmp_16 : i32
		cal.set(%l_counter__8: !cal.state_ref<i32>, %tmp_17: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_13: i32)
		// Output Expression: End
	}
}

//-- Definition of actor class: Merge
cal.actor @Merge ()
	ports_in(%In1: !fifo.output_port<i32>,%In2: !fifo.output_port<i32>,%In3: !fifo.output_port<i32>)
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	// Generation action: $untagged0
	cal.action "$untagged0" priority=0 {
		// Input Pattern: Start
		%l_t__11_d1_0 = fifo.pop(%In1: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %l_t__11_d1_0: i32)
		// Output Expression: End
	}
	// Generation action: $untagged1
	cal.action "$untagged1" priority=0 {
		// Input Pattern: Start
		%l_t__14_d1_0 = fifo.pop(%In2: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %l_t__14_d1_0: i32)
		// Output Expression: End
	}
	// Generation action: $untagged2
	cal.action "$untagged2" priority=0 {
		// Input Pattern: Start
		%l_t__17_d1_0 = fifo.pop(%In3: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %l_t__17_d1_0: i32)
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
		%l_t__20_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		fifo.print("Rx: %i\n\00", %l_t__20_d1_0) : (i32)
	}
}

// -- Top Network: Defines structure of actor application
cal.network
{

	// -- Instantiate channels between actors
	%queue_from_source2_Out, %queue_to_merge_In2 = fifo.create<i32>(1) : !fifo.input_port<i32>, !fifo.output_port<i32>
	%queue_from_merge_Out, %queue_to_sink_In = fifo.create<i32>(1) : !fifo.input_port<i32>, !fifo.output_port<i32>
	%queue_from_source1_Out, %queue_to_merge_In1 = fifo.create<i32>(1) : !fifo.input_port<i32>, !fifo.output_port<i32>
	%queue_from_source3_Out, %queue_to_merge_In3 = fifo.create<i32>(1) : !fifo.input_port<i32>, !fifo.output_port<i32>

	// -- Instantiate actors (also known as nodes/instances)
	cal.create_instance @Source1 "source1" ()
		ports_out(%queue_from_source1_Out: !fifo.input_port<i32>)
	cal.create_instance @Source2 "source2" ()
		ports_out(%queue_from_source2_Out: !fifo.input_port<i32>)
	cal.create_instance @Source3 "source3" ()
		ports_out(%queue_from_source3_Out: !fifo.input_port<i32>)
	cal.create_instance @Merge "merge" ()
		ports_in(%queue_to_merge_In1, %queue_to_merge_In2, %queue_to_merge_In3: !fifo.output_port<i32>, !fifo.output_port<i32>, !fifo.output_port<i32>)
		ports_out(%queue_from_merge_Out: !fifo.input_port<i32>)
	cal.create_instance @Sink "sink" ()
		ports_in(%queue_to_sink_In: !fifo.output_port<i32>)

}

