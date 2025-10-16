//-- Definition of actor class: Source
cal.actor @source1 ()
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	%l_counter__7 = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_counter__7: !cal.state_ref<i32>, %tmp_1: i32)
	%l_$state = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_2 = arith.constant 0 : i1
	%tmp_3 = arith.extui %tmp_2 : i1 to i32
	cal.set(%l_$state: !cal.state_ref<i32>, %tmp_3: i32)
	// Generation action: transmit
	cal.action "transmit" priority=0 {
		// Guard: Start
		cal.predicate {
			%tmp_4 = cal.get(%l_counter__7: !cal.state_ref<i32>) : i32
			// Evaluate global variable $eval2.
			%tmp_5 = arith.constant 4 : i3
			%tmp_6 = arith.extui %tmp_5 : i3 to i32
			// Evaluate global variable $eval2 done: assigned to tmp_6 above in this context.
			%tmp_7 = arith.cmpi slt, %tmp_4, %tmp_6 : i32
			cal.predicate_result %tmp_7 : i1
		}
		// Guard: End
		// Action Local Variable Decl: Start
		%tmp_8 = cal.get(%l_counter__7: !cal.state_ref<i32>) : i32
		%tmp_9 = arith.constant 100 : i7
		// Evaluate global variable $eval1.
		%tmp_10 = arith.constant 1 : i1
		%tmp_11 = arith.extui %tmp_10 : i1 to i32
		// Evaluate global variable $eval1 done: assigned to tmp_11 above in this context.
		%tmp_12 = arith.extui %tmp_9 : i7 to i32
		%tmp_13 = arith.muli %tmp_12, %tmp_11 : i32
		%tmp_14 = arith.addi %tmp_8, %tmp_13 : i32
		// l_t__8_d1_0 aliased to tmp_14
		// Action Local Variable Decl: End
		// Call Statement: Start
		fifo.print("Src: 1 %i\n\00", %tmp_14) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_15 = cal.get(%l_counter__7: !cal.state_ref<i32>) : i32
		%tmp_16 = arith.constant 1 : i1
		%tmp_17 = arith.extui %tmp_16 : i1 to i32
		%tmp_18 = arith.addi %tmp_15, %tmp_17 : i32
		cal.set(%l_counter__7: !cal.state_ref<i32>, %tmp_18: i32)
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_19 = arith.constant 0 : i1
		%tmp_20 = arith.extui %tmp_19 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_20: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_14: i32)
		// Output Expression: End
	}
}

//-- Definition of actor class: Source
cal.actor @source2 ()
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	%l_counter__9 = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_counter__9: !cal.state_ref<i32>, %tmp_1: i32)
	%l_$state = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_2 = arith.constant 0 : i1
	%tmp_3 = arith.extui %tmp_2 : i1 to i32
	cal.set(%l_$state: !cal.state_ref<i32>, %tmp_3: i32)
	// Generation action: transmit
	cal.action "transmit" priority=0 {
		// Guard: Start
		cal.predicate {
			%tmp_4 = cal.get(%l_counter__9: !cal.state_ref<i32>) : i32
			// Evaluate global variable $eval2.
			%tmp_5 = arith.constant 4 : i3
			%tmp_6 = arith.extui %tmp_5 : i3 to i32
			// Evaluate global variable $eval2 done: assigned to tmp_6 above in this context.
			%tmp_7 = arith.cmpi slt, %tmp_4, %tmp_6 : i32
			cal.predicate_result %tmp_7 : i1
		}
		// Guard: End
		// Action Local Variable Decl: Start
		%tmp_8 = cal.get(%l_counter__9: !cal.state_ref<i32>) : i32
		%tmp_9 = arith.constant 100 : i7
		// Evaluate global variable $eval4.
		%tmp_10 = arith.constant 2 : i2
		%tmp_11 = arith.extui %tmp_10 : i2 to i32
		// Evaluate global variable $eval4 done: assigned to tmp_11 above in this context.
		%tmp_12 = arith.extui %tmp_9 : i7 to i32
		%tmp_13 = arith.muli %tmp_12, %tmp_11 : i32
		%tmp_14 = arith.addi %tmp_8, %tmp_13 : i32
		// l_t__10_d1_0 aliased to tmp_14
		// Action Local Variable Decl: End
		// Call Statement: Start
		fifo.print("Src: 2 %i\n\00", %tmp_14) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_15 = cal.get(%l_counter__9: !cal.state_ref<i32>) : i32
		%tmp_16 = arith.constant 1 : i1
		%tmp_17 = arith.extui %tmp_16 : i1 to i32
		%tmp_18 = arith.addi %tmp_15, %tmp_17 : i32
		cal.set(%l_counter__9: !cal.state_ref<i32>, %tmp_18: i32)
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_19 = arith.constant 0 : i1
		%tmp_20 = arith.extui %tmp_19 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_20: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_14: i32)
		// Output Expression: End
	}
}

