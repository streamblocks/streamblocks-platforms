//-- Definition of actor class: Source
cal.actor @source ()
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
		// Evaluate global variable $eval1.
		%tmp_8 = arith.constant 0 : i1
		%tmp_9 = arith.extui %tmp_8 : i1 to i32
		// Evaluate global variable $eval1 done: assigned to tmp_9 above in this context.
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
		// Call Statement: Start
		fifo.print("Tx0: %i\n\00", %tmp_15) : (i32)
		// Call Statement: End
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

//-- Definition of actor class: ListLiterals
cal.actor @DUT ()
	ports_in(%In: !fifo.output_port<i32>)
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	// Generation action: $untagged0
	cal.action "$untagged0" priority=0 {
		// Input Pattern: Start
		%l_t1__10_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Action Local Variable Decl: Start
		%tmp_0 = memref.alloc() : memref<4xi32>
		%tmp_1 = arith.constant 0: index
		%tmp_2 = arith.constant 100 : i7
		%tmp_3 = arith.extui %tmp_2 : i7 to i32
		memref.store %tmp_3, %tmp_0[%tmp_1] : memref<4xi32>
		%tmp_4 = arith.constant 1: index
		%tmp_5 = arith.constant 100 : i7
		%tmp_6 = arith.extui %tmp_5 : i7 to i32
		memref.store %tmp_6, %tmp_0[%tmp_4] : memref<4xi32>
		%tmp_7 = arith.constant 2: index
		%tmp_8 = arith.constant 100 : i7
		%tmp_9 = arith.extui %tmp_8 : i7 to i32
		memref.store %tmp_9, %tmp_0[%tmp_7] : memref<4xi32>
		%tmp_10 = arith.constant 3: index
		%tmp_11 = arith.constant 100 : i7
		%tmp_12 = arith.extui %tmp_11 : i7 to i32
		memref.store %tmp_12, %tmp_0[%tmp_10] : memref<4xi32>
		// l_list__12_d1_0 aliased to tmp_0
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		%tmp_13 = memref.alloc() : memref<4xi32>
		%tmp_14 = arith.constant 0: index
		%tmp_15 = arith.constant 100 : i7
		%tmp_16 = arith.extui %tmp_15 : i7 to i32
		%tmp_17 = arith.addi %tmp_16, %l_t1__10_d1_0 : i32
		memref.store %tmp_17, %tmp_13[%tmp_14] : memref<4xi32>
		%tmp_18 = arith.constant 1: index
		%tmp_19 = arith.constant 200 : i8
		%tmp_20 = arith.extui %tmp_19 : i8 to i32
		%tmp_21 = arith.addi %tmp_20, %l_t1__10_d1_0 : i32
		memref.store %tmp_21, %tmp_13[%tmp_18] : memref<4xi32>
		%tmp_22 = arith.constant 2: index
		%tmp_23 = arith.constant 300 : i9
		%tmp_24 = arith.extui %tmp_23 : i9 to i32
		%tmp_25 = arith.addi %tmp_24, %l_t1__10_d1_0 : i32
		memref.store %tmp_25, %tmp_13[%tmp_22] : memref<4xi32>
		%tmp_26 = arith.constant 3: index
		%tmp_27 = arith.constant 400 : i9
		%tmp_28 = arith.extui %tmp_27 : i9 to i32
		%tmp_29 = arith.addi %tmp_28, %l_t1__10_d1_0 : i32
		memref.store %tmp_29, %tmp_13[%tmp_26] : memref<4xi32>
		// l_list__12_d1_1 aliased to tmp_13
		// Assignment Statement: End
		// Output Expression: Start
		%tmp_30 = arith.constant 0 : index
		%tmp_31 = memref.load %tmp_13[%tmp_30] : memref<4xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_31: i32)
		%tmp_32 = arith.constant 1 : index
		%tmp_33 = memref.load %tmp_13[%tmp_32] : memref<4xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_33: i32)
		%tmp_34 = arith.constant 2 : index
		%tmp_35 = memref.load %tmp_13[%tmp_34] : memref<4xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_35: i32)
		%tmp_36 = arith.constant 3 : index
		%tmp_37 = memref.load %tmp_13[%tmp_36] : memref<4xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_37: i32)
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
		%l_t__5_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Call Statement: Start
		fifo.print("Rx0: %i\n\00", %l_t__5_d1_0) : (i32)
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

