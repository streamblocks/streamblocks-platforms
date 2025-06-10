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

//-- Definition of actor class: ForeachSimple
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
			scf.yield %tmp_23, %tmp_21 : i32, i32
		}
		// Foreach Statement: End
		// Assignment Statement: Start
		%tmp_24 = arith.constant 0 : i1
		%tmp_25 = arith.extui %tmp_24 : i1 to i32
		// l_a__7_d1_2 aliased to tmp_25
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_26 = arith.constant 0 : i1
		%tmp_27 = arith.extui %tmp_26 : i1 to i32
		// l_b__8_d1_2 aliased to tmp_27
		// Assignment Statement: End
		// Foreach Statement: Begin
		%tmp_28 = arith.constant 0 : i1
		%tmp_29 = arith.extui %tmp_28 : i1 to i32
		%tmp_30_lb = index.casts %tmp_29 : i32 to index
		%tmp_31 = arith.constant 2 : i2
		%tmp_32 = arith.constant 2 : i2
		%tmp_33 = arith.constant 2 : i2
		%tmp_34 = arith.extui %tmp_32 : i2 to i4
		%tmp_35 = arith.extui %tmp_33 : i2 to i4
		%tmp_36 = arith.muli %tmp_34, %tmp_35 : i4
		%tmp_37 = arith.extui %tmp_31 : i2 to i5
		%tmp_38 = arith.extui %tmp_36 : i4 to i5
		%tmp_39 = arith.addi %tmp_37, %tmp_38 : i5
		%tmp_40 = arith.extui %tmp_39 : i5 to i32
		%tmp_41_ub = index.casts %tmp_40 : i32 to index
		%tmp_42_step = index.constant 1
		%l_a__7_d1_3, %l_b__8_d1_3 = scf.for %l_j_d1_1 = %tmp_30_lb to %tmp_41_ub step %tmp_42_step
				iter_args(%l_a__7_d2_0 = %tmp_25, %l_b__8_d2_0 = %tmp_27) -> (i32, i32) {
			%l_j_d2_0 = arith.index_cast %l_j_d1_1 : index to i32
			// Assignment Statement: Start
			%tmp_43 = arith.constant 1 : i1
			%tmp_44 = arith.extui %tmp_43 : i1 to i32
			%tmp_45 = arith.addi %l_b__8_d2_0, %tmp_44 : i32
			// l_b__8_d2_1 aliased to tmp_45
			// Assignment Statement: End
			// Assignment Statement: Start
			%tmp_46 = arith.addi %l_a__7_d2_0, %l_j_d2_0 : i32
			// l_a__7_d2_1 aliased to tmp_46
			// Assignment Statement: End
			fifo.print("a2: %i b2: %i\n\00", %tmp_46, %tmp_45) : (i32, i32)
			scf.yield %tmp_46, %tmp_45 : i32, i32
		}
		// Foreach Statement: End
		// Assignment Statement: Start
		%tmp_47 = arith.constant 0 : i1
		%tmp_48 = arith.extui %tmp_47 : i1 to i32
		// l_a__7_d1_4 aliased to tmp_48
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_49 = arith.constant 0 : i1
		%tmp_50 = arith.extui %tmp_49 : i1 to i32
		// l_b__8_d1_4 aliased to tmp_50
		// Assignment Statement: End
		// Foreach Statement: Begin
		%tmp_51 = arith.constant 0 : i1
		%tmp_52 = arith.extui %tmp_51 : i1 to i32
		%tmp_53_lb = index.casts %tmp_52 : i32 to index
		%tmp_54 = arith.constant 2 : i2
		%tmp_55 = arith.constant 2 : i2
		%tmp_56 = arith.constant 2 : i2
		%tmp_57 = arith.extui %tmp_55 : i2 to i4
		%tmp_58 = arith.extui %tmp_56 : i2 to i4
		%tmp_59 = arith.muli %tmp_57, %tmp_58 : i4
		%tmp_60 = arith.extui %tmp_54 : i2 to i5
		%tmp_61 = arith.extui %tmp_59 : i4 to i5
		%tmp_62 = arith.addi %tmp_60, %tmp_61 : i5
		%tmp_63 = arith.extui %tmp_62 : i5 to i32
		%tmp_64_ub = index.casts %tmp_63 : i32 to index
		%tmp_65_step = index.constant 1
		%l_a__7_d1_5, %l_b__8_d1_5 = scf.for %l_j_d1_2 = %tmp_53_lb to %tmp_64_ub step %tmp_65_step
				iter_args(%l_a__7_d2_0 = %tmp_48, %l_b__8_d2_0 = %tmp_50) -> (i32, i32) {
			%l_j_d2_0 = arith.index_cast %l_j_d1_2 : index to i32
			// Assignment Statement: Start
			%tmp_66 = arith.constant 1 : i1
			%tmp_67 = arith.extui %tmp_66 : i1 to i32
			%tmp_68 = arith.addi %l_b__8_d2_0, %tmp_67 : i32
			// l_b__8_d2_1 aliased to tmp_68
			// Assignment Statement: End
			// Assignment Statement: Start
			%tmp_69 = arith.addi %l_a__7_d2_0, %l_j_d2_0 : i32
			// l_a__7_d2_1 aliased to tmp_69
			// Assignment Statement: End
			fifo.print("a2: %i b2: %i\n\00", %tmp_69, %tmp_68) : (i32, i32)
			scf.yield %tmp_69, %tmp_68 : i32, i32
		}
		// Foreach Statement: End
		// Output Expression: Start
		%tmp_70 = arith.addi %l_a__7_d1_5, %l_b__8_d1_5 : i32
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_70: i32)
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
	%queue_from_pass_Out, %queue_to_sink_In = fifo.create<i32>(1) : !fifo.input_port<i32>, !fifo.output_port<i32>
	%queue_from_source_Out, %queue_to_pass_In = fifo.create<i32>(1) : !fifo.input_port<i32>, !fifo.output_port<i32>

	// -- Instantiate actors (also known as nodes/instances)
	cal.create_instance @source "source" ()
		ports_out(%queue_from_source_Out: !fifo.input_port<i32>)
	cal.create_instance @pass "pass" ()
		ports_in(%queue_to_pass_In: !fifo.output_port<i32>)
		ports_out(%queue_from_pass_Out: !fifo.input_port<i32>)
	cal.create_instance @sink "sink" ()
		ports_in(%queue_to_sink_In: !fifo.output_port<i32>)

}

