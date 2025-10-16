//-- Definition of actor class: Source
cal.actor @source1 ()
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	%l_counter__5 = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_counter__5: !cal.state_ref<i32>, %tmp_1: i32)
	%l_$state = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_2 = arith.constant 0 : i1
	%tmp_3 = arith.extui %tmp_2 : i1 to i32
	cal.set(%l_$state: !cal.state_ref<i32>, %tmp_3: i32)
	// Generation action: transmit
	cal.action "transmit" priority=0 {
		// Guard: Start
		cal.predicate {
			%tmp_4 = cal.get(%l_counter__5: !cal.state_ref<i32>) : i32
			// Evaluate global variable $eval2.
			%tmp_5 = arith.constant 3 : i2
			%tmp_6 = arith.extui %tmp_5 : i2 to i32
			// Evaluate global variable $eval2 done: assigned to tmp_6 above in this context.
			%tmp_7 = arith.cmpi slt, %tmp_4, %tmp_6 : i32
			cal.predicate_result %tmp_7 : i1
		}
		// Guard: End
		// Action Local Variable Decl: Start
		%tmp_8 = cal.get(%l_counter__5: !cal.state_ref<i32>) : i32
		%tmp_9 = arith.constant 100 : i7
		// Evaluate global variable $eval1.
		%tmp_10 = arith.constant 1 : i1
		%tmp_11 = arith.extui %tmp_10 : i1 to i32
		// Evaluate global variable $eval1 done: assigned to tmp_11 above in this context.
		%tmp_12 = arith.extui %tmp_9 : i7 to i32
		%tmp_13 = arith.muli %tmp_12, %tmp_11 : i32
		%tmp_14 = arith.addi %tmp_8, %tmp_13 : i32
		// l_t__6_d1_0 aliased to tmp_14
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		%tmp_15 = arith.constant 1 : i1
		%tmp_16 = arith.extui %tmp_15 : i1 to i32
		%tmp_17 = arith.addi %tmp_14, %tmp_16 : i32
		// l_t__6_d1_1 aliased to tmp_17
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("Tx1: %i\n\00", %tmp_17) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_18 = cal.get(%l_counter__5: !cal.state_ref<i32>) : i32
		%tmp_19 = arith.constant 1 : i1
		%tmp_20 = arith.extui %tmp_19 : i1 to i32
		%tmp_21 = arith.addi %tmp_18, %tmp_20 : i32
		cal.set(%l_counter__5: !cal.state_ref<i32>, %tmp_21: i32)
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_22 = arith.constant 0 : i1
		%tmp_23 = arith.extui %tmp_22 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_23: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_17: i32)
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
	%l_$state = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_2 = arith.constant 0 : i1
	%tmp_3 = arith.extui %tmp_2 : i1 to i32
	cal.set(%l_$state: !cal.state_ref<i32>, %tmp_3: i32)
	// Generation action: transmit
	cal.action "transmit" priority=0 {
		// Guard: Start
		cal.predicate {
			%tmp_4 = cal.get(%l_counter__7: !cal.state_ref<i32>) : i32
			// Evaluate global variable $eval2.
			%tmp_5 = arith.constant 3 : i2
			%tmp_6 = arith.extui %tmp_5 : i2 to i32
			// Evaluate global variable $eval2 done: assigned to tmp_6 above in this context.
			%tmp_7 = arith.cmpi slt, %tmp_4, %tmp_6 : i32
			cal.predicate_result %tmp_7 : i1
		}
		// Guard: End
		// Action Local Variable Decl: Start
		%tmp_8 = cal.get(%l_counter__7: !cal.state_ref<i32>) : i32
		%tmp_9 = arith.constant 100 : i7
		// Evaluate global variable $eval4.
		%tmp_10 = arith.constant 2 : i2
		%tmp_11 = arith.extui %tmp_10 : i2 to i32
		// Evaluate global variable $eval4 done: assigned to tmp_11 above in this context.
		%tmp_12 = arith.extui %tmp_9 : i7 to i32
		%tmp_13 = arith.muli %tmp_12, %tmp_11 : i32
		%tmp_14 = arith.addi %tmp_8, %tmp_13 : i32
		// l_t__8_d1_0 aliased to tmp_14
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		%tmp_15 = arith.constant 1 : i1
		%tmp_16 = arith.extui %tmp_15 : i1 to i32
		%tmp_17 = arith.addi %tmp_14, %tmp_16 : i32
		// l_t__8_d1_1 aliased to tmp_17
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("Tx2: %i\n\00", %tmp_17) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_18 = cal.get(%l_counter__7: !cal.state_ref<i32>) : i32
		%tmp_19 = arith.constant 1 : i1
		%tmp_20 = arith.extui %tmp_19 : i1 to i32
		%tmp_21 = arith.addi %tmp_18, %tmp_20 : i32
		cal.set(%l_counter__7: !cal.state_ref<i32>, %tmp_21: i32)
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_22 = arith.constant 0 : i1
		%tmp_23 = arith.extui %tmp_22 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_23: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_17: i32)
		// Output Expression: End
	}
}

