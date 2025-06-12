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

//-- Definition of actor class: ComplexWhile
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
		// l_x__9_d1_0 aliased to tmp_1
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_2 = arith.constant 0 : i1
		%tmp_3 = arith.extui %tmp_2 : i1 to i32
		// l_y__10_d1_0 aliased to tmp_3
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_4 = arith.constant 0 : i1
		%tmp_5 = arith.extui %tmp_4 : i1 to i32
		// l_z__7_d1_0 aliased to tmp_5
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_6 = arith.constant 0 : i1
		%tmp_7 = arith.extui %tmp_6 : i1 to i32
		// l_a__8_d1_0 aliased to tmp_7
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_8 = arith.constant 0 : i1
		%tmp_9 = arith.extui %tmp_8 : i1 to i32
		// l_b__11_d1_0 aliased to tmp_9
		// Action Local Variable Decl: End
		// While Statement: Begin
		%l_x__9_d1_1, %l_y__10_d1_1, %l_z__7_d1_1 = scf.while(%l_x__9_d2_0 = %tmp_1, %l_y__10_d2_0 = %tmp_3, %l_z__7_d2_0 = %tmp_5) : (i32, i32, i32) -> (i32, i32, i32) {
			// While Statement: Condition Check
			%tmp_10 = arith.constant 4 : i3
			%tmp_11 = arith.extui %tmp_10 : i3 to i32
			%tmp_12 = arith.cmpi slt, %l_x__9_d2_0, %tmp_11 : i32
			scf.condition(%tmp_12) %l_x__9_d2_0, %l_y__10_d2_0, %l_z__7_d2_0 : i32, i32, i32
		} do {
			// While Statement: Body Execution
		^tmp_13(%l_x__9_d2_0: i32, %l_y__10_d2_0: i32, %l_z__7_d2_0: i32):
			// Assignment Statement: Start
			%tmp_14 = arith.constant 0 : i1
			%tmp_15 = arith.extui %tmp_14 : i1 to i32
			// l_y__10_d2_1 aliased to tmp_15
			// Assignment Statement: End
			// While Statement: Begin
			%l_y__10_d2_2, %l_z__7_d2_1 = scf.while(%l_y__10_d3_0 = %tmp_15, %l_z__7_d3_0 = %l_z__7_d2_0) : (i32, i32) -> (i32, i32) {
				// While Statement: Condition Check
				%tmp_16 = arith.constant 4 : i3
				%tmp_17 = arith.extui %tmp_16 : i3 to i32
				%tmp_18 = arith.cmpi slt, %l_y__10_d3_0, %tmp_17 : i32
				scf.condition(%tmp_18) %l_y__10_d3_0, %l_z__7_d3_0 : i32, i32
			} do {
				// While Statement: Body Execution
			^tmp_19(%l_y__10_d3_0: i32, %l_z__7_d3_0: i32):
				// Assignment Statement: Start
				%tmp_20 = arith.constant 1 : i1
				%tmp_21 = arith.extui %tmp_20 : i1 to i32
				%tmp_22 = arith.addi %l_y__10_d3_0, %tmp_21 : i32
				// l_y__10_d3_1 aliased to tmp_22
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_23 = arith.constant 1 : i1
				%tmp_24 = arith.extui %tmp_23 : i1 to i32
				%tmp_25 = arith.addi %l_z__7_d3_0, %tmp_24 : i32
				// l_z__7_d3_1 aliased to tmp_25
				// Assignment Statement: End
				scf.yield %tmp_22, %tmp_25: i32, i32
			}
			// While Statement: End
			// Assignment Statement: Start
			%tmp_26 = arith.constant 1 : i1
			%tmp_27 = arith.extui %tmp_26 : i1 to i32
			%tmp_28 = arith.addi %l_x__9_d2_0, %tmp_27 : i32
			// l_x__9_d2_1 aliased to tmp_28
			// Assignment Statement: End
			scf.yield %tmp_28, %l_y__10_d2_2, %l_z__7_d2_1: i32, i32, i32
		}
		// While Statement: End
		// Call Statement: Start
		fifo.print("z: %i\n\00", %l_z__7_d1_1) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_29 = arith.constant 0 : i1
		%tmp_30 = arith.extui %tmp_29 : i1 to i32
		// l_b__11_d1_1 aliased to tmp_30
		// Assignment Statement: End
		// While Statement: Begin
		%l_a__8_d1_1, %l_b__11_d1_2 = scf.while(%l_a__8_d2_0 = %tmp_7, %l_b__11_d2_0 = %tmp_30) : (i32, i32) -> (i32, i32) {
			// While Statement: Condition Check
			%tmp_31 = arith.constant 10 : i4
			%tmp_32 = arith.extui %tmp_31 : i4 to i32
			%tmp_33 = arith.cmpi slt, %l_b__11_d2_0, %tmp_32 : i32
			scf.condition(%tmp_33) %l_a__8_d2_0, %l_b__11_d2_0 : i32, i32
		} do {
			// While Statement: Body Execution
		^tmp_34(%l_a__8_d2_0: i32, %l_b__11_d2_0: i32):
			// If Statement: Begin
			%tmp_35 = arith.constant 5 : i3
			%tmp_36 = arith.extui %tmp_35 : i3 to i32
			%tmp_37 = arith.cmpi slt, %l_b__11_d2_0, %tmp_36 : i32
			%l_b__11_d2_1 = scf.if %tmp_37 -> (i32) {
				// Assignment Statement: Start
				%tmp_38 = arith.constant 1 : i1
				%tmp_39 = arith.extui %tmp_38 : i1 to i32
				%tmp_40 = arith.addi %l_b__11_d2_0, %tmp_39 : i32
				// l_b__11_d3_0 aliased to tmp_40
				// Assignment Statement: End
				scf.yield %tmp_40 : i32
			} else {
				// Assignment Statement: Start
				%tmp_41 = arith.constant 2 : i2
				%tmp_42 = arith.extui %tmp_41 : i2 to i32
				%tmp_43 = arith.muli %l_b__11_d2_0, %tmp_42 : i32
				// l_b__11_d3_0 aliased to tmp_43
				// Assignment Statement: End
				scf.yield %tmp_43 : i32
			}
			// If Statement: End
			// Assignment Statement: Start
			%tmp_44 = arith.constant 1 : i1
			%tmp_45 = arith.extui %tmp_44 : i1 to i32
			%tmp_46 = arith.addi %l_a__8_d2_0, %tmp_45 : i32
			// l_a__8_d2_1 aliased to tmp_46
			// Assignment Statement: End
			scf.yield %tmp_46, %l_b__11_d2_1: i32, i32
		}
		// While Statement: End
		// Call Statement: Start
		fifo.print("b: %i\n\00", %l_b__11_d1_2) : (i32)
		// Call Statement: End
		// Call Statement: Start
		fifo.print("a: %i\n\00", %l_a__8_d1_1) : (i32)
		// Call Statement: End
		// Output Expression: Start
		%tmp_47 = arith.constant 10 : i4
		%tmp_48 = arith.extui %tmp_47 : i4 to i32
		%tmp_49 = arith.muli %l_t__5_d1_0, %tmp_48 : i32
		%tmp_50 = arith.addi %l_z__7_d1_1, %tmp_49 : i32
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_50: i32)
		%tmp_51 = arith.constant 10 : i4
		%tmp_52 = arith.extui %tmp_51 : i4 to i32
		%tmp_53 = arith.muli %l_t__5_d1_0, %tmp_52 : i32
		%tmp_54 = arith.addi %l_a__8_d1_1, %tmp_53 : i32
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_54: i32)
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
		%l_t__13_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Call Statement: Start
		fifo.print("Rx: %i\n\00", %l_t__13_d1_0) : (i32)
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

