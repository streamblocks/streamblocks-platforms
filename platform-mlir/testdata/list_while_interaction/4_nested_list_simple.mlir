//-- Definition of actor class: Source
cal.actor @source ()
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
	%l_counter__4 = cal.create_state_var<i32> : !cal.state_ref<i32>
	%tmp_0 = arith.constant 0 : i1
	%tmp_1 = arith.extui %tmp_0 : i1 to i32
	cal.set(%l_counter__4: !cal.state_ref<i32>, %tmp_1: i32)
	// Generation action: transmit
	cal.action "transmit" priority=0 {
		// Guard: Start
		cal.predicate {
			%tmp_2 = cal.get(%l_counter__4: !cal.state_ref<i32>) : i32
			// Evaluate global variable $eval2.
			%tmp_3 = arith.constant 1 : i1
			%tmp_4 = arith.extui %tmp_3 : i1 to i32
			// Evaluate global variable $eval2 done: assigned to tmp_4 above in this context.
			%tmp_5 = arith.cmpi slt, %tmp_2, %tmp_4 : i32
			cal.predicate_result %tmp_5 : i1
		}
		// Guard: End
		// Action Local Variable Decl: Start
		%tmp_6 = cal.get(%l_counter__4: !cal.state_ref<i32>) : i32
		%tmp_7 = arith.constant 100 : i7
		// Evaluate global variable $eval1.
		%tmp_8 = arith.constant 0 : i1
		%tmp_9 = arith.extui %tmp_8 : i1 to i32
		// Evaluate global variable $eval1 done: assigned to tmp_9 above in this context.
		%tmp_10 = arith.extui %tmp_7 : i7 to i32
		%tmp_11 = arith.muli %tmp_10, %tmp_9 : i32
		%tmp_12 = arith.addi %tmp_6, %tmp_11 : i32
		// l_t__5_d1_0 aliased to tmp_12
		// Action Local Variable Decl: End
		// Assignment Statement: Start
		%tmp_13 = arith.constant 1 : i1
		%tmp_14 = arith.extui %tmp_13 : i1 to i32
		%tmp_15 = arith.addi %tmp_12, %tmp_14 : i32
		// l_t__5_d1_1 aliased to tmp_15
		// Assignment Statement: End
		fifo.print("Tx0: %i\n\00", %tmp_15) : (i32)
		// Assignment Statement: Start
		%tmp_16 = cal.get(%l_counter__4: !cal.state_ref<i32>) : i32
		%tmp_17 = arith.constant 1 : i1
		%tmp_18 = arith.extui %tmp_17 : i1 to i32
		%tmp_19 = arith.addi %tmp_16, %tmp_18 : i32
		cal.set(%l_counter__4: !cal.state_ref<i32>, %tmp_19: i32)
		// Assignment Statement: End
		// Output Expression: Start
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_15: i32)
		// Output Expression: End
	}
}

