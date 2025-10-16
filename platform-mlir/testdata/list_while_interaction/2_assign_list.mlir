//-- Definition of actor class: Source
cal.actor @source ()
	ports_out(%Out: !fifo.input_port<i32>)
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
			%tmp_5 = arith.constant 5 : i3
			%tmp_6 = arith.extui %tmp_5 : i3 to i32
			// Evaluate global variable $eval1 done: assigned to tmp_6 above in this context.
			%tmp_7 = arith.cmpi slt, %tmp_4, %tmp_6 : i32
			cal.predicate_result %tmp_7 : i1
		}
		// Guard: End
		// Action Local Variable Decl: Start
		%tmp_8 = cal.get(%l_counter__2: !cal.state_ref<i32>) : i32
		// l_t__3_d1_0 aliased to tmp_8
		// Action Local Variable Decl: End
		// Call Statement: Start
		fifo.print("Tx: %i\n\00", %tmp_8) : (i32)
		// Call Statement: End
		// Assignment Statement: Start
		%tmp_9 = cal.get(%l_counter__2: !cal.state_ref<i32>) : i32
		%tmp_10 = arith.constant 1 : i1
		%tmp_11 = arith.extui %tmp_10 : i1 to i32
		%tmp_12 = arith.addi %tmp_9, %tmp_11 : i32
		cal.set(%l_counter__2: !cal.state_ref<i32>, %tmp_12: i32)
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_13 = arith.constant 0 : i1
		%tmp_14 = arith.extui %tmp_13 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_14: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_8: i32)
		// Output Expression: End
	}
}

