//-- Definition of actor class: Source
cal.actor @source ()
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	%l_counter__4 = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_counter__4: !cal.state_ref<i32>, %tmp_1: i32)
	%l_$state = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_2 = arith.constant 0 : i1
	%tmp_3 = arith.extui %tmp_2 : i1 to i32
	cal.set(%l_$state: !cal.state_ref<i32>, %tmp_3: i32)
	// Generation action: transmit
	cal.action "transmit" priority=0 {
		// Guard: Start
		cal.predicate {
			%tmp_4 = cal.get(%l_counter__4: !cal.state_ref<i32>) : i32
			// Evaluate global variable $eval2.
			%tmp_5 = arith.constant 3 : i2
			%tmp_6 = arith.extui %tmp_5 : i2 to i32
			// Evaluate global variable $eval2 done: assigned to tmp_6 above in this context.
			%tmp_7 = arith.cmpi slt, %tmp_4, %tmp_6 : i32
			cal.predicate_result %tmp_7 : i1
		}
		// Guard: End
		// Action Local Variable Decl: Start
		%tmp_8 = cal.get(%l_counter__4: !cal.state_ref<i32>) : i32
		%tmp_9 = arith.constant 100 : i7
		// Evaluate global variable $eval1.
		%tmp_10 = arith.constant 0 : i1
		%tmp_11 = arith.extui %tmp_10 : i1 to i32
		// Evaluate global variable $eval1 done: assigned to tmp_11 above in this context.
		%tmp_12 = arith.extui %tmp_9 : i7 to i32
		%tmp_13 = arith.muli %tmp_12, %tmp_11 : i32
		%tmp_14 = arith.addi %tmp_8, %tmp_13 : i32
		// l_t__5_d1_0 aliased to tmp_14
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		%tmp_15 = arith.constant 1 : i1
		%tmp_16 = arith.extui %tmp_15 : i1 to i32
		%tmp_17 = arith.addi %tmp_14, %tmp_16 : i32
		// l_t__5_d1_1 aliased to tmp_17
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("Tx0: %i\n\00", %tmp_17) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_18 = cal.get(%l_counter__4: !cal.state_ref<i32>) : i32
		%tmp_19 = arith.constant 1 : i1
		%tmp_20 = arith.extui %tmp_19 : i1 to i32
		%tmp_21 = arith.addi %tmp_18, %tmp_20 : i32
		cal.set(%l_counter__4: !cal.state_ref<i32>, %tmp_21: i32)
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

//-- Definition of actor class: ListLiterals
cal.actor @DUT ()
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
		%l_t1__10_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Action Local Variable Decl: Start
		%tmp_2 = memref.alloc() : memref<4xi32>
		%tmp_3 = arith.constant 0: index
		%tmp_4 = arith.constant 100 : i7
		%tmp_5 = arith.extui %tmp_4 : i7 to i32
		memref.store %tmp_5, %tmp_2[%tmp_3] : memref<4xi32>
		%tmp_6 = arith.constant 1: index
		%tmp_7 = arith.constant 100 : i7
		%tmp_8 = arith.extui %tmp_7 : i7 to i32
		memref.store %tmp_8, %tmp_2[%tmp_6] : memref<4xi32>
		%tmp_9 = arith.constant 2: index
		%tmp_10 = arith.constant 100 : i7
		%tmp_11 = arith.extui %tmp_10 : i7 to i32
		memref.store %tmp_11, %tmp_2[%tmp_9] : memref<4xi32>
		%tmp_12 = arith.constant 3: index
		%tmp_13 = arith.constant 100 : i7
		%tmp_14 = arith.extui %tmp_13 : i7 to i32
		memref.store %tmp_14, %tmp_2[%tmp_12] : memref<4xi32>
		// l_list__12_d1_0 aliased to tmp_2
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		%tmp_15 = memref.alloc() : memref<4xi32>
		%tmp_16 = arith.constant 0: index
		%tmp_17 = arith.constant 100 : i7
		%tmp_18 = arith.extui %tmp_17 : i7 to i32
		%tmp_19 = arith.addi %tmp_18, %l_t1__10_d1_0 : i32
		memref.store %tmp_19, %tmp_15[%tmp_16] : memref<4xi32>
		%tmp_20 = arith.constant 1: index
		%tmp_21 = arith.constant 200 : i8
		%tmp_22 = arith.extui %tmp_21 : i8 to i32
		%tmp_23 = arith.addi %tmp_22, %l_t1__10_d1_0 : i32
		memref.store %tmp_23, %tmp_15[%tmp_20] : memref<4xi32>
		%tmp_24 = arith.constant 2: index
		%tmp_25 = arith.constant 300 : i9
		%tmp_26 = arith.extui %tmp_25 : i9 to i32
		%tmp_27 = arith.addi %tmp_26, %l_t1__10_d1_0 : i32
		memref.store %tmp_27, %tmp_15[%tmp_24] : memref<4xi32>
		%tmp_28 = arith.constant 3: index
		%tmp_29 = arith.constant 400 : i9
		%tmp_30 = arith.extui %tmp_29 : i9 to i32
		%tmp_31 = arith.addi %tmp_30, %l_t1__10_d1_0 : i32
		memref.store %tmp_31, %tmp_15[%tmp_28] : memref<4xi32>
		// l_list__12_d1_1 aliased to tmp_15
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_32 = arith.constant 0 : i1
		%tmp_33 = arith.extui %tmp_32 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_33: i32)
		// Assignment Statement: End
		// Output Expression: Start
		%tmp_34 = arith.constant 0 : index
		%tmp_35 = memref.load %tmp_15[%tmp_34] : memref<4xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_35: i32)
		%tmp_36 = arith.constant 1 : index
		%tmp_37 = memref.load %tmp_15[%tmp_36] : memref<4xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_37: i32)
		%tmp_38 = arith.constant 2 : index
		%tmp_39 = memref.load %tmp_15[%tmp_38] : memref<4xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_39: i32)
		%tmp_40 = arith.constant 3 : index
		%tmp_41 = memref.load %tmp_15[%tmp_40] : memref<4xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_41: i32)
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
		%l_t__7_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Call Statement: Start
		fifo.print("Rx0: %i\n\00", %l_t__7_d1_0) : (i32)
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

