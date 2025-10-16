//-- Definition of actor class: Source
cal.actor @source ()
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	%l_counter__2 = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_counter__2: !cal.state_ref<i32>, %tmp_1: i32)
	%l_$state = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_2 = arith.constant 0 : i1
	%tmp_3 = arith.extui %tmp_2 : i1 to i32
	cal.set(%l_$state: !cal.state_ref<i32>, %tmp_3: i32)
	// Generation action: transmit
	cal.action "transmit" priority=0 {
		// Guard: Start
		cal.predicate {
			%tmp_4 = cal.get(%l_counter__2: !cal.state_ref<i32>) : i32
			// Evaluate global variable $eval1.
			%tmp_5 = arith.constant 2 : i2
			%tmp_6 = arith.extui %tmp_5 : i2 to i32
			// Evaluate global variable $eval1 done: assigned to tmp_6 above in this context.
			%tmp_7 = arith.cmpi slt, %tmp_4, %tmp_6 : i32
			cal.predicate_result %tmp_7 : i1
		}
		// Guard: End
		// Action Local Variable Decl: Start
		%tmp_8 = cal.get(%l_counter__2: !cal.state_ref<i32>) : i32
		// l_t__3_d1_0 aliased to tmp_8
		// Action Local Variable Decl: End
		// Call Statement: Start
		fifo.print("Tx: %i\n\00", %tmp_8) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_9 = cal.get(%l_counter__2: !cal.state_ref<i32>) : i32
		%tmp_10 = arith.constant 1 : i1
		%tmp_11 = arith.extui %tmp_10 : i1 to i32
		%tmp_12 = arith.addi %tmp_9, %tmp_11 : i32
		cal.set(%l_counter__2: !cal.state_ref<i32>, %tmp_12: i32)
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_13 = arith.constant 0 : i1
		%tmp_14 = arith.extui %tmp_13 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_14: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_8: i32)
		// Output Expression: End
	}
}

//-- Definition of actor class: StateAndForEach
cal.actor @DUT ()
	ports_in(%In: !fifo.output_port<i32>)
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	%l_b__7 = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_b__7: !cal.state_ref<i32>, %tmp_1: i32)
	%l_$state = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_2 = arith.constant 0 : i1
	%tmp_3 = arith.extui %tmp_2 : i1 to i32
	cal.set(%l_$state: !cal.state_ref<i32>, %tmp_3: i32)
	// Generation action: $untagged0
	cal.action "$untagged0" priority=0 {
		// Input Pattern: Start
		%l_t__9_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Action Local Variable Decl: Start
		%tmp_4 = arith.constant 0 : i1
		%tmp_5 = arith.extui %tmp_4 : i1 to i32
		// l_a__11_d1_0 aliased to tmp_5
		// Action Local Variable Decl: End
		// Foreach Statement: Begin
		%tmp_6 = arith.constant 1 : i1
		%tmp_7 = arith.extui %tmp_6 : i1 to i32
		%tmp_8_lb = index.casts %tmp_7 : i32 to index
		%tmp_9 = arith.constant 5 : i3
		%tmp_10 = arith.extui %tmp_9 : i3 to i32
		%tmp_11_ub = index.casts %tmp_10 : i32 to index
		%tmp_12_step = index.constant 1
		%tmp_13_ub_plus_1 = arith.addi %tmp_11_ub, %tmp_12_step : index
		%l_a__11_d1_1 = scf.for %l_j_d1_0 = %tmp_8_lb to %tmp_13_ub_plus_1 step %tmp_12_step
				iter_args(%l_a__11_d2_0 = %tmp_5) -> (i32) {
			%l_j_d2_0 = arith.index_cast %l_j_d1_0 : index to i32
			// Assignment Statement: Start
			%tmp_14 = arith.constant 1 : i1
			%tmp_15 = arith.extui %tmp_14 : i1 to i32
			%tmp_16 = arith.addi %l_a__11_d2_0, %tmp_15 : i32
			// l_a__11_d2_1 aliased to tmp_16
			// Assignment Statement: End
			// Assignment Statement: Start
			%tmp_17 = cal.get(%l_b__7: !cal.state_ref<i32>) : i32
			%tmp_18 = arith.constant 1 : i1
			%tmp_19 = arith.extui %tmp_18 : i1 to i32
			%tmp_20 = arith.addi %tmp_17, %tmp_19 : i32
			cal.set(%l_b__7: !cal.state_ref<i32>, %tmp_20: i32)
			// Assignment Statement: End
			// Call Statement: Start
			fifo.print("a: %i\n\00", %tmp_16) : (i32)
			// Call Statement: End
			// Call Statement: Start
			%tmp_21 = cal.get(%l_b__7: !cal.state_ref<i32>) : i32
			fifo.print("b: %i\n\00", %tmp_21) : (i32)
			// Call Statement: End
			scf.yield %tmp_16 : i32
		}
		// Foreach Statement: End
		// Assignment Statement: Start
		%tmp_22 = arith.constant 0 : i1
		%tmp_23 = arith.extui %tmp_22 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_23: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %l_a__11_d1_1: i32)
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
		%l_t__5_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Call Statement: Start
		fifo.print("Rx: %i\n\00", %l_t__5_d1_0) : (i32)
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

