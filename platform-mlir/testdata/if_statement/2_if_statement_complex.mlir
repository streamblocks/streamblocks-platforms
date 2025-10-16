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
			%tmp_5 = arith.constant 5 : i3
			%tmp_6 = arith.extui %tmp_5 : i3 to i32
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
		%tmp_12 = arith.constant 10 : i4
		%tmp_13 = arith.extui %tmp_12 : i4 to i32
		%tmp_14 = arith.muli %tmp_11, %tmp_13 : i32
		// l_t__3_d1_1 aliased to tmp_14
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("Tx: %i\n\00", %tmp_14) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_15 = cal.get(%l_counter__2: !cal.state_ref<i32>) : i32
		%tmp_16 = arith.constant 1 : i1
		%tmp_17 = arith.extui %tmp_16 : i1 to i32
		%tmp_18 = arith.addi %tmp_15, %tmp_17 : i32
		cal.set(%l_counter__2: !cal.state_ref<i32>, %tmp_18: i32)
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_19 = arith.constant 0 : i1
		%tmp_20 = arith.extui %tmp_19 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_20: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_14: i32)
		// Output Expression: End
	}
}

//-- Definition of actor class: IfComplex
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
		// l_x__7_d1_0 aliased to tmp_3
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_4 = arith.constant 0 : i1
		%tmp_5 = arith.extui %tmp_4 : i1 to i32
		// l_y__8_d1_0 aliased to tmp_5
		// Action Local Variable Decl: End
		// If Statement: Begin
		%tmp_6 = arith.constant 20 : i5
		%tmp_7 = arith.extui %tmp_6 : i5 to i32
		%tmp_8 = arith.cmpi sge, %l_t__5_d1_0, %tmp_7 : i32
		%l_x__7_d1_1 = scf.if %tmp_8 -> (i32) {
			// Assignment Statement: Start
			%tmp_9 = arith.constant 1 : i1
			%tmp_10 = arith.extui %tmp_9 : i1 to i32
			%tmp_11 = arith.addi %tmp_3, %tmp_10 : i32
			// l_x__7_d2_0 aliased to tmp_11
			// Assignment Statement: End
			scf.yield %tmp_11 : i32
		} else {
			scf.yield %tmp_3 : i32
		}
		// If Statement: End
		// If Statement: Begin
		%tmp_12 = arith.constant 40 : i6
		%tmp_13 = arith.extui %tmp_12 : i6 to i32
		%tmp_14 = arith.cmpi sge, %l_t__5_d1_0, %tmp_13 : i32
		%l_x__7_d1_2, %l_y__8_d1_1 = scf.if %tmp_14 -> (i32, i32) {
			// Assignment Statement: Start
			%tmp_15 = arith.constant 1 : i1
			%tmp_16 = arith.extui %tmp_15 : i1 to i32
			%tmp_17 = arith.addi %l_x__7_d1_1, %tmp_16 : i32
			// l_x__7_d2_0 aliased to tmp_17
			// Assignment Statement: End
			scf.yield %tmp_17, %tmp_5 : i32, i32
		} else {
			// Assignment Statement: Start
			%tmp_18 = arith.constant 6 : i3
			%tmp_19 = arith.extui %tmp_18 : i3 to i32
			%tmp_20 = arith.addi %tmp_5, %tmp_19 : i32
			// l_y__8_d2_0 aliased to tmp_20
			// Assignment Statement: End
			scf.yield %l_x__7_d1_1, %tmp_20 : i32, i32
		}
		// If Statement: End
		// If Statement: Begin
		%tmp_21 = arith.constant 60 : i6
		%tmp_22 = arith.extui %tmp_21 : i6 to i32
		%tmp_23 = arith.cmpi slt, %l_t__5_d1_0, %tmp_22 : i32
		%l_x__7_d1_3 = scf.if %tmp_23 -> (i32) {
			// If Statement: Begin
			%tmp_24 = arith.constant 20 : i5
			%tmp_25 = arith.extui %tmp_24 : i5 to i32
			%tmp_26 = arith.cmpi sgt, %l_t__5_d1_0, %tmp_25 : i32
			%l_x__7_d2_0 = scf.if %tmp_26 -> (i32) {
				// If Statement: Begin
				%tmp_27 = arith.constant 40 : i6
				%tmp_28 = arith.extui %tmp_27 : i6 to i32
				%tmp_29 = arith.cmpi slt, %l_t__5_d1_0, %tmp_28 : i32
				%l_x__7_d3_0 = scf.if %tmp_29 -> (i32) {
					// Assignment Statement: Start
					%tmp_30 = arith.constant 100000 : i17
					%tmp_31 = arith.extui %tmp_30 : i17 to i32
					%tmp_32 = arith.addi %l_x__7_d1_2, %tmp_31 : i32
					// l_x__7_d4_0 aliased to tmp_32
					// Assignment Statement: End
					scf.yield %tmp_32 : i32
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
		%tmp_33 = arith.constant 80 : i7
		%tmp_34 = arith.extui %tmp_33 : i7 to i32
		%tmp_35 = arith.cmpi slt, %l_t__5_d1_0, %tmp_34 : i32
		scf.if %tmp_35 {
		} else {
		}
		// If Statement: End
		// Assignment Statement: Start
		%tmp_36 = arith.constant 0 : i1
		%tmp_37 = arith.extui %tmp_36 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_37: i32)
		// Assignment Statement: End
		// Output Expression: Start
		%tmp_38 = arith.addi %l_t__5_d1_0, %l_x__7_d1_3 : i32
		%tmp_39 = arith.addi %tmp_38, %l_y__8_d1_1 : i32
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_39: i32)
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

