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
			%tmp_3 = arith.constant 5 : i3
			%tmp_4 = arith.extui %tmp_3 : i3 to i32
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
		%tmp_10 = arith.constant 10 : i4
		%tmp_11 = arith.extui %tmp_10 : i4 to i32
		%tmp_12 = arith.muli %tmp_9, %tmp_11 : i32
		// l_t__3_d1_1 aliased to tmp_12
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("Tx: %i\n\00", %tmp_12) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_13 = cal.get(%l_counter__2: !cal.state_ref<i32>) : i32
		%tmp_14 = arith.constant 1 : i1
		%tmp_15 = arith.extui %tmp_14 : i1 to i32
		%tmp_16 = arith.addi %tmp_13, %tmp_15 : i32
		cal.set(%l_counter__2: !cal.state_ref<i32>, %tmp_16: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_12: i32)
		// Output Expression: End
	}
}

//-- Definition of actor class: IfComplex
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
		// l_x__7_d1_0 aliased to tmp_1
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_2 = arith.constant 0 : i1
		%tmp_3 = arith.extui %tmp_2 : i1 to i32
		// l_y__8_d1_0 aliased to tmp_3
		// Action Local Variable Decl: End
		// If Statement: Begin
		%tmp_4 = arith.constant 20 : i5
		%tmp_5 = arith.extui %tmp_4 : i5 to i32
		%tmp_6 = arith.cmpi sge, %l_t__5_d1_0, %tmp_5 : i32
		%l_x__7_d1_1 = scf.if %tmp_6 -> (i32) {
			// Assignment Statement: Start
			%tmp_7 = arith.constant 1 : i1
			%tmp_8 = arith.extui %tmp_7 : i1 to i32
			%tmp_9 = arith.addi %tmp_1, %tmp_8 : i32
			// l_x__7_d2_0 aliased to tmp_9
			// Assignment Statement: End
			scf.yield %tmp_9 : i32
		} else {
			scf.yield %tmp_1 : i32
		}
		// If Statement: End
		// If Statement: Begin
		%tmp_10 = arith.constant 40 : i6
		%tmp_11 = arith.extui %tmp_10 : i6 to i32
		%tmp_12 = arith.cmpi sge, %l_t__5_d1_0, %tmp_11 : i32
		%l_x__7_d1_2, %l_y__8_d1_1 = scf.if %tmp_12 -> (i32, i32) {
			// Assignment Statement: Start
			%tmp_13 = arith.constant 1 : i1
			%tmp_14 = arith.extui %tmp_13 : i1 to i32
			%tmp_15 = arith.addi %l_x__7_d1_1, %tmp_14 : i32
			// l_x__7_d2_0 aliased to tmp_15
			// Assignment Statement: End
			scf.yield %tmp_15, %tmp_3 : i32, i32
		} else {
			// Assignment Statement: Start
			%tmp_16 = arith.constant 6 : i3
			%tmp_17 = arith.extui %tmp_16 : i3 to i32
			%tmp_18 = arith.addi %tmp_3, %tmp_17 : i32
			// l_y__8_d2_0 aliased to tmp_18
			// Assignment Statement: End
			scf.yield %l_x__7_d1_1, %tmp_18 : i32, i32
		}
		// If Statement: End
		// If Statement: Begin
		%tmp_19 = arith.constant 60 : i6
		%tmp_20 = arith.extui %tmp_19 : i6 to i32
		%tmp_21 = arith.cmpi slt, %l_t__5_d1_0, %tmp_20 : i32
		%l_x__7_d1_3 = scf.if %tmp_21 -> (i32) {
			// If Statement: Begin
			%tmp_22 = arith.constant 20 : i5
			%tmp_23 = arith.extui %tmp_22 : i5 to i32
			%tmp_24 = arith.cmpi sgt, %l_t__5_d1_0, %tmp_23 : i32
			%l_x__7_d2_0 = scf.if %tmp_24 -> (i32) {
				// If Statement: Begin
				%tmp_25 = arith.constant 40 : i6
				%tmp_26 = arith.extui %tmp_25 : i6 to i32
				%tmp_27 = arith.cmpi slt, %l_t__5_d1_0, %tmp_26 : i32
				%l_x__7_d3_0 = scf.if %tmp_27 -> (i32) {
					// Assignment Statement: Start
					%tmp_28 = arith.constant 100000 : i17
					%tmp_29 = arith.extui %tmp_28 : i17 to i32
					%tmp_30 = arith.addi %l_x__7_d1_2, %tmp_29 : i32
					// l_x__7_d4_0 aliased to tmp_30
					// Assignment Statement: End
					scf.yield %tmp_30 : i32
				} else {
					scf.yield %l_x__7_d1_2 : i32
				}
				// If Statement: End
				scf.yield %l_x__7_d3_0 : i32
			} else {
				scf.yield %l_x__7_d1_2 : i32
			}
			// If Statement: End
			scf.yield %l_x__7_d2_0 : i32
		} else {
			scf.yield %l_x__7_d1_2 : i32
		}
		// If Statement: End
		// If Statement: Begin
		%tmp_31 = arith.constant 80 : i7
		%tmp_32 = arith.extui %tmp_31 : i7 to i32
		%tmp_33 = arith.cmpi slt, %l_t__5_d1_0, %tmp_32 : i32
		scf.if %tmp_33 {
		} else {
		}
		// If Statement: End
		// Output Expression: Start
		%tmp_34 = arith.addi %l_t__5_d1_0, %l_x__7_d1_3 : i32
		%tmp_35 = arith.addi %tmp_34, %l_y__8_d1_1 : i32
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_35: i32)
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
	%queue_from_pass_Out, %queue_to_sink_In = fifo.create<i32>(3) : !fifo.input_port<i32>, !fifo.output_port<i32>
	%queue_from_source_Out, %queue_to_pass_In = fifo.create<i32>(3) : !fifo.input_port<i32>, !fifo.output_port<i32>

	// -- Instantiate actors (also known as nodes/instances)
	cal.create_instance @source "source" ()
		ports_out(%queue_from_source_Out: !fifo.input_port<i32>)
	cal.create_instance @pass "pass" ()
		ports_in(%queue_to_pass_In: !fifo.output_port<i32>)
		ports_out(%queue_from_pass_Out: !fifo.input_port<i32>)
	cal.create_instance @sink "sink" ()
		ports_in(%queue_to_sink_In: !fifo.output_port<i32>)

}