//-- Definition of actor class: NestedListSimple
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
		%l_list__2d__13_d1_0 = memref.alloc() : memref<4x4xi32>
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_0 = arith.constant 0 : i1
		%tmp_1 = arith.extui %tmp_0 : i1 to i32
		// l_list2d__index__x__14_d1_0 aliased to tmp_1
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_2 = arith.constant 0 : i1
		%tmp_3 = arith.extui %tmp_2 : i1 to i32
		// l_list2d__index__y__15_d1_0 aliased to tmp_3
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%l_list__flat__12_d1_0 = memref.alloc() : memref<16xi32>
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_4 = arith.constant 0 : i1
		%tmp_5 = arith.extui %tmp_4 : i1 to i32
		// l_list__flat__index__16_d1_0 aliased to tmp_5
		// Action Local Variable Decl: End
		// While Statement: Begin
		%l_list2d__index__x__14_d1_1, %l_list2d__index__y__15_d1_1 = scf.while(%l_list2d__index__x__14_d2_0 = %tmp_1, %l_list2d__index__y__15_d2_0 = %tmp_3) : (i32, i32) -> (i32, i32) {
			// While Statement: Condition Check
			%tmp_6 = arith.constant 4 : i3
			%tmp_7 = arith.extui %tmp_6 : i3 to i32
			%tmp_8 = arith.cmpi ult, %l_list2d__index__x__14_d2_0, %tmp_7 : i32
			scf.condition(%tmp_8) %l_list2d__index__x__14_d2_0, %l_list2d__index__y__15_d2_0 : i32, i32
		} do {
			// While Statement: Body Execution
		^tmp_9(%l_list2d__index__x__14_d2_0: i32, %l_list2d__index__y__15_d2_0: i32):
			// Assignment Statement: Start
			%tmp_10 = arith.constant 0 : i1
			%tmp_11 = arith.extui %tmp_10 : i1 to i32
			// l_list2d__index__y__15_d2_1 aliased to tmp_11
			// Assignment Statement: End
			// While Statement: Begin
			%l_list2d__index__y__15_d2_2 = scf.while(%l_list2d__index__y__15_d3_0 = %tmp_11) : (i32) -> (i32) {
				// While Statement: Condition Check
				%tmp_12 = arith.constant 4 : i3
				%tmp_13 = arith.extui %tmp_12 : i3 to i32
				%tmp_14 = arith.cmpi ult, %l_list2d__index__y__15_d3_0, %tmp_13 : i32
				scf.condition(%tmp_14) %l_list2d__index__y__15_d3_0 : i32
			} do {
				// While Statement: Body Execution
			^tmp_15(%l_list2d__index__y__15_d3_0: i32):
				// Assignment Statement: Start
				%tmp_16 = arith.index_cast %l_list2d__index__y__15_d3_0: i32 to index
				%tmp_17 = arith.index_cast %l_list2d__index__x__14_d2_0: i32 to index
				%tmp_18 = arith.constant 4 : i3
				%tmp_19 = arith.extui %tmp_18 : i3 to i32
				%tmp_20 = arith.muli %l_list2d__index__x__14_d2_0, %tmp_19 : i32
				%tmp_21 = arith.addi %tmp_20, %l_list2d__index__y__15_d3_0 : i32
				memref.store %tmp_21, %l_list__2d__13_d1_0[%tmp_17, %tmp_16] : memref<4x4xi32>
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_22 = arith.constant 1 : i1
				%tmp_23 = arith.extui %tmp_22 : i1 to i32
				%tmp_24 = arith.addi %l_list2d__index__y__15_d3_0, %tmp_23 : i32
				// l_list2d__index__y__15_d3_1 aliased to tmp_24
				// Assignment Statement: End
				scf.yield %tmp_24: i32
			}
			// While Statement: End
			// Assignment Statement: Start
			%tmp_25 = arith.constant 1 : i1
			%tmp_26 = arith.extui %tmp_25 : i1 to i32
			%tmp_27 = arith.addi %l_list2d__index__x__14_d2_0, %tmp_26 : i32
			// l_list2d__index__x__14_d2_1 aliased to tmp_27
			// Assignment Statement: End
			scf.yield %tmp_27, %l_list2d__index__y__15_d2_2: i32, i32
		}
		// While Statement: End
		// Assignment Statement: Start
		%tmp_28 = arith.constant 0 : i1
		%tmp_29 = arith.extui %tmp_28 : i1 to i32
		// l_list2d__index__x__14_d1_2 aliased to tmp_29
		// Assignment Statement: End
		// While Statement: Begin
		%l_list2d__index__x__14_d1_3, %l_list2d__index__y__15_d1_2, %l_list__flat__index__16_d1_1 = scf.while(%l_list2d__index__x__14_d2_0 = %tmp_29, %l_list2d__index__y__15_d2_0 = %l_list2d__index__y__15_d1_1, %l_list__flat__index__16_d2_0 = %tmp_5) : (i32, i32, i32) -> (i32, i32, i32) {
			// While Statement: Condition Check
			%tmp_30 = arith.constant 4 : i3
			%tmp_31 = arith.extui %tmp_30 : i3 to i32
			%tmp_32 = arith.cmpi ult, %l_list2d__index__x__14_d2_0, %tmp_31 : i32
			scf.condition(%tmp_32) %l_list2d__index__x__14_d2_0, %l_list2d__index__y__15_d2_0, %l_list__flat__index__16_d2_0 : i32, i32, i32
		} do {
			// While Statement: Body Execution
		^tmp_33(%l_list2d__index__x__14_d2_0: i32, %l_list2d__index__y__15_d2_0: i32, %l_list__flat__index__16_d2_0: i32):
			// Assignment Statement: Start
			%tmp_34 = arith.constant 0 : i1
			%tmp_35 = arith.extui %tmp_34 : i1 to i32
			// l_list2d__index__y__15_d2_1 aliased to tmp_35
			// Assignment Statement: End
			// While Statement: Begin
			%l_list2d__index__y__15_d2_2, %l_list__flat__index__16_d2_1 = scf.while(%l_list2d__index__y__15_d3_0 = %tmp_35, %l_list__flat__index__16_d3_0 = %l_list__flat__index__16_d2_0) : (i32, i32) -> (i32, i32) {
				// While Statement: Condition Check
				%tmp_36 = arith.constant 4 : i3
				%tmp_37 = arith.extui %tmp_36 : i3 to i32
				%tmp_38 = arith.cmpi ult, %l_list2d__index__y__15_d3_0, %tmp_37 : i32
				scf.condition(%tmp_38) %l_list2d__index__y__15_d3_0, %l_list__flat__index__16_d3_0 : i32, i32
			} do {
				// While Statement: Body Execution
			^tmp_39(%l_list2d__index__y__15_d3_0: i32, %l_list__flat__index__16_d3_0: i32):
				// Assignment Statement: Start
				%tmp_40 = arith.index_cast %l_list__flat__index__16_d3_0: i32 to index
				%tmp_41 = arith.index_cast %l_list2d__index__y__15_d3_0: i32 to index
				%tmp_42 = arith.index_cast %l_list2d__index__x__14_d2_0: i32 to index
				%tmp_43 = memref.load %l_list__2d__13_d1_0[%tmp_42, %tmp_41] : memref<4x4xi32>
				memref.store %tmp_43, %l_list__flat__12_d1_0[%tmp_40] : memref<16xi32>
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_44 = arith.constant 1 : i1
				%tmp_45 = arith.extui %tmp_44 : i1 to i32
				%tmp_46 = arith.addi %l_list2d__index__y__15_d3_0, %tmp_45 : i32
				// l_list2d__index__y__15_d3_1 aliased to tmp_46
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_47 = arith.constant 1 : i1
				%tmp_48 = arith.extui %tmp_47 : i1 to i32
				%tmp_49 = arith.addi %l_list__flat__index__16_d3_0, %tmp_48 : i32
				// l_list__flat__index__16_d3_1 aliased to tmp_49
				// Assignment Statement: End
				scf.yield %tmp_46, %tmp_49: i32, i32
			}
			// While Statement: End
			// Assignment Statement: Start
			%tmp_50 = arith.constant 1 : i1
			%tmp_51 = arith.extui %tmp_50 : i1 to i32
			%tmp_52 = arith.addi %l_list2d__index__x__14_d2_0, %tmp_51 : i32
			// l_list2d__index__x__14_d2_1 aliased to tmp_52
			// Assignment Statement: End
			scf.yield %tmp_52, %l_list2d__index__y__15_d2_2, %l_list__flat__index__16_d2_1: i32, i32, i32
		}
		// While Statement: End
		// Output Expression: Start
		%tmp_53 = arith.constant 0 : index
		%tmp_54 = memref.load %l_list__flat__12_d1_0[%tmp_53] : memref<16xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_54: i32)
		%tmp_55 = arith.constant 1 : index
		%tmp_56 = memref.load %l_list__flat__12_d1_0[%tmp_55] : memref<16xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_56: i32)
		%tmp_57 = arith.constant 2 : index
		%tmp_58 = memref.load %l_list__flat__12_d1_0[%tmp_57] : memref<16xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_58: i32)
		%tmp_59 = arith.constant 3 : index
		%tmp_60 = memref.load %l_list__flat__12_d1_0[%tmp_59] : memref<16xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_60: i32)
		%tmp_61 = arith.constant 4 : index
		%tmp_62 = memref.load %l_list__flat__12_d1_0[%tmp_61] : memref<16xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_62: i32)
		%tmp_63 = arith.constant 5 : index
		%tmp_64 = memref.load %l_list__flat__12_d1_0[%tmp_63] : memref<16xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_64: i32)
		%tmp_65 = arith.constant 6 : index
		%tmp_66 = memref.load %l_list__flat__12_d1_0[%tmp_65] : memref<16xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_66: i32)
		%tmp_67 = arith.constant 7 : index
		%tmp_68 = memref.load %l_list__flat__12_d1_0[%tmp_67] : memref<16xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_68: i32)
		%tmp_69 = arith.constant 8 : index
		%tmp_70 = memref.load %l_list__flat__12_d1_0[%tmp_69] : memref<16xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_70: i32)
		%tmp_71 = arith.constant 9 : index
		%tmp_72 = memref.load %l_list__flat__12_d1_0[%tmp_71] : memref<16xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_72: i32)
		%tmp_73 = arith.constant 10 : index
		%tmp_74 = memref.load %l_list__flat__12_d1_0[%tmp_73] : memref<16xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_74: i32)
		%tmp_75 = arith.constant 11 : index
		%tmp_76 = memref.load %l_list__flat__12_d1_0[%tmp_75] : memref<16xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_76: i32)
		%tmp_77 = arith.constant 12 : index
		%tmp_78 = memref.load %l_list__flat__12_d1_0[%tmp_77] : memref<16xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_78: i32)
		%tmp_79 = arith.constant 13 : index
		%tmp_80 = memref.load %l_list__flat__12_d1_0[%tmp_79] : memref<16xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_80: i32)
		%tmp_81 = arith.constant 14 : index
		%tmp_82 = memref.load %l_list__flat__12_d1_0[%tmp_81] : memref<16xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_82: i32)
		%tmp_83 = arith.constant 15 : index
		%tmp_84 = memref.load %l_list__flat__12_d1_0[%tmp_83] : memref<16xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_84: i32)
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
		%l_t__7_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		fifo.print("Rx0: %i\n\00", %l_t__7_d1_0) : (i32)
	}
}

// -- Top Network: Defines structure of actor application
cal.network
{

	// -- Instantiate channels between actors
	%queue_from_source_Out, %queue_to_DUT_In = fifo.create<i32>(1) : !fifo.input_port<i32>, !fifo.output_port<i32>
	%queue_from_DUT_Out, %queue_to_sink_In = fifo.create<i32>(16) : !fifo.input_port<i32>, !fifo.output_port<i32>

	// -- Instantiate actors (also known as nodes/instances)
	cal.create_instance @source "source" ()
		ports_out(%queue_from_source_Out: !fifo.input_port<i32>)
	cal.create_instance @DUT "DUT" ()
		ports_in(%queue_to_DUT_In: !fifo.output_port<i32>)
		ports_out(%queue_from_DUT_Out: !fifo.input_port<i32>)
	cal.create_instance @sink "sink" ()
		ports_in(%queue_to_sink_In: !fifo.output_port<i32>)

}

