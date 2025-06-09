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
		%tmp_7 = arith.constant 2 : i2
		%tmp_8 = arith.extui %tmp_7 : i2 to i32
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

//-- Definition of actor class: UnaryExpr
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
		%tmp_0 = arith.constant 100 : i7
		%tmp_1 = arith.extui %tmp_0 : i7 to i32
		// l_x__7_d1_0 aliased to tmp_1
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		%tmp_2 = arith.constant 0 : i32
		%tmp_3 = arith.subi %tmp_2, %l_t__5_d1_0 : i32
		// l_x__7_d1_1 aliased to tmp_3
		// Assignment Statement: End
		fifo.print("%i\n\00", %tmp_3) : (i32)
		// If Statement: Begin
		%tmp_4 = arith.constant 0 : i32
		%tmp_5 = arith.subi %tmp_4, %l_t__5_d1_0 : i32
		%tmp_6 = arith.cmpi ne, %tmp_3, %tmp_5 : i32
		%tmp_7 = arith.constant 1 : i1
		%tmp_8 = arith.xori %tmp_6, %tmp_7 : i1
		%l_x__7_d1_2 = scf.if %tmp_8 -> (i32) {
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
		fifo.print("%i\n\00", %l_x__7_d1_2) : (i32)
		// Assignment Statement: Start
		%tmp_12 = arith.constant 0xffffffff : i32
		%tmp_13 = arith.xori %l_x__7_d1_2, %tmp_12 : i32
		%tmp_14 = arith.constant 1 : i1
		%tmp_15 = arith.extui %tmp_14 : i1 to i32
		%tmp_16 = arith.addi %tmp_13, %tmp_15 : i32
		// l_x__7_d1_3 aliased to tmp_16
		// Assignment Statement: End
		fifo.print("%i\n\00", %tmp_16) : (i32)
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_16: i32)
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
	cal.create_instance @source "source" ()
		ports_out(%queue_from_source_Out: !fifo.input_port<i32>)
	cal.create_instance @pass "pass" ()
		ports_in(%queue_to_pass_In: !fifo.output_port<i32>)
		ports_out(%queue_from_pass_Out: !fifo.input_port<i32>)
	cal.create_instance @sink "sink" ()
		ports_in(%queue_to_sink_In: !fifo.output_port<i32>)

}

