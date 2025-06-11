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
			fifo.print("a1: %i b1: %i\n\00", %tmp_24, %tmp_22) : (i32, i32)
			scf.yield %tmp_24, %tmp_22 : i32, i32
		}
		// Foreach Statement: End
		// Assignment Statement: Start
		%tmp_25 = arith.constant 0 : i1
		%tmp_26 = arith.extui %tmp_25 : i1 to i32
		// l_a__7_d1_2 aliased to tmp_26
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_27 = arith.constant 0 : i1
		%tmp_28 = arith.extui %tmp_27 : i1 to i32
		// l_b__8_d1_2 aliased to tmp_28
		// Assignment Statement: End
		// Foreach Statement: Begin
		%tmp_29 = arith.constant 0 : i1
		%tmp_30 = arith.extui %tmp_29 : i1 to i32
		%tmp_31_lb = index.casts %tmp_30 : i32 to index
		%tmp_32 = arith.constant 2 : i2
		%tmp_33 = arith.constant 2 : i2
		%tmp_34 = arith.constant 2 : i2
		%tmp_35 = arith.extui %tmp_33 : i2 to i4
		%tmp_36 = arith.extui %tmp_34 : i2 to i4
		%tmp_37 = arith.muli %tmp_35, %tmp_36 : i4
		%tmp_38 = arith.extui %tmp_32 : i2 to i5
		%tmp_39 = arith.extui %tmp_37 : i4 to i5
		%tmp_40 = arith.addi %tmp_38, %tmp_39 : i5
		%tmp_41 = arith.extui %tmp_40 : i5 to i32
		%tmp_42_ub = index.casts %tmp_41 : i32 to index
		%tmp_43_step = index.constant 1
		%tmp_44_ub_plus_1 = arith.addi %tmp_42_ub, %tmp_43_step : index
		%l_a__7_d1_3, %l_b__8_d1_3 = scf.for %l_j_d1_1 = %tmp_31_lb to %tmp_44_ub_plus_1 step %tmp_43_step
				iter_args(%l_a__7_d2_0 = %tmp_26, %l_b__8_d2_0 = %tmp_28) -> (i32, i32) {
			%l_j_d2_0 = arith.index_cast %l_j_d1_1 : index to i32
			// Assignment Statement: Start
			%tmp_45 = arith.constant 1 : i1
			%tmp_46 = arith.extui %tmp_45 : i1 to i32
			%tmp_47 = arith.addi %l_b__8_d2_0, %tmp_46 : i32
			// l_b__8_d2_1 aliased to tmp_47
			// Assignment Statement: End
			// Assignment Statement: Start
			%tmp_48 = arith.addi %l_a__7_d2_0, %l_j_d2_0 : i32
			// l_a__7_d2_1 aliased to tmp_48
			// Assignment Statement: End
			fifo.print("a2: %i b2: %i\n\00", %tmp_48, %tmp_47) : (i32, i32)
			scf.yield %tmp_48, %tmp_47 : i32, i32
		}
		// Foreach Statement: End
		// Assignment Statement: Start
		%tmp_49 = arith.constant 0 : i1
		%tmp_50 = arith.extui %tmp_49 : i1 to i32
		// l_a__7_d1_4 aliased to tmp_50
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_51 = arith.constant 0 : i1
		%tmp_52 = arith.extui %tmp_51 : i1 to i32
		// l_b__8_d1_4 aliased to tmp_52
		// Assignment Statement: End
		// Foreach Statement: Begin
		%tmp_53 = arith.constant 0 : i1
		%tmp_54 = arith.extui %tmp_53 : i1 to i32
		%tmp_55_lb = index.casts %tmp_54 : i32 to index
		%tmp_56 = arith.constant 2 : i2
		%tmp_57 = arith.constant 2 : i2
		%tmp_58 = arith.constant 2 : i2
		%tmp_59 = arith.extui %tmp_57 : i2 to i4
		%tmp_60 = arith.extui %tmp_58 : i2 to i4
		%tmp_61 = arith.muli %tmp_59, %tmp_60 : i4
		%tmp_62 = arith.extui %tmp_56 : i2 to i5
		%tmp_63 = arith.extui %tmp_61 : i4 to i5
		%tmp_64 = arith.addi %tmp_62, %tmp_63 : i5
		%tmp_65 = arith.extui %tmp_64 : i5 to i32
		%tmp_66_ub = index.casts %tmp_65 : i32 to index
		%tmp_67_step = index.constant 1
		%tmp_68_ub_plus_1 = arith.addi %tmp_66_ub, %tmp_67_step : index
		%l_a__7_d1_5, %l_b__8_d1_5 = scf.for %l_j_d1_2 = %tmp_55_lb to %tmp_68_ub_plus_1 step %tmp_67_step
				iter_args(%l_a__7_d2_0 = %tmp_50, %l_b__8_d2_0 = %tmp_52) -> (i32, i32) {
			%l_j_d2_0 = arith.index_cast %l_j_d1_2 : index to i32
			// Assignment Statement: Start
			%tmp_69 = arith.constant 1 : i1
			%tmp_70 = arith.extui %tmp_69 : i1 to i32
			%tmp_71 = arith.addi %l_b__8_d2_0, %tmp_70 : i32
			// l_b__8_d2_1 aliased to tmp_71
			// Assignment Statement: End
			// Assignment Statement: Start
			%tmp_72 = arith.addi %l_a__7_d2_0, %l_j_d2_0 : i32
			// l_a__7_d2_1 aliased to tmp_72
			// Assignment Statement: End
			fifo.print("a2: %i b2: %i\n\00", %tmp_72, %tmp_71) : (i32, i32)
			scf.yield %tmp_72, %tmp_71 : i32, i32
		}
		// Foreach Statement: End
		// Output Expression: Start
		%tmp_73 = arith.addi %l_a__7_d1_5, %l_b__8_d1_5 : i32
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_73: i32)
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

