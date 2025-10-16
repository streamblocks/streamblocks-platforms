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
			%tmp_5 = arith.constant 1 : i1
			%tmp_6 = arith.extui %tmp_5 : i1 to i32
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

//-- Definition of actor class: ForeachSimple
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
			scf.yield %tmp_26, %tmp_24 : i32, i32
		}
		// Foreach Statement: End
		// Assignment Statement: Start
		%tmp_27 = arith.constant 0 : i1
		%tmp_28 = arith.extui %tmp_27 : i1 to i32
		// l_a__7_d1_2 aliased to tmp_28
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_29 = arith.constant 0 : i1
		%tmp_30 = arith.extui %tmp_29 : i1 to i32
		// l_b__8_d1_2 aliased to tmp_30
		// Assignment Statement: End
		// Foreach Statement: Begin
		%tmp_31 = arith.constant 0 : i1
		%tmp_32 = arith.extui %tmp_31 : i1 to i32
		%tmp_33_lb = index.casts %tmp_32 : i32 to index
		%tmp_34 = arith.constant 2 : i2
		%tmp_35 = arith.constant 2 : i2
		%tmp_36 = arith.constant 2 : i2
		%tmp_37 = arith.extui %tmp_35 : i2 to i4
		%tmp_38 = arith.extui %tmp_36 : i2 to i4
		%tmp_39 = arith.muli %tmp_37, %tmp_38 : i4
		%tmp_40 = arith.extui %tmp_34 : i2 to i5
		%tmp_41 = arith.extui %tmp_39 : i4 to i5
		%tmp_42 = arith.addi %tmp_40, %tmp_41 : i5
		%tmp_43 = arith.extui %tmp_42 : i5 to i32
		%tmp_44_ub = index.casts %tmp_43 : i32 to index
		%tmp_45_step = index.constant 1
		%tmp_46_ub_plus_1 = arith.addi %tmp_44_ub, %tmp_45_step : index
		%l_a__7_d1_3, %l_b__8_d1_3 = scf.for %l_j_d1_1 = %tmp_33_lb to %tmp_46_ub_plus_1 step %tmp_45_step
				iter_args(%l_a__7_d2_0 = %tmp_28, %l_b__8_d2_0 = %tmp_30) -> (i32, i32) {
			%l_j_d2_0 = arith.index_cast %l_j_d1_1 : index to i32
			// Assignment Statement: Start
			%tmp_47 = arith.constant 1 : i1
			%tmp_48 = arith.extui %tmp_47 : i1 to i32
			%tmp_49 = arith.addi %l_b__8_d2_0, %tmp_48 : i32
			// l_b__8_d2_1 aliased to tmp_49
			// Assignment Statement: End
			// Assignment Statement: Start
			%tmp_50 = arith.addi %l_a__7_d2_0, %l_j_d2_0 : i32
			// l_a__7_d2_1 aliased to tmp_50
			// Assignment Statement: End
			// Call Statement: Start
			fifo.print("a2: %i b2: %i\n\00", %tmp_50, %tmp_49) : (i32, i32)
			// Call Statement: End
			scf.yield %tmp_50, %tmp_49 : i32, i32
		}
		// Foreach Statement: End
		// Assignment Statement: Start
		%tmp_51 = arith.constant 0 : i1
		%tmp_52 = arith.extui %tmp_51 : i1 to i32
		// l_a__7_d1_4 aliased to tmp_52
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_53 = arith.constant 0 : i1
		%tmp_54 = arith.extui %tmp_53 : i1 to i32
		// l_b__8_d1_4 aliased to tmp_54
		// Assignment Statement: End
		// Foreach Statement: Begin
		%tmp_55 = arith.constant 0 : i1
		%tmp_56 = arith.extui %tmp_55 : i1 to i32
		%tmp_57_lb = index.casts %tmp_56 : i32 to index
		%tmp_58 = arith.constant 2 : i2
		%tmp_59 = arith.constant 2 : i2
		%tmp_60 = arith.constant 2 : i2
		%tmp_61 = arith.extui %tmp_59 : i2 to i4
		%tmp_62 = arith.extui %tmp_60 : i2 to i4
		%tmp_63 = arith.muli %tmp_61, %tmp_62 : i4
		%tmp_64 = arith.extui %tmp_58 : i2 to i5
		%tmp_65 = arith.extui %tmp_63 : i4 to i5
		%tmp_66 = arith.addi %tmp_64, %tmp_65 : i5
		%tmp_67 = arith.extui %tmp_66 : i5 to i32
		%tmp_68_ub = index.casts %tmp_67 : i32 to index
		%tmp_69_step = index.constant 1
		%tmp_70_ub_plus_1 = arith.addi %tmp_68_ub, %tmp_69_step : index
		%l_a__7_d1_5, %l_b__8_d1_5 = scf.for %l_j_d1_2 = %tmp_57_lb to %tmp_70_ub_plus_1 step %tmp_69_step
				iter_args(%l_a__7_d2_0 = %tmp_52, %l_b__8_d2_0 = %tmp_54) -> (i32, i32) {
			%l_j_d2_0 = arith.index_cast %l_j_d1_2 : index to i32
			// Assignment Statement: Start
			%tmp_71 = arith.constant 1 : i1
			%tmp_72 = arith.extui %tmp_71 : i1 to i32
			%tmp_73 = arith.addi %l_b__8_d2_0, %tmp_72 : i32
			// l_b__8_d2_1 aliased to tmp_73
			// Assignment Statement: End
			// Assignment Statement: Start
			%tmp_74 = arith.addi %l_a__7_d2_0, %l_j_d2_0 : i32
			// l_a__7_d2_1 aliased to tmp_74
			// Assignment Statement: End
			// Call Statement: Start
			fifo.print("a2: %i b2: %i\n\00", %tmp_74, %tmp_73) : (i32, i32)
			// Call Statement: End
			scf.yield %tmp_74, %tmp_73 : i32, i32
		}
		// Foreach Statement: End
		// Assignment Statement: Start
		%tmp_75 = arith.constant 0 : i1
		%tmp_76 = arith.extui %tmp_75 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_76: i32)
		// Assignment Statement: End
		// Output Expression: Start
		%tmp_77 = arith.addi %l_a__7_d1_5, %l_b__8_d1_5 : i32
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_77: i32)
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

