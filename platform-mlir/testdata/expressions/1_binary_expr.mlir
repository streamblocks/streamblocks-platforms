//-- Definition of actor class: Source
cal.actor @Source ()
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

//-- Definition of actor class: BinaryExpr
cal.actor @BinaryExpr ()
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
		fifo.print("%i\n\00", %tmp_16) : (i32)
		// Assignment Statement: Start
		%tmp_17 = arith.constant 2 : i2
		%tmp_18 = arith.extui %tmp_17 : i2 to i32
		%tmp_19 = arith.subi %tmp_16, %tmp_18 : i32
		// l_x__7_d1_2 aliased to tmp_19
		// Assignment Statement: End
		fifo.print("%i\n\00", %tmp_19) : (i32)
		// Assignment Statement: Start
		%tmp_20 = arith.constant 10 : i4
		%tmp_21 = arith.extui %tmp_20 : i4 to i32
		%tmp_22 = arith.muli %tmp_19, %tmp_21 : i32
		// l_x__7_d1_3 aliased to tmp_22
		// Assignment Statement: End
		fifo.print("%i\n\00", %tmp_22) : (i32)
		// Assignment Statement: Start
		%tmp_23 = arith.constant 2 : i2
		%tmp_24 = arith.extui %tmp_23 : i2 to i32
		%tmp_25 = arith.divsi %tmp_22, %tmp_24 : i32
		// l_x__7_d1_4 aliased to tmp_25
		// Assignment Statement: End
		fifo.print("%i\n\00", %tmp_25) : (i32)
		// Assignment Statement: Start
		%tmp_26 = arith.constant 4 : i3
		%tmp_27 = arith.extui %tmp_26 : i3 to i32
		%tmp_28 = arith.remsi %tmp_25, %tmp_27 : i32
		%tmp_29 = arith.trunci %tmp_28 : i32 to i3
		%tmp_30 = arith.extui %tmp_29 : i3 to i32
		// l_x__7_d1_5 aliased to tmp_30
		// Assignment Statement: End
		fifo.print("%i\n\00", %tmp_30) : (i32)
		// Assignment Statement: Start
		%tmp_31 = arith.constant 1 : i1
		%tmp_32 = arith.extui %tmp_31 : i1 to i32
		%tmp_33 = arith.shrsi %tmp_30, %tmp_32 : i32
		// l_x__7_d1_6 aliased to tmp_33
		// Assignment Statement: End
		fifo.print("%i\n\00", %tmp_33) : (i32)
		// Assignment Statement: Start
		%tmp_34 = arith.constant 6 : i3
		%tmp_35 = arith.extui %tmp_34 : i3 to i32
		%tmp_36 = arith.ori %tmp_33, %tmp_35 : i32
		// l_x__7_d1_7 aliased to tmp_36
		// Assignment Statement: End
		fifo.print("%i\n\00", %tmp_36) : (i32)
		// Assignment Statement: Start
		%tmp_37 = arith.constant 5 : i3
		%tmp_38 = arith.extui %tmp_37 : i3 to i32
		%tmp_39 = arith.andi %tmp_36, %tmp_38 : i32
		%tmp_40 = arith.trunci %tmp_39 : i32 to i3
		%tmp_41 = arith.extui %tmp_40 : i3 to i32
		// l_x__7_d1_8 aliased to tmp_41
		// Assignment Statement: End
		fifo.print("%i\n\00", %tmp_41) : (i32)
		// Assignment Statement: Start
		%tmp_42 = arith.constant 1 : i1
		%tmp_43 = arith.extui %tmp_42 : i1 to i32
		%tmp_44 = arith.shli %tmp_41, %tmp_43 : i32
		// l_x__7_d1_9 aliased to tmp_44
		// Assignment Statement: End
		fifo.print("%i\n\00", %tmp_44) : (i32)
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_44: i32)
		// Output Expression: End
	}
}

//-- Definition of actor class: Sink
cal.actor @Sink ()
	ports_in(%In: !fifo.output_port<i32>)
{
	// -- Actor body
	// Generation action: $untagged0
	cal.action "$untagged0" priority=0 {
		// Input Pattern: Start
		%l_t__9_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		fifo.print("Rx: %i\n\00", %l_t__9_d1_0) : (i32)
	}
}

// -- Top Network: Defines structure of actor application
cal.network
{

	// -- Instantiate channels between actors
	%queue_from_pass_Out, %queue_to_sink_In = fifo.create<i32>(3) : !fifo.input_port<i32>, !fifo.output_port<i32>
	%queue_from_source_Out, %queue_to_pass_In = fifo.create<i32>(3) : !fifo.input_port<i32>, !fifo.output_port<i32>

	// -- Instantiate actors (also known as nodes/instances)
	cal.create_instance @Source "source" ()
		ports_out(%queue_from_source_Out: !fifo.input_port<i32>)
	cal.create_instance @BinaryExpr "pass" ()
		ports_in(%queue_to_pass_In: !fifo.output_port<i32>)
		ports_out(%queue_from_pass_Out: !fifo.input_port<i32>)
	cal.create_instance @Sink "sink" ()
		ports_in(%queue_to_sink_In: !fifo.output_port<i32>)

}

