//-- Definition of actor class: Source
cal.actor @source1 ()
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	%l_counter__5 = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_counter__5: !cal.state_ref<i32>, %tmp_1: i32)
	// Generation action: transmit
	cal.action "transmit" priority=0 {
		// Guard: Start
		cal.predicate {
			%tmp_2 = cal.get(%l_counter__5: !cal.state_ref<i32>) : i32
			// Evaluate global variable $eval2.
			%tmp_3 = arith.constant 3 : i2
			%tmp_4 = arith.extui %tmp_3 : i2 to i32
			// Evaluate global variable $eval2 done: assigned to tmp_4 above in this context.
			%tmp_5 = arith.cmpi slt, %tmp_2, %tmp_4 : i32
			cal.predicate_result %tmp_5 : i1
		}
		// Guard: End
		// Action Local Variable Decl: Start
		%tmp_6 = cal.get(%l_counter__5: !cal.state_ref<i32>) : i32
		%tmp_7 = arith.constant 100 : i7
		// Evaluate global variable $eval1.
		%tmp_8 = arith.constant 1 : i1
		%tmp_9 = arith.extui %tmp_8 : i1 to i32
		// Evaluate global variable $eval1 done: assigned to tmp_9 above in this context.
		%tmp_10 = arith.extui %tmp_7 : i7 to i32
		%tmp_11 = arith.muli %tmp_10, %tmp_9 : i32
		%tmp_12 = arith.addi %tmp_6, %tmp_11 : i32
		// l_t__6_d1_0 aliased to tmp_12
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		%tmp_13 = arith.constant 1 : i1
		%tmp_14 = arith.extui %tmp_13 : i1 to i32
		%tmp_15 = arith.addi %tmp_12, %tmp_14 : i32
		// l_t__6_d1_1 aliased to tmp_15
		// Assignment Statement: End
		fifo.print("Tx1: %i\n\00", %tmp_15) : (i32)
		// Assignment Statement: Start
		%tmp_16 = cal.get(%l_counter__5: !cal.state_ref<i32>) : i32
		%tmp_17 = arith.constant 1 : i1
		%tmp_18 = arith.extui %tmp_17 : i1 to i32
		%tmp_19 = arith.addi %tmp_16, %tmp_18 : i32
		cal.set(%l_counter__5: !cal.state_ref<i32>, %tmp_19: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_15: i32)
		// Output Expression: End
	}
}

//-- Definition of actor class: Source
cal.actor @source2 ()
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	%l_counter__7 = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_counter__7: !cal.state_ref<i32>, %tmp_1: i32)
	// Generation action: transmit
	cal.action "transmit" priority=0 {
		// Guard: Start
		cal.predicate {
			%tmp_2 = cal.get(%l_counter__7: !cal.state_ref<i32>) : i32
			// Evaluate global variable $eval2.
			%tmp_3 = arith.constant 3 : i2
			%tmp_4 = arith.extui %tmp_3 : i2 to i32
			// Evaluate global variable $eval2 done: assigned to tmp_4 above in this context.
			%tmp_5 = arith.cmpi slt, %tmp_2, %tmp_4 : i32
			cal.predicate_result %tmp_5 : i1
		}
		// Guard: End
		// Action Local Variable Decl: Start
		%tmp_6 = cal.get(%l_counter__7: !cal.state_ref<i32>) : i32
		%tmp_7 = arith.constant 100 : i7
		// Evaluate global variable $eval4.
		%tmp_8 = arith.constant 2 : i2
		%tmp_9 = arith.extui %tmp_8 : i2 to i32
		// Evaluate global variable $eval4 done: assigned to tmp_9 above in this context.
		%tmp_10 = arith.extui %tmp_7 : i7 to i32
		%tmp_11 = arith.muli %tmp_10, %tmp_9 : i32
		%tmp_12 = arith.addi %tmp_6, %tmp_11 : i32
		// l_t__8_d1_0 aliased to tmp_12
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		%tmp_13 = arith.constant 1 : i1
		%tmp_14 = arith.extui %tmp_13 : i1 to i32
		%tmp_15 = arith.addi %tmp_12, %tmp_14 : i32
		// l_t__8_d1_1 aliased to tmp_15
		// Assignment Statement: End
		fifo.print("Tx2: %i\n\00", %tmp_15) : (i32)
		// Assignment Statement: Start
		%tmp_16 = cal.get(%l_counter__7: !cal.state_ref<i32>) : i32
		%tmp_17 = arith.constant 1 : i1
		%tmp_18 = arith.extui %tmp_17 : i1 to i32
		%tmp_19 = arith.addi %tmp_16, %tmp_18 : i32
		cal.set(%l_counter__7: !cal.state_ref<i32>, %tmp_19: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_15: i32)
		// Output Expression: End
	}
}

