//-- Definition of actor class: Source
cal.actor @source ()
	ports_out(%Out: !fifo.input_port<i32>)
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
			%tmp_3 = arith.constant 5 : i3
			%tmp_4 = arith.extui %tmp_3 : i3 to i32
			// Evaluate global variable $eval1 done: assigned to tmp_4 above in this context.
			%tmp_5 = arith.cmpi slt, %tmp_2, %tmp_4 : i32
			cal.predicate_result %tmp_5 : i1
		}
		// Guard: End
		// Action Local Variable Decl: Start
		%tmp_6 = cal.get(%l_counter__2: !cal.state_ref<i32>) : i32
		// l_t__3_d1_0 aliased to tmp_6
		// Action Local Variable Decl: End
		fifo.print("Tx: %i\n\00", %tmp_6) : (i32)
		// Assignment Statement: Start
		%tmp_7 = cal.get(%l_counter__2: !cal.state_ref<i32>) : i32
		%tmp_8 = arith.constant 1 : i1
		%tmp_9 = arith.extui %tmp_8 : i1 to i32
		%tmp_10 = arith.addi %tmp_7, %tmp_9 : i32
		cal.set(%l_counter__2: !cal.state_ref<i32>, %tmp_10: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_6: i32)
		// Output Expression: End
	}
}

//-- Definition of actor class: AssignList
cal.actor @pass ()
	ports_in(%In: !fifo.output_port<i32>)
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	// Generation action: $untagged0
	cal.action "$untagged0" priority=0 {
		// Input Pattern: Start
		%l_t1__5_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Action Local Variable Decl: Start
		%tmp_0 = memref.alloc() : memref<4xi32>
		%tmp_1 = arith.constant 0: index
		%tmp_2 = arith.constant 1 : i1
		%tmp_3 = arith.extui %tmp_2 : i1 to i32
		memref.store %tmp_3, %tmp_0[%tmp_1] : memref<4xi32>
		%tmp_4 = arith.constant 1: index
		%tmp_5 = arith.constant 2 : i2
		%tmp_6 = arith.extui %tmp_5 : i2 to i32
		memref.store %tmp_6, %tmp_0[%tmp_4] : memref<4xi32>
		%tmp_7 = arith.constant 2: index
		%tmp_8 = arith.constant 3 : i2
		%tmp_9 = arith.extui %tmp_8 : i2 to i32
		memref.store %tmp_9, %tmp_0[%tmp_7] : memref<4xi32>
		%tmp_10 = arith.constant 3: index
		%tmp_11 = arith.constant 4 : i3
		%tmp_12 = arith.extui %tmp_11 : i3 to i32
		memref.store %tmp_12, %tmp_0[%tmp_10] : memref<4xi32>
		// l_list__7_d1_0 aliased to tmp_0
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_13 = arith.constant 0 : i1
		%tmp_14 = arith.extui %tmp_13 : i1 to i32
		// l_index__8_d1_0 aliased to tmp_14
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		%tmp_15 = arith.constant 0 : i1
		%tmp_16 = arith.extui %tmp_15 : i1 to i32
		%tmp_17 = arith.index_cast %tmp_16: i32 to index
		%tmp_18 = arith.constant 1 : i1
		%tmp_19 = arith.extui %tmp_18 : i1 to i32
		memref.store %tmp_19, %tmp_0[%tmp_17] : memref<4xi32>
		// Assignment Statement: End
		// While Statement: Begin
		scf.while() : () -> () {
			// While Statement: Condition Check
			%tmp_20 = arith.constant 0 : i1
			%tmp_21 = arith.extui %tmp_20 : i1 to i32
			%tmp_22 = arith.index_cast %tmp_21: i32 to index
			%tmp_24 = arith.constant 4 : i3
			%tmp_25 = arith.extui %tmp_24 : i3 to i32
			%tmp_26 = arith.cmpi ult, %tmp_23, %tmp_25 : i32
			scf.condition(%tmp_26)
		} do {
			// While Statement: Body Execution
		^tmp_27():
			// Assignment Statement: Start
			%tmp_28 = arith.constant 0 : i1
			%tmp_29 = arith.extui %tmp_28 : i1 to i32
			%tmp_30 = arith.index_cast %tmp_29: i32 to index
			%tmp_32 = arith.index_cast %tmp_31: i32 to index
			%tmp_33 = arith.constant 0 : i1
			%tmp_34 = arith.extui %tmp_33 : i1 to i32
			%tmp_35 = arith.index_cast %tmp_34: i32 to index
			%tmp_37 = arith.addi %l_t1__5_d1_0, %tmp_36 : i32
			memref.store %tmp_37, %tmp_0[%tmp_32] : memref<4xi32>
			// Assignment Statement: End
			// Assignment Statement: Start
			%tmp_38 = arith.constant 0 : i1
			%tmp_39 = arith.extui %tmp_38 : i1 to i32
			%tmp_40 = arith.index_cast %tmp_39: i32 to index
			%tmp_41 = arith.constant 0 : i1
			%tmp_42 = arith.extui %tmp_41 : i1 to i32
			%tmp_43 = arith.index_cast %tmp_42: i32 to index
			%tmp_45 = arith.constant 1 : i1
			%tmp_46 = arith.extui %tmp_45 : i1 to i32
			%tmp_47 = arith.addi %tmp_44, %tmp_46 : i32
			memref.store %tmp_47, %tmp_0[%tmp_40] : memref<4xi32>
			// Assignment Statement: End
			scf.yield
		}
		// While Statement: End
		// Output Expression: Start
		%tmp_48 = arith.constant 0 : index
		%tmp_49 = memref.load %tmp_0[%tmp_48] : memref<4xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_49: i32)
		%tmp_50 = arith.constant 1 : index
		%tmp_51 = memref.load %tmp_0[%tmp_50] : memref<4xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_51: i32)
		%tmp_52 = arith.constant 2 : index
		%tmp_53 = memref.load %tmp_0[%tmp_52] : memref<4xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_53: i32)
		%tmp_54 = arith.constant 3 : index
		%tmp_55 = memref.load %tmp_0[%tmp_54] : memref<4xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_55: i32)
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
		fifo.print("Rx: %i\n\00", %l_t__10_d1_0) : (i32)
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

