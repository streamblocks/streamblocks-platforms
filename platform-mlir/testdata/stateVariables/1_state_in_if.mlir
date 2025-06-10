//-- Definition of actor class: Source
cal.actor @source ()
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	%l_counter__3 = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_counter__3: !cal.state_ref<i32>, %tmp_1: i32)
	// Generation action: transmit
	cal.action "transmit" priority=0 {
		// Guard: Start
		cal.predicate {
			%tmp_2 = cal.get(%l_counter__3: !cal.state_ref<i32>) : i32
			// Evaluate global variable $eval2.
			%tmp_3 = arith.constant 3 : i2
			%tmp_4 = arith.extui %tmp_3 : i2 to i32
			// Evaluate global variable $eval2 done: assigned to tmp_4 above in this context.
			%tmp_5 = arith.cmpi slt, %tmp_2, %tmp_4 : i32
			cal.predicate_result %tmp_5 : i1
		}
		// Guard: End
		// Action Local Variable Decl: Start
		%tmp_6 = cal.get(%l_counter__3: !cal.state_ref<i32>) : i32
		%tmp_7 = arith.constant 100 : i7
		// Evaluate global variable $eval1.
		%tmp_8 = arith.constant 1 : i1
		%tmp_9 = arith.extui %tmp_8 : i1 to i32
		// Evaluate global variable $eval1 done: assigned to tmp_9 above in this context.
		%tmp_10 = arith.extui %tmp_7 : i7 to i32
		%tmp_11 = arith.muli %tmp_10, %tmp_9 : i32
		%tmp_12 = arith.addi %tmp_6, %tmp_11 : i32
		// l_t__4_d1_0 aliased to tmp_12
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		%tmp_13 = arith.constant 1 : i1
		%tmp_14 = arith.extui %tmp_13 : i1 to i32
		%tmp_15 = arith.addi %tmp_12, %tmp_14 : i32
		// l_t__4_d1_1 aliased to tmp_15
		// Assignment Statement: End
		fifo.print("Tx1: %i\n\00", %tmp_15) : (i32)
		// Assignment Statement: Start
		%tmp_16 = cal.get(%l_counter__3: !cal.state_ref<i32>) : i32
		%tmp_17 = arith.constant 1 : i1
		%tmp_18 = arith.extui %tmp_17 : i1 to i32
		%tmp_19 = arith.addi %tmp_16, %tmp_18 : i32
		cal.set(%l_counter__3: !cal.state_ref<i32>, %tmp_19: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_15: i32)
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
	// Generation action: $untagged0
	cal.action "$untagged0" priority=0 {
		// Input Pattern: Start
		%l_t__7_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Action Local Variable Decl: Start
		%tmp_2 = arith.constant 0 : i1
		%tmp_3 = arith.extui %tmp_2 : i1 to i32
		// l_a__9_d1_0 aliased to tmp_3
		// Action Local Variable Decl: End
		// If Statement: Begin
		%tmp_4 = arith.constant 1 : i1
		%tmp_5 = arith.extui %tmp_4 : i1 to i32
		%tmp_6 = arith.cmpi eq, %l_t__7_d1_0, %tmp_5 : i32
		%l_a__9_d1_1 = scf.if %tmp_6 -> (i32) {
			// Assignment Statement: Start
			%tmp_7 = arith.constant 4 : i3
			%tmp_8 = arith.extui %tmp_7 : i3 to i32
			// l_a__9_d2_0 aliased to tmp_8
			// Assignment Statement: End
			// Assignment Statement: Start
			%tmp_9 = cal.get(%l_b__5: !cal.state_ref<i32>) : i32
			%tmp_10 = arith.constant 4 : i3
			%tmp_11 = arith.extui %tmp_10 : i3 to i32
			%tmp_12 = arith.addi %tmp_9, %tmp_11 : i32
			cal.set(%l_b__5: !cal.state_ref<i32>, %tmp_12: i32)
			// Assignment Statement: End
			scf.yield %tmp_8 : i32
		} else {
			// Assignment Statement: Start
			%tmp_13 = arith.constant 2 : i2
			%tmp_14 = arith.extui %tmp_13 : i2 to i32
			// l_a__9_d2_0 aliased to tmp_14
			// Assignment Statement: End
			// Assignment Statement: Start
			%tmp_15 = cal.get(%l_b__5: !cal.state_ref<i32>) : i32
			%tmp_16 = arith.constant 2 : i2
			%tmp_17 = arith.extui %tmp_16 : i2 to i32
			%tmp_18 = arith.addi %tmp_15, %tmp_17 : i32
			cal.set(%l_b__5: !cal.state_ref<i32>, %tmp_18: i32)
			// Assignment Statement: End
			scf.yield %tmp_14 : i32
		}
		// If Statement: End
		fifo.print("a: %i\n\00", %l_a__9_d1_1) : (i32)
		%tmp_19 = cal.get(%l_b__5: !cal.state_ref<i32>) : i32
		fifo.print("b: %i\n\00", %tmp_19) : (i32)
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
	// Generation action: $untagged0
	cal.action "$untagged0" priority=0 {
		// Input Pattern: Start
		%l_t__11_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		fifo.print("Rx: %i\n\00", %l_t__11_d1_0) : (i32)
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

