//-- Definition of actor class: Source
cal.actor @source ()
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	%l_counter__3 = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_counter__3: !cal.state_ref<i32>, %tmp_1: i32)
	%l_$state = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_2 = arith.constant 0 : i1
	%tmp_3 = arith.extui %tmp_2 : i1 to i32
	cal.set(%l_$state: !cal.state_ref<i32>, %tmp_3: i32)
	// Generation action: transmit
	cal.action "transmit" priority=0 {
		// Guard: Start
		cal.predicate {
			%tmp_4 = cal.get(%l_counter__3: !cal.state_ref<i32>) : i32
			// Evaluate global variable $eval2.
			%tmp_5 = arith.constant 3 : i2
			%tmp_6 = arith.extui %tmp_5 : i2 to i32
			// Evaluate global variable $eval2 done: assigned to tmp_6 above in this context.
			%tmp_7 = arith.cmpi slt, %tmp_4, %tmp_6 : i32
			cal.predicate_result %tmp_7 : i1
		}
		// Guard: End
		// Action Local Variable Decl: Start
		%tmp_8 = cal.get(%l_counter__3: !cal.state_ref<i32>) : i32
		%tmp_9 = arith.constant 100 : i7
		// Evaluate global variable $eval1.
		%tmp_10 = arith.constant 1 : i1
		%tmp_11 = arith.extui %tmp_10 : i1 to i32
		// Evaluate global variable $eval1 done: assigned to tmp_11 above in this context.
		%tmp_12 = arith.extui %tmp_9 : i7 to i32
		%tmp_13 = arith.muli %tmp_12, %tmp_11 : i32
		%tmp_14 = arith.addi %tmp_8, %tmp_13 : i32
		// l_t__4_d1_0 aliased to tmp_14
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		%tmp_15 = arith.constant 1 : i1
		%tmp_16 = arith.extui %tmp_15 : i1 to i32
		%tmp_17 = arith.addi %tmp_14, %tmp_16 : i32
		// l_t__4_d1_1 aliased to tmp_17
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("Tx1: %i\n\00", %tmp_17) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_18 = cal.get(%l_counter__3: !cal.state_ref<i32>) : i32
		%tmp_19 = arith.constant 1 : i1
		%tmp_20 = arith.extui %tmp_19 : i1 to i32
		%tmp_21 = arith.addi %tmp_18, %tmp_20 : i32
		cal.set(%l_counter__3: !cal.state_ref<i32>, %tmp_21: i32)
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_22 = arith.constant 0 : i1
		%tmp_23 = arith.extui %tmp_22 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_23: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_17: i32)
		// Output Expression: End
	}
}

//-- Definition of actor class: StateAndIf
cal.actor @DUT ()
	ports_in(%In: !fifo.output_port<i32>)
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	%l_b__5 = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_b__5: !cal.state_ref<i32>, %tmp_1: i32)
	%l_$state = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_2 = arith.constant 0 : i1
	%tmp_3 = arith.extui %tmp_2 : i1 to i32
	cal.set(%l_$state: !cal.state_ref<i32>, %tmp_3: i32)
	// Generation action: $untagged0
	cal.action "$untagged0" priority=0 {
		// Input Pattern: Start
		%l_t__7_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Action Local Variable Decl: Start
		%tmp_4 = arith.constant 0 : i1
		%tmp_5 = arith.extui %tmp_4 : i1 to i32
		// l_a__9_d1_0 aliased to tmp_5
		// Action Local Variable Decl: End
		// If Statement: Begin
		%tmp_6 = arith.constant 1 : i1
		%tmp_7 = arith.extui %tmp_6 : i1 to i32
		%tmp_8 = arith.cmpi eq, %l_t__7_d1_0, %tmp_7 : i32
		%l_a__9_d1_1 = scf.if %tmp_8 -> (i32) {
			// Assignment Statement: Start
			%tmp_9 = arith.constant 4 : i3
			%tmp_10 = arith.extui %tmp_9 : i3 to i32
			// l_a__9_d2_0 aliased to tmp_10
			// Assignment Statement: End
			// Assignment Statement: Start
			%tmp_11 = cal.get(%l_b__5: !cal.state_ref<i32>) : i32
			%tmp_12 = arith.constant 4 : i3
			%tmp_13 = arith.extui %tmp_12 : i3 to i32
			%tmp_14 = arith.addi %tmp_11, %tmp_13 : i32
			cal.set(%l_b__5: !cal.state_ref<i32>, %tmp_14: i32)
			// Assignment Statement: End
			scf.yield %tmp_10 : i32
		} else {
			// Assignment Statement: Start
			%tmp_15 = arith.constant 2 : i2
			%tmp_16 = arith.extui %tmp_15 : i2 to i32
			// l_a__9_d2_0 aliased to tmp_16
			// Assignment Statement: End
			// Assignment Statement: Start
			%tmp_17 = cal.get(%l_b__5: !cal.state_ref<i32>) : i32
			%tmp_18 = arith.constant 2 : i2
			%tmp_19 = arith.extui %tmp_18 : i2 to i32
			%tmp_20 = arith.addi %tmp_17, %tmp_19 : i32
			cal.set(%l_b__5: !cal.state_ref<i32>, %tmp_20: i32)
			// Assignment Statement: End
			scf.yield %tmp_16 : i32
		}
		// If Statement: End
		// Call Statement: Start
		fifo.print("a: %i\n\00", %l_a__9_d1_1) : (i32)
		// Call Statement: End
		// Call Statement: Start
		%tmp_21 = cal.get(%l_b__5: !cal.state_ref<i32>) : i32
		fifo.print("b: %i\n\00", %tmp_21) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_22 = arith.constant 0 : i1
		%tmp_23 = arith.extui %tmp_22 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_23: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %l_a__9_d1_1: i32)
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
		%l_t__11_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Call Statement: Start
		fifo.print("Rx: %i\n\00", %l_t__11_d1_0) : (i32)
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
	%queue_from_source_Out, %queue_to_DUT_In = fifo.create<i32>(1) : !fifo.input_port<i32>, !fifo.output_port<i32>
	%queue_from_DUT_Out, %queue_to_sink_In = fifo.create<i32>(1) : !fifo.input_port<i32>, !fifo.output_port<i32>

	// -- Instantiate actors (also known as nodes/instances)
	cal.create_instance @source "source" ()
		ports_out(%queue_from_source_Out: !fifo.input_port<i32>)
	cal.create_instance @DUT "DUT" ()
		ports_in(%queue_to_DUT_In: !fifo.output_port<i32>)
		ports_out(%queue_from_DUT_Out: !fifo.input_port<i32>)
	cal.create_instance @sink "sink" ()
		ports_in(%queue_to_sink_In: !fifo.output_port<i32>)

}