//-- Definition of actor class: RepeatInput
cal.actor @pass ()
	ports_in(%In1: !fifo.output_port<i32>,%In2: !fifo.output_port<i32>)
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	// Generation action: $untagged0
	cal.action "$untagged0" priority=0 {
		// Input Pattern: Start
		%l_t1__10_d1_0 = memref.alloca() : memref<2xi32>
		%tmp_0 = arith.constant 0 : index
		%tmp_1 = fifo.pop(%In1: !fifo.output_port<i32>) : i32
		memref.store %tmp_1, %l_t1__10_d1_0[%tmp_0] : memref<2xi32>
		%tmp_2 = arith.constant 1 : index
		%tmp_3 = fifo.pop(%In1: !fifo.output_port<i32>) : i32
		memref.store %tmp_3, %l_t1__10_d1_0[%tmp_2] : memref<2xi32>
		// Input Pattern: End
		// Input Pattern: Start
		%l_t2__13_d1_0 = memref.alloca() : memref<2xi32>
		%tmp_4 = arith.constant 0 : index
		%tmp_5 = fifo.pop(%In2: !fifo.output_port<i32>) : i32
		memref.store %tmp_5, %l_t2__13_d1_0[%tmp_4] : memref<2xi32>
		%tmp_6 = arith.constant 1 : index
		%tmp_7 = fifo.pop(%In2: !fifo.output_port<i32>) : i32
		memref.store %tmp_7, %l_t2__13_d1_0[%tmp_6] : memref<2xi32>
		// Input Pattern: End
		// Action Local Variable Decl: Start
		%tmp_8 = arith.constant 1 : i1
		%tmp_9 = arith.extui %tmp_8 : i1 to i32
		// l_b__16_d1_0 aliased to tmp_9
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%l_a__15_d1_0 = arith.constant 0 : i32
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		%tmp_10 = arith.constant 0 : i1
		%tmp_11 = arith.extui %tmp_10 : i1 to i32
		%tmp_12 = arith.index_cast %tmp_11: i32 to index
		%tmp_13 = memref.load %l_t1__10_d1_0[%tmp_12] : memref<2xi32>
		%tmp_14 = arith.constant 1 : i1
		%tmp_15 = arith.extui %tmp_14 : i1 to i32
		%tmp_16 = arith.index_cast %tmp_15: i32 to index
		%tmp_17 = memref.load %l_t1__10_d1_0[%tmp_16] : memref<2xi32>
		%tmp_18 = arith.addi %tmp_13, %tmp_17 : i32
		%tmp_19 = arith.constant 0 : i1
		%tmp_20 = arith.extui %tmp_19 : i1 to i32
		%tmp_21 = arith.index_cast %tmp_20: i32 to index
		%tmp_22 = memref.load %l_t2__13_d1_0[%tmp_21] : memref<2xi32>
		%tmp_23 = arith.addi %tmp_18, %tmp_22 : i32
		%tmp_24 = arith.constant 1 : i1
		%tmp_25 = arith.extui %tmp_24 : i1 to i32
		%tmp_26 = arith.index_cast %tmp_25: i32 to index
		%tmp_27 = memref.load %l_t2__13_d1_0[%tmp_26] : memref<2xi32>
		%tmp_28 = arith.addi %tmp_23, %tmp_27 : i32
		%tmp_29 = arith.addi %tmp_28, %tmp_9 : i32
		// l_a__15_d1_1 aliased to tmp_29
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_29: i32)
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
		%l_t__18_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		fifo.print("Rx: %i\n\00", %l_t__18_d1_0) : (i32)
	}
}

// -- Top Network: Defines structure of actor application
cal.network
{

	// -- Instantiate channels between actors
	%queue_from_source2_Out, %queue_to_pass_In2 = fifo.create<i32>(2) : !fifo.input_port<i32>, !fifo.output_port<i32>
	%queue_from_source1_Out, %queue_to_pass_In1 = fifo.create<i32>(2) : !fifo.input_port<i32>, !fifo.output_port<i32>
	%queue_from_pass_Out, %queue_to_sink_In = fifo.create<i32>(1) : !fifo.input_port<i32>, !fifo.output_port<i32>

	// -- Instantiate actors (also known as nodes/instances)
	cal.create_instance @source1 "source1" ()
		ports_out(%queue_from_source1_Out: !fifo.input_port<i32>)
	cal.create_instance @source2 "source2" ()
		ports_out(%queue_from_source2_Out: !fifo.input_port<i32>)
	cal.create_instance @pass "pass" ()
		ports_in(%queue_to_pass_In1, %queue_to_pass_In2: !fifo.output_port<i32>, !fifo.output_port<i32>)
		ports_out(%queue_from_pass_Out: !fifo.input_port<i32>)
	cal.create_instance @sink "sink" ()
		ports_in(%queue_to_sink_In: !fifo.output_port<i32>)

}

