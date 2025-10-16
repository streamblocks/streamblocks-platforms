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

//-- Definition of actor class: BinaryExpr
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
		%tmp_2 = arith.constant 25 : i5
		%tmp_3 = arith.constant 25 : i5
		%tmp_4 = arith.extui %tmp_2 : i5 to i6
		%tmp_5 = arith.extui %tmp_3 : i5 to i6
		%tmp_6 = arith.addi %tmp_4, %tmp_5 : i6
		%tmp_7 = arith.constant 25 : i5
		%tmp_8 = arith.extui %tmp_6 : i6 to i7
		%tmp_9 = arith.extui %tmp_7 : i5 to i7
		%tmp_10 = arith.addi %tmp_8, %tmp_9 : i7
		%tmp_11 = arith.constant 25 : i5
		%tmp_12 = arith.extui %tmp_10 : i7 to i8
		%tmp_13 = arith.extui %tmp_11 : i5 to i8
		%tmp_14 = arith.addi %tmp_12, %tmp_13 : i8
		%tmp_15 = arith.extui %tmp_14 : i8 to i32
		// l_x__7_d1_0 aliased to tmp_15
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		%tmp_16 = arith.constant 1 : i1
		%tmp_17 = arith.extui %tmp_16 : i1 to i32
		%tmp_18 = arith.addi %tmp_15, %tmp_17 : i32
		// l_x__7_d1_1 aliased to tmp_18
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("%i\n\00", %tmp_18) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_19 = arith.constant 2 : i2
		%tmp_20 = arith.extui %tmp_19 : i2 to i32
		%tmp_21 = arith.subi %tmp_18, %tmp_20 : i32
		// l_x__7_d1_2 aliased to tmp_21
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("%i\n\00", %tmp_21) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_22 = arith.constant 10 : i4
		%tmp_23 = arith.extui %tmp_22 : i4 to i32
		%tmp_24 = arith.muli %tmp_21, %tmp_23 : i32
		// l_x__7_d1_3 aliased to tmp_24
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("%i\n\00", %tmp_24) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_25 = arith.constant 2 : i2
		%tmp_26 = arith.extui %tmp_25 : i2 to i32
		%tmp_27 = arith.divsi %tmp_24, %tmp_26 : i32
		// l_x__7_d1_4 aliased to tmp_27
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("%i\n\00", %tmp_27) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_28 = arith.constant 4 : i3
		%tmp_29 = arith.trunci %tmp_27 : i32 to i3
		%tmp_30 = arith.remui %tmp_29, %tmp_28 : i3
		%tmp_31 = arith.extui %tmp_30 : i3 to i32
		// l_x__7_d1_5 aliased to tmp_31
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("%i\n\00", %tmp_31) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_32 = arith.constant 1 : i1
		%tmp_33 = arith.extui %tmp_32 : i1 to i32
		%tmp_34 = arith.shrsi %tmp_31, %tmp_33 : i32
		// l_x__7_d1_6 aliased to tmp_34
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("%i\n\00", %tmp_34) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_35 = arith.constant 6 : i3
		%tmp_36 = arith.extui %tmp_35 : i3 to i32
		%tmp_37 = arith.ori %tmp_34, %tmp_36 : i32
		// l_x__7_d1_7 aliased to tmp_37
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("%i\n\00", %tmp_37) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_38 = arith.constant 5 : i3
		%tmp_39 = arith.trunci %tmp_37 : i32 to i3
		%tmp_40 = arith.andi %tmp_39, %tmp_38 : i3
		%tmp_41 = arith.extui %tmp_40 : i3 to i32
		// l_x__7_d1_8 aliased to tmp_41
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("%i\n\00", %tmp_41) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_42 = arith.constant 1 : i1
		%tmp_43 = arith.extui %tmp_42 : i1 to i32
		%tmp_44 = arith.shli %tmp_41, %tmp_43 : i32
		// l_x__7_d1_9 aliased to tmp_44
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("%i\n\00", %tmp_44) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_45 = arith.constant 0 : i1
		%tmp_46 = arith.extui %tmp_45 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_46: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_44: i32)
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
		%l_t__9_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Call Statement: Start
		fifo.print("Rx: %i\n\00", %l_t__9_d1_0) : (i32)
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

