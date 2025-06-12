//-- Definition of actor class: Source
cal.actor @source ()
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	%l_counter__3 = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_counter__3: !cal.state_ref<i32>, %tmp_1: i32)
	// Generation action: transmit
	cal.action "transmit" priority=0 {
		// Guard: Start
		cal.predicate {
			%tmp_2 = cal.get(%l_counter__3: !cal.state_ref<i32>) : i32
			// Evaluate global variable $eval2.
			%tmp_3 = arith.constant 3 : i2
			%tmp_4 = arith.extui %tmp_3 : i2 to i32
			// Evaluate global variable $eval2 done: assigned to tmp_4 above in this context.
			%tmp_5 = arith.cmpi slt, %tmp_2, %tmp_4 : i32
			cal.predicate_result %tmp_5 : i1
		}
		// Guard: End
		// Action Local Variable Decl: Start
		%tmp_6 = cal.get(%l_counter__3: !cal.state_ref<i32>) : i32
		%tmp_7 = arith.constant 100 : i7
		// Evaluate global variable $eval1.
		%tmp_8 = arith.constant 0 : i1
		%tmp_9 = arith.extui %tmp_8 : i1 to i32
		// Evaluate global variable $eval1 done: assigned to tmp_9 above in this context.
		%tmp_10 = arith.extui %tmp_7 : i7 to i32
		%tmp_11 = arith.muli %tmp_10, %tmp_9 : i32
		%tmp_12 = arith.addi %tmp_6, %tmp_11 : i32
		// l_t__4_d1_0 aliased to tmp_12
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		%tmp_13 = arith.constant 1 : i1
		%tmp_14 = arith.extui %tmp_13 : i1 to i32
		%tmp_15 = arith.addi %tmp_12, %tmp_14 : i32
		// l_t__4_d1_1 aliased to tmp_15
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("Tx0: %i\n\00", %tmp_15) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_16 = cal.get(%l_counter__3: !cal.state_ref<i32>) : i32
		%tmp_17 = arith.constant 1 : i1
		%tmp_18 = arith.extui %tmp_17 : i1 to i32
		%tmp_19 = arith.addi %tmp_16, %tmp_18 : i32
		cal.set(%l_counter__3: !cal.state_ref<i32>, %tmp_19: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_15: i32)
		// Output Expression: End
	}
}

//-- Definition of actor class: RepeatOutput
cal.actor @DUT ()
	ports_in(%In: !fifo.output_port<i32>)
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	// Generation action: $untagged0
	cal.action "$untagged0" priority=0 {
		// Input Pattern: Start
		%l_t__6_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Action Local Variable Decl: Start
		%l_list__8_d1_0 = memref.alloc() : memref<4xi32>
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		%tmp_0 = arith.constant 0 : i1
		%tmp_1 = arith.extui %tmp_0 : i1 to i32
		%tmp_2 = arith.index_cast %tmp_1: i32 to index
		%tmp_3 = arith.constant 40 : i6
		%tmp_4 = arith.extui %tmp_3 : i6 to i32
		%tmp_5 = arith.addi %tmp_4, %l_t__6_d1_0 : i32
		memref.store %tmp_5, %l_list__8_d1_0[%tmp_2] : memref<4xi32>
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_6 = arith.constant 1 : i1
		%tmp_7 = arith.extui %tmp_6 : i1 to i32
		%tmp_8 = arith.index_cast %tmp_7: i32 to index
		%tmp_9 = arith.constant 30 : i5
		%tmp_10 = arith.extui %tmp_9 : i5 to i32
		%tmp_11 = arith.addi %tmp_10, %l_t__6_d1_0 : i32
		memref.store %tmp_11, %l_list__8_d1_0[%tmp_8] : memref<4xi32>
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_12 = arith.constant 2 : i2
		%tmp_13 = arith.extui %tmp_12 : i2 to i32
		%tmp_14 = arith.index_cast %tmp_13: i32 to index
		%tmp_15 = arith.constant 20 : i5
		%tmp_16 = arith.extui %tmp_15 : i5 to i32
		%tmp_17 = arith.addi %tmp_16, %l_t__6_d1_0 : i32
		memref.store %tmp_17, %l_list__8_d1_0[%tmp_14] : memref<4xi32>
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_18 = arith.constant 3 : i2
		%tmp_19 = arith.extui %tmp_18 : i2 to i32
		%tmp_20 = arith.index_cast %tmp_19: i32 to index
		%tmp_21 = arith.constant 10 : i4
		%tmp_22 = arith.extui %tmp_21 : i4 to i32
		%tmp_23 = arith.addi %tmp_22, %l_t__6_d1_0 : i32
		memref.store %tmp_23, %l_list__8_d1_0[%tmp_20] : memref<4xi32>
		// Assignment Statement: End
		// Output Expression: Start
		%tmp_24 = arith.constant 0 : index
		%tmp_25 = memref.load %l_list__8_d1_0[%tmp_24] : memref<4xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_25: i32)
		%tmp_26 = arith.constant 1 : index
		%tmp_27 = memref.load %l_list__8_d1_0[%tmp_26] : memref<4xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_27: i32)
		%tmp_28 = arith.constant 2 : index
		%tmp_29 = memref.load %l_list__8_d1_0[%tmp_28] : memref<4xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_29: i32)
		%tmp_30 = arith.constant 3 : index
		%tmp_31 = memref.load %l_list__8_d1_0[%tmp_30] : memref<4xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_31: i32)
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
		%l_t__10_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Call Statement: Start
		fifo.print("Rx: %i\n\00", %l_t__10_d1_0) : (i32)
		// Call Statement: End
	}
}

// -- Top Network: Defines structure of actor application
cal.network
{

	// -- Instantiate channels between actors
	%queue_from_source_Out, %queue_to_DUT_In = fifo.create<i32>(1) : !fifo.input_port<i32>, !fifo.output_port<i32>
	%queue_from_DUT_Out, %queue_to_sink_In = fifo.create<i32>(4) : !fifo.input_port<i32>, !fifo.output_port<i32>

	// -- Instantiate actors (also known as nodes/instances)
	cal.create_instance @source "source" ()
		ports_out(%queue_from_source_Out: !fifo.input_port<i32>)
	cal.create_instance @DUT "DUT" ()
		ports_in(%queue_to_DUT_In: !fifo.output_port<i32>)
		ports_out(%queue_from_DUT_Out: !fifo.input_port<i32>)
	cal.create_instance @sink "sink" ()
		ports_in(%queue_to_sink_In: !fifo.output_port<i32>)

}

