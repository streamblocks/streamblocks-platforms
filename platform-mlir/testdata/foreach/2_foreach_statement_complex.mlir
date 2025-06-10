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
		fifo.print("Tx: %i\n\00", %tmp_9) : (i32)
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
		%l_a__7_d1_1, %l_b__8_d1_1 = scf.for %l_j_d1_0 = %tmp_6_lb to %tmp_17_ub step %tmp_18_step
				iter_args(%l_a__7_d2_0 = %tmp_1, %l_b__8_d2_0 = %tmp_3) -> (i32, i32) {
			%l_j_d2_0 = arith.index_cast %l_j_d1_0 : index to i32
			%l_j_d2_1 = arith.trunci %l_j_d2_0 : i32 to i8
			// Assignment Statement: Start
			%tmp_19 = arith.constant 1 : i1
			%tmp_20 = arith.extui %tmp_19 : i1 to i32
			%tmp_21 = arith.addi %l_b__8_d2_0, %tmp_20 : i32
			// l_b__8_d2_1 aliased to tmp_21
			// Assignment Statement: End
			// Assignment Statement: Start
			%tmp_22 = arith.extui %l_j_d2_1 : i8 to i32
			%tmp_23 = arith.addi %l_a__7_d2_0, %tmp_22 : i32
			// l_a__7_d2_1 aliased to tmp_23
			// Assignment Statement: End
			fifo.print("a1: %i b1: %i\n\00", %tmp_23, %tmp_21) : (i32, i32)
			// Foreach Statement: Begin
			%tmp_24 = arith.constant 0 : i1
			%tmp_25 = arith.extui %tmp_24 : i1 to i32
			%tmp_26_lb = index.casts %tmp_25 : i32 to index
			%tmp_27 = arith.constant 2 : i2
			%tmp_28 = arith.constant 2 : i2
			%tmp_29 = arith.constant 2 : i2
			%tmp_30 = arith.extui %tmp_28 : i2 to i4
			%tmp_31 = arith.extui %tmp_29 : i2 to i4
			%tmp_32 = arith.muli %tmp_30, %tmp_31 : i4
			%tmp_33 = arith.extui %tmp_27 : i2 to i5
			%tmp_34 = arith.extui %tmp_32 : i4 to i5
			%tmp_35 = arith.addi %tmp_33, %tmp_34 : i5
			%tmp_36 = arith.extui %tmp_35 : i5 to i32
			%tmp_37_ub = index.casts %tmp_36 : i32 to index
			%tmp_38_step = index.constant 1
			%l_a__7_d2_2, %l_b__8_d2_2 = scf.for %l_j_d2_2 = %tmp_26_lb to %tmp_37_ub step %tmp_38_step
					iter_args(%l_a__7_d3_0 = %tmp_23, %l_b__8_d3_0 = %tmp_21) -> (i32, i32) {
				%l_j_d3_0 = arith.index_cast %l_j_d2_2 : index to i32
				// Assignment Statement: Start
				%tmp_39 = arith.constant 1 : i1
				%tmp_40 = arith.extui %tmp_39 : i1 to i32
				%tmp_41 = arith.addi %l_b__8_d3_0, %tmp_40 : i32
				// l_b__8_d3_1 aliased to tmp_41
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_42 = arith.addi %l_a__7_d3_0, %l_j_d3_0 : i32
				// l_a__7_d3_1 aliased to tmp_42
				// Assignment Statement: End
				fifo.print("    a2: %i b2: %i\n\00", %tmp_42, %tmp_41) : (i32, i32)
				scf.yield %tmp_42, %tmp_41 : i32, i32
			}
			// Foreach Statement: End
			scf.yield %l_a__7_d2_2, %l_b__8_d2_2 : i32, i32
		}
		// Foreach Statement: End
		fifo.print("a3: %i b3: %i\n\00", %l_a__7_d1_1, %l_b__8_d1_1) : (i32, i32)
		// Output Expression: Start
		%tmp_43 = arith.addi %l_a__7_d1_1, %l_b__8_d1_1 : i32
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_43: i32)
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
		fifo.print("Rx: %i\n\00", %l_t__10_d1_0) : (i32)
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

