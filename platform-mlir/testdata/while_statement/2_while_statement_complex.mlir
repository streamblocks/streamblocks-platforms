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

//-- Definition of actor class: ComplexWhile
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
		// l_x__9_d1_0 aliased to tmp_3
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_4 = arith.constant 0 : i1
		%tmp_5 = arith.extui %tmp_4 : i1 to i32
		// l_y__10_d1_0 aliased to tmp_5
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_6 = arith.constant 0 : i1
		%tmp_7 = arith.extui %tmp_6 : i1 to i32
		// l_z__7_d1_0 aliased to tmp_7
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_8 = arith.constant 0 : i1
		%tmp_9 = arith.extui %tmp_8 : i1 to i32
		// l_a__8_d1_0 aliased to tmp_9
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_10 = arith.constant 0 : i1
		%tmp_11 = arith.extui %tmp_10 : i1 to i32
		// l_b__11_d1_0 aliased to tmp_11
		// Action Local Variable Decl: End
		// While Statement: Begin
		%l_x__9_d1_1, %l_y__10_d1_1, %l_z__7_d1_1 = scf.while(%l_x__9_d2_0 = %tmp_3, %l_y__10_d2_0 = %tmp_5, %l_z__7_d2_0 = %tmp_7) : (i32, i32, i32) -> (i32, i32, i32) {
			// While Statement: Condition Check
			%tmp_12 = arith.constant 4 : i3
			%tmp_13 = arith.extui %tmp_12 : i3 to i32
			%tmp_14 = arith.cmpi slt, %l_x__9_d2_0, %tmp_13 : i32
			scf.condition(%tmp_14) %l_x__9_d2_0, %l_y__10_d2_0, %l_z__7_d2_0 : i32, i32, i32
		} do {
			// While Statement: Body Execution
		^tmp_15(%l_x__9_d2_0: i32, %l_y__10_d2_0: i32, %l_z__7_d2_0: i32):
			// Assignment Statement: Start
			%tmp_16 = arith.constant 0 : i1
			%tmp_17 = arith.extui %tmp_16 : i1 to i32
			// l_y__10_d2_1 aliased to tmp_17
			// Assignment Statement: End
			// While Statement: Begin
			%l_y__10_d2_2, %l_z__7_d2_1 = scf.while(%l_y__10_d3_0 = %tmp_17, %l_z__7_d3_0 = %l_z__7_d2_0) : (i32, i32) -> (i32, i32) {
				// While Statement: Condition Check
				%tmp_18 = arith.constant 4 : i3
				%tmp_19 = arith.extui %tmp_18 : i3 to i32
				%tmp_20 = arith.cmpi slt, %l_y__10_d3_0, %tmp_19 : i32
				scf.condition(%tmp_20) %l_y__10_d3_0, %l_z__7_d3_0 : i32, i32
			} do {
				// While Statement: Body Execution
			^tmp_21(%l_y__10_d3_0: i32, %l_z__7_d3_0: i32):
				// Assignment Statement: Start
				%tmp_22 = arith.constant 1 : i1
				%tmp_23 = arith.extui %tmp_22 : i1 to i32
				%tmp_24 = arith.addi %l_y__10_d3_0, %tmp_23 : i32
				// l_y__10_d3_1 aliased to tmp_24
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_25 = arith.constant 1 : i1
				%tmp_26 = arith.extui %tmp_25 : i1 to i32
				%tmp_27 = arith.addi %l_z__7_d3_0, %tmp_26 : i32
				// l_z__7_d3_1 aliased to tmp_27
				// Assignment Statement: End
				scf.yield %tmp_24, %tmp_27: i32, i32
			}
			// While Statement: End
			// Assignment Statement: Start
			%tmp_28 = arith.constant 1 : i1
			%tmp_29 = arith.extui %tmp_28 : i1 to i32
			%tmp_30 = arith.addi %l_x__9_d2_0, %tmp_29 : i32
			// l_x__9_d2_1 aliased to tmp_30
			// Assignment Statement: End
			scf.yield %tmp_30, %l_y__10_d2_2, %l_z__7_d2_1: i32, i32, i32
		}
		// While Statement: End
		// Call Statement: Start
		fifo.print("z: %i\n\00", %l_z__7_d1_1) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_31 = arith.constant 0 : i1
		%tmp_32 = arith.extui %tmp_31 : i1 to i32
		// l_b__11_d1_1 aliased to tmp_32
		// Assignment Statement: End
		// While Statement: Begin
		%l_a__8_d1_1, %l_b__11_d1_2 = scf.while(%l_a__8_d2_0 = %tmp_9, %l_b__11_d2_0 = %tmp_32) : (i32, i32) -> (i32, i32) {
			// While Statement: Condition Check
			%tmp_33 = arith.constant 10 : i4
			%tmp_34 = arith.extui %tmp_33 : i4 to i32
			%tmp_35 = arith.cmpi slt, %l_b__11_d2_0, %tmp_34 : i32
			scf.condition(%tmp_35) %l_a__8_d2_0, %l_b__11_d2_0 : i32, i32
		} do {
			// While Statement: Body Execution
		^tmp_36(%l_a__8_d2_0: i32, %l_b__11_d2_0: i32):
			// If Statement: Begin
			%tmp_37 = arith.constant 5 : i3
			%tmp_38 = arith.extui %tmp_37 : i3 to i32
			%tmp_39 = arith.cmpi slt, %l_b__11_d2_0, %tmp_38 : i32
			%l_b__11_d2_1 = scf.if %tmp_39 -> (i32) {
				// Assignment Statement: Start
				%tmp_40 = arith.constant 1 : i1
				%tmp_41 = arith.extui %tmp_40 : i1 to i32
				%tmp_42 = arith.addi %l_b__11_d2_0, %tmp_41 : i32
				// l_b__11_d3_0 aliased to tmp_42
				// Assignment Statement: End
				scf.yield %tmp_42 : i32
			} else {
				// Assignment Statement: Start
				%tmp_43 = arith.constant 2 : i2
				%tmp_44 = arith.extui %tmp_43 : i2 to i32
				%tmp_45 = arith.muli %l_b__11_d2_0, %tmp_44 : i32
				// l_b__11_d3_0 aliased to tmp_45
				// Assignment Statement: End
				scf.yield %tmp_45 : i32
			}
			// If Statement: End
			// Assignment Statement: Start
			%tmp_46 = arith.constant 1 : i1
			%tmp_47 = arith.extui %tmp_46 : i1 to i32
			%tmp_48 = arith.addi %l_a__8_d2_0, %tmp_47 : i32
			// l_a__8_d2_1 aliased to tmp_48
			// Assignment Statement: End
			scf.yield %tmp_48, %l_b__11_d2_1: i32, i32
		}
		// While Statement: End
		// Call Statement: Start
		fifo.print("b: %i\n\00", %l_b__11_d1_2) : (i32)
		// Call Statement: End
		// Call Statement: Start
		fifo.print("a: %i\n\00", %l_a__8_d1_1) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_49 = arith.constant 0 : i1
		%tmp_50 = arith.extui %tmp_49 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_50: i32)
		// Assignment Statement: End
		// Output Expression: Start
		%tmp_51 = arith.constant 10 : i4
		%tmp_52 = arith.extui %tmp_51 : i4 to i32
		%tmp_53 = arith.muli %l_t__5_d1_0, %tmp_52 : i32
		%tmp_54 = arith.addi %l_z__7_d1_1, %tmp_53 : i32
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_54: i32)
		%tmp_55 = arith.constant 10 : i4
		%tmp_56 = arith.extui %tmp_55 : i4 to i32
		%tmp_57 = arith.muli %l_t__5_d1_0, %tmp_56 : i32
		%tmp_58 = arith.addi %l_a__8_d1_1, %tmp_57 : i32
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_58: i32)
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
		%l_t__13_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Call Statement: Start
		fifo.print("Rx: %i\n\00", %l_t__13_d1_0) : (i32)
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

