//-- Definition of actor class: Source
cal.actor @source ()
	ports_out(%Out: !fifo.input_port<f32>)
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
		%tmp_18 = arith.sitofp %tmp_11 : i32 to f32
		fifo.push(%Out: !fifo.input_port<f32>, %tmp_18: f32)
		// Output Expression: End
	}
}

//-- Definition of actor class: BinaryExprFpAndIntMixed
cal.actor @DUT ()
	ports_in(%In: !fifo.output_port<f32>)
	ports_out(%Out: !fifo.input_port<f32>)
{
	// -- Actor body
	%l_$state = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_$state: !cal.state_ref<i32>, %tmp_1: i32)
	// Generation action: $untagged0
	cal.action "$untagged0" priority=0 {
		// Input Pattern: Start
		%l_t__5_d1_0 = fifo.pop(%In: !fifo.output_port<f32>) : f32
		// Input Pattern: End
		// Action Local Variable Decl: Start
		%l_x__7_d1_0 = arith.constant 0.0 : f32
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_2 = arith.constant 25 : i5
		%tmp_3 = arith.constant 25.0 : f32
		%tmp_4 = arith.uitofp %tmp_2 : i5 to f32
		%tmp_5 = arith.addf %tmp_4, %tmp_3 : f32
		%tmp_6 = arith.constant 25 : i5
		%tmp_7 = arith.uitofp %tmp_6 : i5 to f32
		%tmp_8 = arith.addf %tmp_5, %tmp_7 : f32
		%tmp_9 = arith.constant 25.0 : f32
		%tmp_10 = arith.addf %tmp_8, %tmp_9 : f32
		%tmp_11 = arith.constant 0.5 : f32
		%tmp_12 = arith.addf %tmp_10, %tmp_11 : f32
		%tmp_13 = arith.fptosi %tmp_12 : f32 to i32
		// l_y__8_d1_0 aliased to tmp_13
		// Action Local Variable Decl: End
		// Call Statement: Start
		fifo.print("t: %f\n\00", %l_t__5_d1_0) : (f32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_14 = arith.constant 25 : i5
		%tmp_15 = arith.constant 25.0 : f32
		%tmp_16 = arith.uitofp %tmp_14 : i5 to f32
		%tmp_17 = arith.addf %tmp_16, %tmp_15 : f32
		%tmp_18 = arith.constant 25 : i5
		%tmp_19 = arith.uitofp %tmp_18 : i5 to f32
		%tmp_20 = arith.addf %tmp_17, %tmp_19 : f32
		%tmp_21 = arith.constant 25.0 : f32
		%tmp_22 = arith.addf %tmp_20, %tmp_21 : f32
		%tmp_23 = arith.constant 0.5 : f32
		%tmp_24 = arith.addf %tmp_22, %tmp_23 : f32
		// l_x__7_d1_1 aliased to tmp_24
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("x1: %f\n\00", %tmp_24) : (f32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_25 = arith.constant 1 : i1
		%tmp_26 = arith.uitofp %tmp_25 : i1 to f32
		%tmp_27 = arith.addf %tmp_24, %tmp_26 : f32
		// l_x__7_d1_2 aliased to tmp_27
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("x2: %f\n\00", %tmp_27) : (f32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_28 = arith.constant 2 : i2
		%tmp_29 = arith.uitofp %tmp_28 : i2 to f32
		%tmp_30 = arith.subf %tmp_27, %tmp_29 : f32
		// l_x__7_d1_3 aliased to tmp_30
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("x3: %f\n\00", %tmp_30) : (f32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_31 = arith.constant 10 : i4
		%tmp_32 = arith.uitofp %tmp_31 : i4 to f32
		%tmp_33 = arith.mulf %tmp_32, %tmp_30 : f32
		// l_x__7_d1_4 aliased to tmp_33
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("x4: %f\n\00", %tmp_33) : (f32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_34 = arith.constant 2 : i2
		%tmp_35 = arith.uitofp %tmp_34 : i2 to f32
		%tmp_36 = arith.divf %tmp_33, %tmp_35 : f32
		// l_x__7_d1_5 aliased to tmp_36
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("x5: %f\n\00", %tmp_36) : (f32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_37 = arith.constant 0.0 : f32
		%tmp_38 = arith.subf %tmp_37, %tmp_36 : f32
		// l_x__7_d1_6 aliased to tmp_38
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("x6: %f\n\00", %tmp_38) : (f32)
		// Call Statement: End
		// Call Statement: Start
		fifo.print("y1: %i\n\00", %tmp_13) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_39 = arith.constant 1.5 : f32
		%tmp_40 = arith.sitofp %tmp_13 : i32 to f32
		%tmp_41 = arith.addf %tmp_40, %tmp_39 : f32
		%tmp_42 = arith.fptosi %tmp_41 : f32 to i32
		// l_y__8_d1_1 aliased to tmp_42
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("y2: %i\n\00", %tmp_42) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_43 = arith.constant 2.5 : f32
		%tmp_44 = arith.sitofp %tmp_42 : i32 to f32
		%tmp_45 = arith.subf %tmp_44, %tmp_43 : f32
		%tmp_46 = arith.fptosi %tmp_45 : f32 to i32
		// l_y__8_d1_2 aliased to tmp_46
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("y3: %i\n\00", %tmp_46) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_47 = arith.constant 10.5 : f32
		%tmp_48 = arith.sitofp %tmp_46 : i32 to f32
		%tmp_49 = arith.mulf %tmp_48, %tmp_47 : f32
		%tmp_50 = arith.fptosi %tmp_49 : f32 to i32
		// l_y__8_d1_3 aliased to tmp_50
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("y4: %i\n\00", %tmp_50) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_51 = arith.constant 2.5 : f32
		%tmp_52 = arith.sitofp %tmp_50 : i32 to f32
		%tmp_53 = arith.divf %tmp_52, %tmp_51 : f32
		%tmp_54 = arith.fptosi %tmp_53 : f32 to i32
		// l_y__8_d1_4 aliased to tmp_54
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("y5: %i\n\00", %tmp_54) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_55 = arith.constant 0 : i32
		%tmp_56 = arith.subi %tmp_55, %tmp_54 : i32
		// l_y__8_d1_5 aliased to tmp_56
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("y6: %i\n\00", %tmp_56) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_57 = arith.constant 0 : i1
		%tmp_58 = arith.extui %tmp_57 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_58: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<f32>, %tmp_38: f32)
		// Output Expression: End
	}
}

//-- Definition of actor class: Sink
cal.actor @sink ()
	ports_in(%In: !fifo.output_port<f32>)
{
	// -- Actor body
	%l_$state = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_$state: !cal.state_ref<i32>, %tmp_1: i32)
	// Generation action: $untagged0
	cal.action "$untagged0" priority=0 {
		// Input Pattern: Start
		%l_t__10_d1_0 = fifo.pop(%In: !fifo.output_port<f32>) : f32
		// Input Pattern: End
		// Call Statement: Start
		fifo.print("Rx: %f\n\00", %l_t__10_d1_0) : (f32)
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