//-- Definition of actor class: RepeatInput
cal.actor @pass ()
	ports_in(%In1: !fifo.output_port<i32>,%In2: !fifo.output_port<i32>)
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
		%l_t1__10_d1_0 = memref.alloca() : memref<2xi32>
		%tmp_2 = arith.constant 0 : index
		%tmp_3 = fifo.pop(%In1: !fifo.output_port<i32>) : i32
		memref.store %tmp_3, %l_t1__10_d1_0[%tmp_2] : memref<2xi32>
		%tmp_4 = arith.constant 1 : index
		%tmp_5 = fifo.pop(%In1: !fifo.output_port<i32>) : i32
		memref.store %tmp_5, %l_t1__10_d1_0[%tmp_4] : memref<2xi32>
		// Input Pattern: End
		// Input Pattern: Start
		%l_t2__13_d1_0 = memref.alloca() : memref<2xi32>
		%tmp_6 = arith.constant 0 : index
		%tmp_7 = fifo.pop(%In2: !fifo.output_port<i32>) : i32
		memref.store %tmp_7, %l_t2__13_d1_0[%tmp_6] : memref<2xi32>
		%tmp_8 = arith.constant 1 : index
		%tmp_9 = fifo.pop(%In2: !fifo.output_port<i32>) : i32
		memref.store %tmp_9, %l_t2__13_d1_0[%tmp_8] : memref<2xi32>
		// Input Pattern: End
		// Action Local Variable Decl: Start
		%tmp_10 = arith.constant 1 : i1
		%tmp_11 = arith.extui %tmp_10 : i1 to i32
		// l_b__16_d1_0 aliased to tmp_11
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%l_a__15_d1_0 = arith.constant 0 : i32
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		%tmp_12 = arith.constant 0 : i1
		%tmp_13 = arith.extui %tmp_12 : i1 to i32
		%tmp_14 = arith.index_cast %tmp_13: i32 to index
		%tmp_15 = memref.load %l_t1__10_d1_0[%tmp_14] : memref<2xi32>
		%tmp_16 = arith.constant 1 : i1
		%tmp_17 = arith.extui %tmp_16 : i1 to i32
		%tmp_18 = arith.index_cast %tmp_17: i32 to index
		%tmp_19 = memref.load %l_t1__10_d1_0[%tmp_18] : memref<2xi32>
		%tmp_20 = arith.addi %tmp_15, %tmp_19 : i32
		%tmp_21 = arith.constant 0 : i1
		%tmp_22 = arith.extui %tmp_21 : i1 to i32
		%tmp_23 = arith.index_cast %tmp_22: i32 to index
		%tmp_24 = memref.load %l_t2__13_d1_0[%tmp_23] : memref<2xi32>
		%tmp_25 = arith.addi %tmp_20, %tmp_24 : i32
		%tmp_26 = arith.constant 1 : i1
		%tmp_27 = arith.extui %tmp_26 : i1 to i32
		%tmp_28 = arith.index_cast %tmp_27: i32 to index
		%tmp_29 = memref.load %l_t2__13_d1_0[%tmp_28] : memref<2xi32>
		%tmp_30 = arith.addi %tmp_25, %tmp_29 : i32
		%tmp_31 = arith.addi %tmp_30, %tmp_11 : i32
		// l_a__15_d1_1 aliased to tmp_31
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_32 = arith.constant 0 : i1
		%tmp_33 = arith.extui %tmp_32 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_33: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_31: i32)
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
		%l_t__18_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Call Statement: Start
		fifo.print("Rx: %i\n\00", %l_t__18_d1_0) : (i32)
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

