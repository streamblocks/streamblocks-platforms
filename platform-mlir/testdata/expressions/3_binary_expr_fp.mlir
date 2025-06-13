//-- Definition of actor class: Source
cal.actor @source ()
	ports_out(%Out: !fifo.input_port<f32>)
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
		%tmp_6 = arith.constant 1.1 : f32
		// l_t__3_d1_0 aliased to tmp_6
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		%tmp_7 = arith.constant 1.0 : f32
		%tmp_8 = arith.addf %tmp_6, %tmp_7 : f32
		// l_t__3_d1_1 aliased to tmp_8
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("Tx: %f\n\00", %tmp_8) : (f32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_9 = cal.get(%l_counter__2: !cal.state_ref<i32>) : i32
		%tmp_10 = arith.constant 1 : i1
		%tmp_11 = arith.extui %tmp_10 : i1 to i32
		%tmp_12 = arith.addi %tmp_9, %tmp_11 : i32
		cal.set(%l_counter__2: !cal.state_ref<i32>, %tmp_12: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<f32>, %tmp_8: f32)
		// Output Expression: End
	}
}

//-- Definition of actor class: BinaryExprFloat
cal.actor @DUT ()
	ports_in(%In: !fifo.output_port<f32>)
	ports_out(%Out: !fifo.input_port<f32>)
{
	// -- Actor body
	// Generation action: $untagged0
	cal.action "$untagged0" priority=0 {
		// Input Pattern: Start
		%l_t__5_d1_0 = fifo.pop(%In: !fifo.output_port<f32>) : f32
		// Input Pattern: End
		// Action Local Variable Decl: Start
		%tmp_0 = arith.constant 25.0 : f32
		%tmp_1 = arith.constant 25.0 : f32
		%tmp_2 = arith.addf %tmp_0, %tmp_1 : f32
		%tmp_3 = arith.constant 25.0 : f32
		%tmp_4 = arith.addf %tmp_2, %tmp_3 : f32
		%tmp_5 = arith.constant 25.0 : f32
		%tmp_6 = arith.addf %tmp_4, %tmp_5 : f32
		// l_x__7_d1_0 aliased to tmp_6
		// Action Local Variable Decl: End
		// Call Statement: Start
		fifo.print("1: %f\n\00", %tmp_6) : (f32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_7 = arith.constant 1.1 : f32
		%tmp_8 = arith.addf %tmp_6, %tmp_7 : f32
		// l_x__7_d1_1 aliased to tmp_8
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("2: %f\n\00", %tmp_8) : (f32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_9 = arith.constant 2.2 : f32
		%tmp_10 = arith.subf %tmp_8, %tmp_9 : f32
		// l_x__7_d1_2 aliased to tmp_10
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("3: %f\n\00", %tmp_10) : (f32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_11 = arith.constant 10.1 : f32
		%tmp_12 = arith.mulf %tmp_10, %tmp_11 : f32
		// l_x__7_d1_3 aliased to tmp_12
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("3: %f\n\00", %tmp_12) : (f32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_13 = arith.constant 2.3 : f32
		%tmp_14 = arith.divf %tmp_12, %tmp_13 : f32
		// l_x__7_d1_4 aliased to tmp_14
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("4: %f\n\00", %tmp_14) : (f32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_15 = arith.constant 0.0 : f32
		%tmp_16 = arith.subf %tmp_15, %tmp_14 : f32
		// l_x__7_d1_5 aliased to tmp_16
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("5: %f\n\00", %tmp_16) : (f32)
		// Call Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<f32>, %tmp_16: f32)
		// Output Expression: End
	}
}

//-- Definition of actor class: Sink
cal.actor @sink ()
	ports_in(%In: !fifo.output_port<f32>)
{
	// -- Actor body
	// Generation action: $untagged0
	cal.action "$untagged0" priority=0 {
		// Input Pattern: Start
		%l_t__9_d1_0 = fifo.pop(%In: !fifo.output_port<f32>) : f32
		// Input Pattern: End
		// Call Statement: Start
		fifo.print("Rx: %f\n\00", %l_t__9_d1_0) : (f32)
		// Call Statement: End
	}
}

// -- Top Network: Defines structure of actor application
cal.network
{

	// -- Instantiate channels between actors
	%queue_from_source_Out, %queue_to_DUT_In = fifo.create<f32>(1) : !fifo.input_port<f32>, !fifo.output_port<f32>
	%queue_from_DUT_Out, %queue_to_sink_In = fifo.create<f32>(1) : !fifo.input_port<f32>, !fifo.output_port<f32>

	// -- Instantiate actors (also known as nodes/instances)
	cal.create_instance @source "source" ()
		ports_out(%queue_from_source_Out: !fifo.input_port<f32>)
	cal.create_instance @DUT "DUT" ()
		ports_in(%queue_to_DUT_In: !fifo.output_port<f32>)
		ports_out(%queue_from_DUT_Out: !fifo.input_port<f32>)
	cal.create_instance @sink "sink" ()
		ports_in(%queue_to_sink_In: !fifo.output_port<f32>)

}

