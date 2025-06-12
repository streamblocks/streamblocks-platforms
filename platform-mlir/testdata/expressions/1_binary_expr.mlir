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

//-- Definition of actor class: BinaryExpr
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
		%tmp_0 = arith.constant 25 : i5
		%tmp_1 = arith.constant 25 : i5
		%tmp_2 = arith.extui %tmp_0 : i5 to i6
		%tmp_3 = arith.extui %tmp_1 : i5 to i6
		%tmp_4 = arith.addi %tmp_2, %tmp_3 : i6
		%tmp_5 = arith.constant 25 : i5
		%tmp_6 = arith.extui %tmp_4 : i6 to i7
		%tmp_7 = arith.extui %tmp_5 : i5 to i7
		%tmp_8 = arith.addi %tmp_6, %tmp_7 : i7
		%tmp_9 = arith.constant 25 : i5
		%tmp_10 = arith.extui %tmp_8 : i7 to i8
		%tmp_11 = arith.extui %tmp_9 : i5 to i8
		%tmp_12 = arith.addi %tmp_10, %tmp_11 : i8
		%tmp_13 = arith.extui %tmp_12 : i8 to i32
		// l_x__7_d1_0 aliased to tmp_13
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		%tmp_14 = arith.constant 1 : i1
		%tmp_15 = arith.extui %tmp_14 : i1 to i32
		%tmp_16 = arith.addi %tmp_13, %tmp_15 : i32
		// l_x__7_d1_1 aliased to tmp_16
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("%i\n\00", %tmp_16) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_17 = arith.constant 2 : i2
		%tmp_18 = arith.extui %tmp_17 : i2 to i32
		%tmp_19 = arith.subi %tmp_16, %tmp_18 : i32
		// l_x__7_d1_2 aliased to tmp_19
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("%i\n\00", %tmp_19) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_20 = arith.constant 10 : i4
		%tmp_21 = arith.extui %tmp_20 : i4 to i32
		%tmp_22 = arith.muli %tmp_19, %tmp_21 : i32
		// l_x__7_d1_3 aliased to tmp_22
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("%i\n\00", %tmp_22) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_23 = arith.constant 2 : i2
		%tmp_24 = arith.extui %tmp_23 : i2 to i32
		%tmp_25 = arith.divsi %tmp_22, %tmp_24 : i32
		// l_x__7_d1_4 aliased to tmp_25
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("%i\n\00", %tmp_25) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_26 = arith.constant 4 : i3
		%tmp_27 = arith.trunci %tmp_25 : i32 to i3
		%tmp_28 = arith.remui %tmp_27, %tmp_26 : i3
		%tmp_29 = arith.extui %tmp_28 : i3 to i32
		// l_x__7_d1_5 aliased to tmp_29
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("%i\n\00", %tmp_29) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_30 = arith.constant 1 : i1
		%tmp_31 = arith.extui %tmp_30 : i1 to i32
		%tmp_32 = arith.shrsi %tmp_29, %tmp_31 : i32
		// l_x__7_d1_6 aliased to tmp_32
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("%i\n\00", %tmp_32) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_33 = arith.constant 6 : i3
		%tmp_34 = arith.extui %tmp_33 : i3 to i32
		%tmp_35 = arith.ori %tmp_32, %tmp_34 : i32
		// l_x__7_d1_7 aliased to tmp_35
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("%i\n\00", %tmp_35) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_36 = arith.constant 5 : i3
		%tmp_37 = arith.trunci %tmp_35 : i32 to i3
		%tmp_38 = arith.andi %tmp_37, %tmp_36 : i3
		%tmp_39 = arith.extui %tmp_38 : i3 to i32
		// l_x__7_d1_8 aliased to tmp_39
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("%i\n\00", %tmp_39) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_40 = arith.constant 1 : i1
		%tmp_41 = arith.extui %tmp_40 : i1 to i32
		%tmp_42 = arith.shli %tmp_39, %tmp_41 : i32
		// l_x__7_d1_9 aliased to tmp_42
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("%i\n\00", %tmp_42) : (i32)
		// Call Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_42: i32)
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
		%l_t__9_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Call Statement: Start
		fifo.print("Rx: %i\n\00", %l_t__9_d1_0) : (i32)
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