//-- Definition of actor class: AssignList
cal.actor @pass ()
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
		%l_t1__5_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Action Local Variable Decl: Start
		%tmp_2 = memref.alloc() : memref<4xi32>
		%tmp_3 = arith.constant 0: index
		%tmp_4 = arith.constant 1 : i1
		%tmp_5 = arith.extui %tmp_4 : i1 to i32
		memref.store %tmp_5, %tmp_2[%tmp_3] : memref<4xi32>
		%tmp_6 = arith.constant 1: index
		%tmp_7 = arith.constant 2 : i2
		%tmp_8 = arith.extui %tmp_7 : i2 to i32
		memref.store %tmp_8, %tmp_2[%tmp_6] : memref<4xi32>
		%tmp_9 = arith.constant 2: index
		%tmp_10 = arith.constant 3 : i2
		%tmp_11 = arith.extui %tmp_10 : i2 to i32
		memref.store %tmp_11, %tmp_2[%tmp_9] : memref<4xi32>
		%tmp_12 = arith.constant 3: index
		%tmp_13 = arith.constant 4 : i3
		%tmp_14 = arith.extui %tmp_13 : i3 to i32
		memref.store %tmp_14, %tmp_2[%tmp_12] : memref<4xi32>
		// l_list__7_d1_0 aliased to tmp_2
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_15 = arith.constant 0 : i1
		%tmp_16 = arith.extui %tmp_15 : i1 to i32
		// l_index__8_d1_0 aliased to tmp_16
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		%tmp_17 = arith.constant 0 : i1
		%tmp_18 = arith.extui %tmp_17 : i1 to i32
		%tmp_19 = arith.index_cast %tmp_18: i32 to index
		%tmp_20 = arith.constant 1 : i1
		%tmp_21 = arith.extui %tmp_20 : i1 to i32
		memref.store %tmp_21, %tmp_2[%tmp_19] : memref<4xi32>
		// Assignment Statement: End
		// While Statement: Begin
		scf.while() : () -> () {
			// While Statement: Condition Check
			%tmp_22 = arith.constant 0 : i1
			%tmp_23 = arith.extui %tmp_22 : i1 to i32
			%tmp_24 = arith.index_cast %tmp_23: i32 to index
			%tmp_25 = memref.load %tmp_2[%tmp_24] : memref<4xi32>
			%tmp_26 = arith.constant 4 : i3
			%tmp_27 = arith.extui %tmp_26 : i3 to i32
			%tmp_28 = arith.cmpi ult, %tmp_25, %tmp_27 : i32
			scf.condition(%tmp_28)
		} do {
			// While Statement: Body Execution
		^tmp_29():
			// Assignment Statement: Start
			%tmp_30 = arith.constant 0 : i1
			%tmp_31 = arith.extui %tmp_30 : i1 to i32
			%tmp_32 = arith.index_cast %tmp_31: i32 to index
			%tmp_33 = memref.load %tmp_2[%tmp_32] : memref<4xi32>
			%tmp_34 = arith.index_cast %tmp_33: i32 to index
			%tmp_35 = arith.constant 0 : i1
			%tmp_36 = arith.extui %tmp_35 : i1 to i32
			%tmp_37 = arith.index_cast %tmp_36: i32 to index
			%tmp_38 = memref.load %tmp_2[%tmp_37] : memref<4xi32>
			%tmp_39 = arith.addi %l_t1__5_d1_0, %tmp_38 : i32
			memref.store %tmp_39, %tmp_2[%tmp_34] : memref<4xi32>
			// Assignment Statement: End
			// Assignment Statement: Start
			%tmp_40 = arith.constant 0 : i1
			%tmp_41 = arith.extui %tmp_40 : i1 to i32
			%tmp_42 = arith.index_cast %tmp_41: i32 to index
			%tmp_43 = arith.constant 0 : i1
			%tmp_44 = arith.extui %tmp_43 : i1 to i32
			%tmp_45 = arith.index_cast %tmp_44: i32 to index
			%tmp_46 = memref.load %tmp_2[%tmp_45] : memref<4xi32>
			%tmp_47 = arith.constant 1 : i1
			%tmp_48 = arith.extui %tmp_47 : i1 to i32
			%tmp_49 = arith.addi %tmp_46, %tmp_48 : i32
			memref.store %tmp_49, %tmp_2[%tmp_42] : memref<4xi32>
			// Assignment Statement: End
			scf.yield
		}
		// While Statement: End
		// Assignment Statement: Start
		%tmp_50 = arith.constant 0 : i1
		%tmp_51 = arith.extui %tmp_50 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_51: i32)
		// Assignment Statement: End
		// Output Expression: Start
		%tmp_52 = arith.constant 0 : index
		%tmp_53 = memref.load %tmp_2[%tmp_52] : memref<4xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_53: i32)
		%tmp_54 = arith.constant 1 : index
		%tmp_55 = memref.load %tmp_2[%tmp_54] : memref<4xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_55: i32)
		%tmp_56 = arith.constant 2 : index
		%tmp_57 = memref.load %tmp_2[%tmp_56] : memref<4xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_57: i32)
		%tmp_58 = arith.constant 3 : index
		%tmp_59 = memref.load %tmp_2[%tmp_58] : memref<4xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_59: i32)
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
		%l_t__10_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Call Statement: Start
		fifo.print("Rx: %i\n\00", %l_t__10_d1_0) : (i32)
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
	%queue_from_pass_Out, %queue_to_sink_In = fifo.create<i32>(4) : !fifo.input_port<i32>, !fifo.output_port<i32>
	%queue_from_source_Out, %queue_to_pass_In = fifo.create<i32>(1) : !fifo.input_port<i32>, !fifo.output_port<i32>

	// -- Instantiate actors (also known as nodes/instances)
	cal.create_instance @source "source" ()
		ports_out(%queue_from_source_Out: !fifo.input_port<i32>)
	cal.create_instance @pass "pass" ()
		ports_in(%queue_to_pass_In: !fifo.output_port<i32>)
		ports_out(%queue_from_pass_Out: !fifo.input_port<i32>)
	cal.create_instance @sink "sink" ()
		ports_in(%queue_to_sink_In: !fifo.output_port<i32>)

}

