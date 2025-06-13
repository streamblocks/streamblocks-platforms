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
		%tmp_14 = arith.sitofp %tmp_9 : i32 to f32
		fifo.push(%Out: !fifo.input_port<f32>, %tmp_14: f32)
		// Output Expression: End
	}
}

//-- Definition of actor class: BinaryExprFpAndIntMixed
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
		%l_x__7_d1_0 = arith.constant 0.0 : f32
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_0 = arith.constant 25 : i5
		%tmp_1 = arith.constant 25.0 : f32
		%tmp_2 = arith.uitofp %tmp_0 : i5 to f32
		%tmp_3 = arith.addf %tmp_2, %tmp_1 : f32
		%tmp_4 = arith.constant 25 : i5
		%tmp_5 = arith.uitofp %tmp_4 : i5 to f32
		%tmp_6 = arith.addf %tmp_3, %tmp_5 : f32
		%tmp_7 = arith.constant 25.0 : f32
		%tmp_8 = arith.addf %tmp_6, %tmp_7 : f32
		%tmp_9 = arith.constant 0.5 : f32
		%tmp_10 = arith.addf %tmp_8, %tmp_9 : f32
		%tmp_11 = arith.fptosi %tmp_10 : f32 to i32
		// l_y__8_d1_0 aliased to tmp_11
		// Action Local Variable Decl: End
		// Call Statement: Start
		fifo.print("t: %f\n\00", %l_t__5_d1_0) : (f32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_12 = arith.constant 25 : i5
		%tmp_13 = arith.constant 25.0 : f32
		%tmp_14 = arith.uitofp %tmp_12 : i5 to f32
		%tmp_15 = arith.addf %tmp_14, %tmp_13 : f32
		%tmp_16 = arith.constant 25 : i5
		%tmp_17 = arith.uitofp %tmp_16 : i5 to f32
		%tmp_18 = arith.addf %tmp_15, %tmp_17 : f32
		%tmp_19 = arith.constant 25.0 : f32
		%tmp_20 = arith.addf %tmp_18, %tmp_19 : f32
		%tmp_21 = arith.constant 0.5 : f32
		%tmp_22 = arith.addf %tmp_20, %tmp_21 : f32
		// l_x__7_d1_1 aliased to tmp_22
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("x1: %f\n\00", %tmp_22) : (f32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_23 = arith.constant 1 : i1
		%tmp_24 = arith.uitofp %tmp_23 : i1 to f32
		%tmp_25 = arith.addf %tmp_22, %tmp_24 : f32
		// l_x__7_d1_2 aliased to tmp_25
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("x2: %f\n\00", %tmp_25) : (f32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_26 = arith.constant 2 : i2
		%tmp_27 = arith.uitofp %tmp_26 : i2 to f32
		%tmp_28 = arith.subf %tmp_25, %tmp_27 : f32
		// l_x__7_d1_3 aliased to tmp_28
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("x3: %f\n\00", %tmp_28) : (f32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_29 = arith.constant 10 : i4
		%tmp_30 = arith.uitofp %tmp_29 : i4 to f32
		%tmp_31 = arith.mulf %tmp_30, %tmp_28 : f32
		// l_x__7_d1_4 aliased to tmp_31
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("x4: %f\n\00", %tmp_31) : (f32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_32 = arith.constant 2 : i2
		%tmp_33 = arith.uitofp %tmp_32 : i2 to f32
		%tmp_34 = arith.divf %tmp_31, %tmp_33 : f32
		// l_x__7_d1_5 aliased to tmp_34
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("x5: %f\n\00", %tmp_34) : (f32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_35 = arith.constant 0.0 : f32
		%tmp_36 = arith.subf %tmp_35, %tmp_34 : f32
		// l_x__7_d1_6 aliased to tmp_36
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("x6: %f\n\00", %tmp_36) : (f32)
		// Call Statement: End
		// Call Statement: Start
		fifo.print("y1: %i\n\00", %tmp_11) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_37 = arith.constant 1.5 : f32
		%tmp_38 = arith.sitofp %tmp_11 : i32 to f32
		%tmp_39 = arith.addf %tmp_38, %tmp_37 : f32
		%tmp_40 = arith.fptosi %tmp_39 : f32 to i32
		// l_y__8_d1_1 aliased to tmp_40
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("y2: %i\n\00", %tmp_40) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_41 = arith.constant 2.5 : f32
		%tmp_42 = arith.sitofp %tmp_40 : i32 to f32
		%tmp_43 = arith.subf %tmp_42, %tmp_41 : f32
		%tmp_44 = arith.fptosi %tmp_43 : f32 to i32
		// l_y__8_d1_2 aliased to tmp_44
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("y3: %i\n\00", %tmp_44) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_45 = arith.constant 10.5 : f32
		%tmp_46 = arith.sitofp %tmp_44 : i32 to f32
		%tmp_47 = arith.mulf %tmp_46, %tmp_45 : f32
		%tmp_48 = arith.fptosi %tmp_47 : f32 to i32
		// l_y__8_d1_3 aliased to tmp_48
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("y4: %i\n\00", %tmp_48) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_49 = arith.constant 2.5 : f32
		%tmp_50 = arith.sitofp %tmp_48 : i32 to f32
		%tmp_51 = arith.divf %tmp_50, %tmp_49 : f32
		%tmp_52 = arith.fptosi %tmp_51 : f32 to i32
		// l_y__8_d1_4 aliased to tmp_52
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("y5: %i\n\00", %tmp_52) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_53 = arith.constant 0 : i32
		%tmp_54 = arith.subi %tmp_53, %tmp_52 : i32
		// l_y__8_d1_5 aliased to tmp_54
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("y6: %i\n\00", %tmp_54) : (i32)
		// Call Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<f32>, %tmp_36: f32)
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
		%l_t__10_d1_0 = fifo.pop(%In: !fifo.output_port<f32>) : f32
		// Input Pattern: End
		// Call Statement: Start
		fifo.print("Rx: %f\n\00", %l_t__10_d1_0) : (f32)
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