//-- Definition of actor class: Source
cal.actor @source3 ()
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	%l_counter__11 = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_counter__11: !cal.state_ref<i32>, %tmp_1: i32)
	%l_$state = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_2 = arith.constant 0 : i1
	%tmp_3 = arith.extui %tmp_2 : i1 to i32
	cal.set(%l_$state: !cal.state_ref<i32>, %tmp_3: i32)
	// Generation action: transmit
	cal.action "transmit" priority=0 {
		// Guard: Start
		cal.predicate {
			%tmp_4 = cal.get(%l_counter__11: !cal.state_ref<i32>) : i32
			// Evaluate global variable $eval2.
			%tmp_5 = arith.constant 4 : i3
			%tmp_6 = arith.extui %tmp_5 : i3 to i32
			// Evaluate global variable $eval2 done: assigned to tmp_6 above in this context.
			%tmp_7 = arith.cmpi slt, %tmp_4, %tmp_6 : i32
			cal.predicate_result %tmp_7 : i1
		}
		// Guard: End
		// Action Local Variable Decl: Start
		%tmp_8 = cal.get(%l_counter__11: !cal.state_ref<i32>) : i32
		%tmp_9 = arith.constant 100 : i7
		// Evaluate global variable $eval6.
		%tmp_10 = arith.constant 3 : i2
		%tmp_11 = arith.extui %tmp_10 : i2 to i32
		// Evaluate global variable $eval6 done: assigned to tmp_11 above in this context.
		%tmp_12 = arith.extui %tmp_9 : i7 to i32
		%tmp_13 = arith.muli %tmp_12, %tmp_11 : i32
		%tmp_14 = arith.addi %tmp_8, %tmp_13 : i32
		// l_t__12_d1_0 aliased to tmp_14
		// Action Local Variable Decl: End
		// Call Statement: Start
		fifo.print("Src: 3 %i\n\00", %tmp_14) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_15 = cal.get(%l_counter__11: !cal.state_ref<i32>) : i32
		%tmp_16 = arith.constant 1 : i1
		%tmp_17 = arith.extui %tmp_16 : i1 to i32
		%tmp_18 = arith.addi %tmp_15, %tmp_17 : i32
		cal.set(%l_counter__11: !cal.state_ref<i32>, %tmp_18: i32)
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_19 = arith.constant 0 : i1
		%tmp_20 = arith.extui %tmp_19 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_20: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_14: i32)
		// Output Expression: End
	}
}

//-- Definition of actor class: Merge
cal.actor @merge ()
	ports_in(%In1: !fifo.output_port<i32>,%In2: !fifo.output_port<i32>,%In3: !fifo.output_port<i32>)
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	%l_$state = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_$state: !cal.state_ref<i32>, %tmp_1: i32)
	// Generation action: $untagged0
	cal.action "$untagged0" priority=0 {
		// Input Pattern: Start
		%l_t__14_d1_0 = fifo.pop(%In1: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Assignment Statement: Start
		%tmp_2 = arith.constant 0 : i1
		%tmp_3 = arith.extui %tmp_2 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_3: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %l_t__14_d1_0: i32)
		// Output Expression: End
	}
	// Generation action: $untagged1
	cal.action "$untagged1" priority=0 {
		// Input Pattern: Start
		%l_t__17_d1_0 = fifo.pop(%In2: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Assignment Statement: Start
		%tmp_4 = arith.constant 0 : i1
		%tmp_5 = arith.extui %tmp_4 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_5: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %l_t__17_d1_0: i32)
		// Output Expression: End
	}
	// Generation action: $untagged2
	cal.action "$untagged2" priority=0 {
		// Input Pattern: Start
		%l_t__20_d1_0 = fifo.pop(%In3: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Assignment Statement: Start
		%tmp_6 = arith.constant 0 : i1
		%tmp_7 = arith.extui %tmp_6 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_7: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %l_t__20_d1_0: i32)
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
		%l_t__23_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Call Statement: Start
		fifo.print("Rx: %i\n\00", %l_t__23_d1_0) : (i32)
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
	%queue_from_source2_Out, %queue_to_merge_In2 = fifo.create<i32>(1) : !fifo.input_port<i32>, !fifo.output_port<i32>
	%queue_from_merge_Out, %queue_to_sink_In = fifo.create<i32>(1) : !fifo.input_port<i32>, !fifo.output_port<i32>
	%queue_from_source1_Out, %queue_to_merge_In1 = fifo.create<i32>(1) : !fifo.input_port<i32>, !fifo.output_port<i32>
	%queue_from_source3_Out, %queue_to_merge_In3 = fifo.create<i32>(1) : !fifo.input_port<i32>, !fifo.output_port<i32>

	// -- Instantiate actors (also known as nodes/instances)
	cal.create_instance @source1 "source1" ()
		ports_out(%queue_from_source1_Out: !fifo.input_port<i32>)
	cal.create_instance @source2 "source2" ()
		ports_out(%queue_from_source2_Out: !fifo.input_port<i32>)
	cal.create_instance @source3 "source3" ()
		ports_out(%queue_from_source3_Out: !fifo.input_port<i32>)
	cal.create_instance @merge "merge" ()
		ports_in(%queue_to_merge_In1, %queue_to_merge_In2, %queue_to_merge_In3: !fifo.output_port<i32>, !fifo.output_port<i32>, !fifo.output_port<i32>)
		ports_out(%queue_from_merge_Out: !fifo.input_port<i32>)
	cal.create_instance @sink "sink" ()
		ports_in(%queue_to_sink_In: !fifo.output_port<i32>)

}

