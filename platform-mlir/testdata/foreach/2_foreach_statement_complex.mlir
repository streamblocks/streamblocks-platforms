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
			%tmp_5 = arith.constant 3 : i2
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
		// Assignment Statement: Start
		%tmp_9 = arith.constant 1 : i1
		%tmp_10 = arith.extui %tmp_9 : i1 to i32
		%tmp_11 = arith.addi %tmp_8, %tmp_10 : i32
		// l_t__3_d1_1 aliased to tmp_11
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("Tx: %i\n\00", %tmp_11) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_12 = cal.get(%l_counter__2: !cal.state_ref<i32>) : i32
		%tmp_13 = arith.constant 1 : i1
		%tmp_14 = arith.extui %tmp_13 : i1 to i32
		%tmp_15 = arith.addi %tmp_12, %tmp_14 : i32
		cal.set(%l_counter__2: !cal.state_ref<i32>, %tmp_15: i32)
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

//-- Definition of actor class: ForeachComplex
cal.actor @pass ()
	ports_in(%In: !fifo.output_port<i32>)
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
		%l_t__5_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Action Local Variable Decl: Start
		%tmp_2 = arith.constant 0 : i1
		%tmp_3 = arith.extui %tmp_2 : i1 to i32
		// l_a__7_d1_0 aliased to tmp_3
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_4 = arith.constant 0 : i1
		%tmp_5 = arith.extui %tmp_4 : i1 to i32
		// l_b__8_d1_0 aliased to tmp_5
		// Action Local Variable Decl: End
		// Foreach Statement: Begin
		%tmp_6 = arith.constant 0 : i1
		%tmp_7 = arith.extui %tmp_6 : i1 to i32
		%tmp_8_lb = index.casts %tmp_7 : i32 to index
		%tmp_9 = arith.constant 2 : i2
		%tmp_10 = arith.constant 2 : i2
		%tmp_11 = arith.constant 2 : i2
		%tmp_12 = arith.extui %tmp_10 : i2 to i4
		%tmp_13 = arith.extui %tmp_11 : i2 to i4
		%tmp_14 = arith.muli %tmp_12, %tmp_13 : i4
		%tmp_15 = arith.extui %tmp_9 : i2 to i5
		%tmp_16 = arith.extui %tmp_14 : i4 to i5
		%tmp_17 = arith.addi %tmp_15, %tmp_16 : i5
		%tmp_18 = arith.extui %tmp_17 : i5 to i32
		%tmp_19_ub = index.casts %tmp_18 : i32 to index
		%tmp_20_step = index.constant 1
		%tmp_21_ub_plus_1 = arith.addi %tmp_19_ub, %tmp_20_step : index
		%l_a__7_d1_1, %l_b__8_d1_1 = scf.for %l_j_d1_0 = %tmp_8_lb to %tmp_21_ub_plus_1 step %tmp_20_step
				iter_args(%l_a__7_d2_0 = %tmp_3, %l_b__8_d2_0 = %tmp_5) -> (i32, i32) {
			%l_j_d2_0 = arith.index_cast %l_j_d1_0 : index to i32
			%l_j_d2_1 = arith.trunci %l_j_d2_0 : i32 to i8
			// Assignment Statement: Start
			%tmp_22 = arith.constant 1 : i1
			%tmp_23 = arith.extui %tmp_22 : i1 to i32
			%tmp_24 = arith.addi %l_b__8_d2_0, %tmp_23 : i32
			// l_b__8_d2_1 aliased to tmp_24
			// Assignment Statement: End
			// Assignment Statement: Start
			%tmp_25 = arith.extui %l_j_d2_1 : i8 to i32
			%tmp_26 = arith.addi %l_a__7_d2_0, %tmp_25 : i32
			// l_a__7_d2_1 aliased to tmp_26
			// Assignment Statement: End
			// Call Statement: Start
			fifo.print("a1: %i b1: %i\n\00", %tmp_26, %tmp_24) : (i32, i32)
			// Call Statement: End
			// Foreach Statement: Begin
			%tmp_27 = arith.constant 0 : i1
			%tmp_28 = arith.extui %tmp_27 : i1 to i32
			%tmp_29_lb = index.casts %tmp_28 : i32 to index
			%tmp_30 = arith.constant 2 : i2
			%tmp_31 = arith.constant 2 : i2
			%tmp_32 = arith.constant 2 : i2
			%tmp_33 = arith.extui %tmp_31 : i2 to i4
			%tmp_34 = arith.extui %tmp_32 : i2 to i4
			%tmp_35 = arith.muli %tmp_33, %tmp_34 : i4
			%tmp_36 = arith.extui %tmp_30 : i2 to i5
			%tmp_37 = arith.extui %tmp_35 : i4 to i5
			%tmp_38 = arith.addi %tmp_36, %tmp_37 : i5
			%tmp_39 = arith.extui %tmp_38 : i5 to i32
			%tmp_40_ub = index.casts %tmp_39 : i32 to index
			%tmp_41_step = index.constant 1
			%tmp_42_ub_plus_1 = arith.addi %tmp_40_ub, %tmp_41_step : index
			%l_a__7_d2_2, %l_b__8_d2_2 = scf.for %l_j_d2_2 = %tmp_29_lb to %tmp_42_ub_plus_1 step %tmp_41_step
					iter_args(%l_a__7_d3_0 = %tmp_26, %l_b__8_d3_0 = %tmp_24) -> (i32, i32) {
				%l_j_d3_0 = arith.index_cast %l_j_d2_2 : index to i32
				// Assignment Statement: Start
				%tmp_43 = arith.constant 1 : i1
				%tmp_44 = arith.extui %tmp_43 : i1 to i32
				%tmp_45 = arith.addi %l_b__8_d3_0, %tmp_44 : i32
				// l_b__8_d3_1 aliased to tmp_45
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_46 = arith.addi %l_a__7_d3_0, %l_j_d3_0 : i32
				// l_a__7_d3_1 aliased to tmp_46
				// Assignment Statement: End
				// Call Statement: Start
				fifo.print("    a2: %i b2: %i\n\00", %tmp_46, %tmp_45) : (i32, i32)
				// Call Statement: End
				scf.yield %tmp_46, %tmp_45 : i32, i32
			}
			// Foreach Statement: End
			scf.yield %l_a__7_d2_2, %l_b__8_d2_2 : i32, i32
		}
		// Foreach Statement: End
		// Call Statement: Start
		fifo.print("a3: %i b3: %i\n\00", %l_a__7_d1_1, %l_b__8_d1_1) : (i32, i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_47 = arith.constant 0 : i1
		%tmp_48 = arith.extui %tmp_47 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_48: i32)
		// Assignment Statement: End
		// Output Expression: Start
		%tmp_49 = arith.addi %l_a__7_d1_1, %l_b__8_d1_1 : i32
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_49: i32)
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
		%l_t__10_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Call Statement: Start
		fifo.print("Rx: %i\n\00", %l_t__10_d1_0) : (i32)
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
	%queue_from_pass_Out, %queue_to_sink_In = fifo.create<i32>(4) : !fifo.input_port<i32>, !fifo.output_port<i32>
	%queue_from_source_Out, %queue_to_pass_In = fifo.create<i32>(4) : !fifo.input_port<i32>, !fifo.output_port<i32>

	// -- Instantiate actors (also known as nodes/instances)
	cal.create_instance @source "source" ()
		ports_out(%queue_from_source_Out: !fifo.input_port<i32>)
	cal.create_instance @pass "pass" ()
		ports_in(%queue_to_pass_In: !fifo.output_port<i32>)
		ports_out(%queue_from_pass_Out: !fifo.input_port<i32>)
	cal.create_instance @sink "sink" ()
		ports_in(%queue_to_sink_In: !fifo.output_port<i32>)

}

