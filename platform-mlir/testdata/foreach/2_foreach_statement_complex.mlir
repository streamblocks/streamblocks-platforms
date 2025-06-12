//-- Definition of actor class: Source
cal.actor @source ()
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
			%tmp_3 = arith.constant 3 : i2
			%tmp_4 = arith.extui %tmp_3 : i2 to i32
			// Evaluate global variable $eval1 done: assigned to tmp_4 above in this context.
			%tmp_5 = arith.cmpi slt, %tmp_2, %tmp_4 : i32
			cal.predicate_result %tmp_5 : i1
		}
		// Guard: End
		// Action Local Variable Decl: Start
		%tmp_6 = cal.get(%l_counter__2: !cal.state_ref<i32>) : i32
		// l_t__3_d1_0 aliased to tmp_6
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		%tmp_7 = arith.constant 1 : i1
		%tmp_8 = arith.extui %tmp_7 : i1 to i32
		%tmp_9 = arith.addi %tmp_6, %tmp_8 : i32
		// l_t__3_d1_1 aliased to tmp_9
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("Tx: %i\n\00", %tmp_9) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_10 = cal.get(%l_counter__2: !cal.state_ref<i32>) : i32
		%tmp_11 = arith.constant 1 : i1
		%tmp_12 = arith.extui %tmp_11 : i1 to i32
		%tmp_13 = arith.addi %tmp_10, %tmp_12 : i32
		cal.set(%l_counter__2: !cal.state_ref<i32>, %tmp_13: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_9: i32)
		// Output Expression: End
	}
}

//-- Definition of actor class: ForeachComplex
cal.actor @pass ()
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
		// l_a__7_d1_0 aliased to tmp_1
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_2 = arith.constant 0 : i1
		%tmp_3 = arith.extui %tmp_2 : i1 to i32
		// l_b__8_d1_0 aliased to tmp_3
		// Action Local Variable Decl: End
		// Foreach Statement: Begin
		%tmp_4 = arith.constant 0 : i1
		%tmp_5 = arith.extui %tmp_4 : i1 to i32
		%tmp_6_lb = index.casts %tmp_5 : i32 to index
		%tmp_7 = arith.constant 2 : i2
		%tmp_8 = arith.constant 2 : i2
		%tmp_9 = arith.constant 2 : i2
		%tmp_10 = arith.extui %tmp_8 : i2 to i4
		%tmp_11 = arith.extui %tmp_9 : i2 to i4
		%tmp_12 = arith.muli %tmp_10, %tmp_11 : i4
		%tmp_13 = arith.extui %tmp_7 : i2 to i5
		%tmp_14 = arith.extui %tmp_12 : i4 to i5
		%tmp_15 = arith.addi %tmp_13, %tmp_14 : i5
		%tmp_16 = arith.extui %tmp_15 : i5 to i32
		%tmp_17_ub = index.casts %tmp_16 : i32 to index
		%tmp_18_step = index.constant 1
		%tmp_19_ub_plus_1 = arith.addi %tmp_17_ub, %tmp_18_step : index
		%l_a__7_d1_1, %l_b__8_d1_1 = scf.for %l_j_d1_0 = %tmp_6_lb to %tmp_19_ub_plus_1 step %tmp_18_step
				iter_args(%l_a__7_d2_0 = %tmp_1, %l_b__8_d2_0 = %tmp_3) -> (i32, i32) {
			%l_j_d2_0 = arith.index_cast %l_j_d1_0 : index to i32
			%l_j_d2_1 = arith.trunci %l_j_d2_0 : i32 to i8
			// Assignment Statement: Start
			%tmp_20 = arith.constant 1 : i1
			%tmp_21 = arith.extui %tmp_20 : i1 to i32
			%tmp_22 = arith.addi %l_b__8_d2_0, %tmp_21 : i32
			// l_b__8_d2_1 aliased to tmp_22
			// Assignment Statement: End
			// Assignment Statement: Start
			%tmp_23 = arith.extui %l_j_d2_1 : i8 to i32
			%tmp_24 = arith.addi %l_a__7_d2_0, %tmp_23 : i32
			// l_a__7_d2_1 aliased to tmp_24
			// Assignment Statement: End
			// Call Statement: Start
			fifo.print("a1: %i b1: %i\n\00", %tmp_24, %tmp_22) : (i32, i32)
			// Call Statement: End
			// Foreach Statement: Begin
			%tmp_25 = arith.constant 0 : i1
			%tmp_26 = arith.extui %tmp_25 : i1 to i32
			%tmp_27_lb = index.casts %tmp_26 : i32 to index
			%tmp_28 = arith.constant 2 : i2
			%tmp_29 = arith.constant 2 : i2
			%tmp_30 = arith.constant 2 : i2
			%tmp_31 = arith.extui %tmp_29 : i2 to i4
			%tmp_32 = arith.extui %tmp_30 : i2 to i4
			%tmp_33 = arith.muli %tmp_31, %tmp_32 : i4
			%tmp_34 = arith.extui %tmp_28 : i2 to i5
			%tmp_35 = arith.extui %tmp_33 : i4 to i5
			%tmp_36 = arith.addi %tmp_34, %tmp_35 : i5
			%tmp_37 = arith.extui %tmp_36 : i5 to i32
			%tmp_38_ub = index.casts %tmp_37 : i32 to index
			%tmp_39_step = index.constant 1
			%tmp_40_ub_plus_1 = arith.addi %tmp_38_ub, %tmp_39_step : index
			%l_a__7_d2_2, %l_b__8_d2_2 = scf.for %l_j_d2_2 = %tmp_27_lb to %tmp_40_ub_plus_1 step %tmp_39_step
					iter_args(%l_a__7_d3_0 = %tmp_24, %l_b__8_d3_0 = %tmp_22) -> (i32, i32) {
				%l_j_d3_0 = arith.index_cast %l_j_d2_2 : index to i32
				// Assignment Statement: Start
				%tmp_41 = arith.constant 1 : i1
				%tmp_42 = arith.extui %tmp_41 : i1 to i32
				%tmp_43 = arith.addi %l_b__8_d3_0, %tmp_42 : i32
				// l_b__8_d3_1 aliased to tmp_43
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_44 = arith.addi %l_a__7_d3_0, %l_j_d3_0 : i32
				// l_a__7_d3_1 aliased to tmp_44
				// Assignment Statement: End
				// Call Statement: Start
				fifo.print("    a2: %i b2: %i\n\00", %tmp_44, %tmp_43) : (i32, i32)
				// Call Statement: End
				scf.yield %tmp_44, %tmp_43 : i32, i32
			}
			// Foreach Statement: End
			scf.yield %l_a__7_d2_2, %l_b__8_d2_2 : i32, i32
		}
		// Foreach Statement: End
		// Call Statement: Start
		fifo.print("a3: %i b3: %i\n\00", %l_a__7_d1_1, %l_b__8_d1_1) : (i32, i32)
		// Call Statement: End
		// Output Expression: Start
		%tmp_45 = arith.addi %l_a__7_d1_1, %l_b__8_d1_1 : i32
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_45: i32)
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
		%l_t__10_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Call Statement: Start
		fifo.print("Rx: %i\n\00", %l_t__10_d1_0) : (i32)
		// Call Statement: End
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

