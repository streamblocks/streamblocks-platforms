//-- Definition of actor class: Source
cal.actor @source ()
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	%l_counter__3 = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_counter__3: !cal.state_ref<i32>, %tmp_1: i32)
	%l_$state = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_2 = arith.constant 0 : i1
	%tmp_3 = arith.extui %tmp_2 : i1 to i32
	cal.set(%l_$state: !cal.state_ref<i32>, %tmp_3: i32)
	// Generation action: transmit
	cal.action "transmit" priority=0 {
		// Guard: Start
		cal.predicate {
			%tmp_4 = cal.get(%l_counter__3: !cal.state_ref<i32>) : i32
			// Evaluate global variable $eval2.
			%tmp_5 = arith.constant 3 : i2
			%tmp_6 = arith.extui %tmp_5 : i2 to i32
			// Evaluate global variable $eval2 done: assigned to tmp_6 above in this context.
			%tmp_7 = arith.cmpi slt, %tmp_4, %tmp_6 : i32
			cal.predicate_result %tmp_7 : i1
		}
		// Guard: End
		// Action Local Variable Decl: Start
		%tmp_8 = cal.get(%l_counter__3: !cal.state_ref<i32>) : i32
		%tmp_9 = arith.constant 100 : i7
		// Evaluate global variable $eval1.
		%tmp_10 = arith.constant 1 : i1
		%tmp_11 = arith.extui %tmp_10 : i1 to i32
		// Evaluate global variable $eval1 done: assigned to tmp_11 above in this context.
		%tmp_12 = arith.extui %tmp_9 : i7 to i32
		%tmp_13 = arith.muli %tmp_12, %tmp_11 : i32
		%tmp_14 = arith.addi %tmp_8, %tmp_13 : i32
		// l_t__4_d1_0 aliased to tmp_14
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		%tmp_15 = arith.constant 1 : i1
		%tmp_16 = arith.extui %tmp_15 : i1 to i32
		%tmp_17 = arith.addi %tmp_14, %tmp_16 : i32
		// l_t__4_d1_1 aliased to tmp_17
		// Assignment Statement: End
		// Call Statement: Start
		fifo.print("Tx1: %i\n\00", %tmp_17) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_18 = cal.get(%l_counter__3: !cal.state_ref<i32>) : i32
		%tmp_19 = arith.constant 1 : i1
		%tmp_20 = arith.extui %tmp_19 : i1 to i32
		%tmp_21 = arith.addi %tmp_18, %tmp_20 : i32
		cal.set(%l_counter__3: !cal.state_ref<i32>, %tmp_21: i32)
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

//-- Definition of actor class: ListsSimple
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
		%l_t__6_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Action Local Variable Decl: Start
		%l_list__9_d1_0 = memref.alloc() : memref<4xi32>
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_2 = arith.constant 0 : i1
		%tmp_3 = arith.extui %tmp_2 : i1 to i32
		// l_a__8_d1_0 aliased to tmp_3
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		%tmp_4 = arith.constant 0 : i1
		%tmp_5 = arith.extui %tmp_4 : i1 to i32
		%tmp_6 = arith.index_cast %tmp_5: i32 to index
		%tmp_7 = arith.constant 40 : i6
		%tmp_8 = arith.extui %tmp_7 : i6 to i32
		memref.store %tmp_8, %l_list__9_d1_0[%tmp_6] : memref<4xi32>
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_9 = arith.constant 1 : i1
		%tmp_10 = arith.extui %tmp_9 : i1 to i32
		%tmp_11 = arith.index_cast %tmp_10: i32 to index
		%tmp_12 = arith.constant 30 : i5
		%tmp_13 = arith.extui %tmp_12 : i5 to i32
		memref.store %tmp_13, %l_list__9_d1_0[%tmp_11] : memref<4xi32>
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_14 = arith.constant 2 : i2
		%tmp_15 = arith.extui %tmp_14 : i2 to i32
		%tmp_16 = arith.index_cast %tmp_15: i32 to index
		%tmp_17 = arith.constant 20 : i5
		%tmp_18 = arith.extui %tmp_17 : i5 to i32
		memref.store %tmp_18, %l_list__9_d1_0[%tmp_16] : memref<4xi32>
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_19 = arith.constant 3 : i2
		%tmp_20 = arith.extui %tmp_19 : i2 to i32
		%tmp_21 = arith.index_cast %tmp_20: i32 to index
		%tmp_22 = arith.constant 10 : i4
		%tmp_23 = arith.extui %tmp_22 : i4 to i32
		memref.store %tmp_23, %l_list__9_d1_0[%tmp_21] : memref<4xi32>
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_24 = arith.constant 0 : i1
		%tmp_25 = arith.extui %tmp_24 : i1 to i32
		%tmp_26 = arith.index_cast %tmp_25: i32 to index
		%tmp_27 = memref.load %l_list__9_d1_0[%tmp_26] : memref<4xi32>
		%tmp_28 = arith.constant 1 : i1
		%tmp_29 = arith.extui %tmp_28 : i1 to i32
		%tmp_30 = arith.index_cast %tmp_29: i32 to index
		%tmp_31 = memref.load %l_list__9_d1_0[%tmp_30] : memref<4xi32>
		%tmp_32 = arith.addi %tmp_27, %tmp_31 : i32
		%tmp_33 = arith.constant 2 : i2
		%tmp_34 = arith.extui %tmp_33 : i2 to i32
		%tmp_35 = arith.index_cast %tmp_34: i32 to index
		%tmp_36 = memref.load %l_list__9_d1_0[%tmp_35] : memref<4xi32>
		%tmp_37 = arith.addi %tmp_32, %tmp_36 : i32
		%tmp_38 = arith.constant 3 : i2
		%tmp_39 = arith.extui %tmp_38 : i2 to i32
		%tmp_40 = arith.index_cast %tmp_39: i32 to index
		%tmp_41 = memref.load %l_list__9_d1_0[%tmp_40] : memref<4xi32>
		%tmp_42 = arith.addi %tmp_37, %tmp_41 : i32
		%tmp_43 = arith.constant 4 : i32
		%tmp_44 = arith.addi %tmp_42, %tmp_43 : i32
		// l_a__8_d1_1 aliased to tmp_44
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_45 = arith.constant 0 : i1
		%tmp_46 = arith.extui %tmp_45 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_46: i32)
		// Assignment Statement: End
		// Output Expression: Start
		%tmp_47 = arith.addi %tmp_44, %l_t__6_d1_0 : i32
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_47: i32)
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
		%l_t__11_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Call Statement: Start
		fifo.print("Rx: %i\n\00", %l_t__11_d1_0) : (i32)
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
	%queue_from_DUT_Out, %queue_to_sink_In = fifo.create<i32>(1) : !fifo.input_port<i32>, !fifo.output_port<i32>

	// -- Instantiate actors (also known as nodes/instances)
	cal.create_instance @source "source" ()
		ports_out(%queue_from_source_Out: !fifo.input_port<i32>)
	cal.create_instance @DUT "DUT" ()
		ports_in(%queue_to_DUT_In: !fifo.output_port<i32>)
		ports_out(%queue_from_DUT_Out: !fifo.input_port<i32>)
	cal.create_instance @sink "sink" ()
		ports_in(%queue_to_sink_In: !fifo.output_port<i32>)

}

