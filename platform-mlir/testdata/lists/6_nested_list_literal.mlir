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

//-- Definition of actor class: NestedLiterals
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
		%l_twoDList__13_d1_0 = memref.alloc() : memref<2x3xi32>
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%l_out__list__12_d1_0 = memref.alloc() : memref<6xi32>
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_0 = arith.constant 0 : i1
		%tmp_1 = arith.extui %tmp_0 : i1 to i32
		// l_inner__index__14_d1_0 aliased to tmp_1
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_2 = arith.constant 0 : i1
		%tmp_3 = arith.extui %tmp_2 : i1 to i32
		// l_outer__index__15_d1_0 aliased to tmp_3
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_4 = arith.constant 0 : i1
		%tmp_5 = arith.extui %tmp_4 : i1 to i32
		// l_out__list__index__16_d1_0 aliased to tmp_5
		// Action Local Variable Decl: End
		// While Statement: Begin
		%l_inner__index__14_d1_1, %l_outer__index__15_d1_1 = scf.while(%l_inner__index__14_d2_0 = %tmp_1, %l_outer__index__15_d2_0 = %tmp_3) : (i32, i32) -> (i32, i32) {
			// While Statement: Condition Check
			%tmp_6 = arith.constant 2 : i2
			%tmp_7 = arith.extui %tmp_6 : i2 to i32
			%tmp_8 = arith.cmpi ult, %l_outer__index__15_d2_0, %tmp_7 : i32
			scf.condition(%tmp_8) %l_inner__index__14_d2_0, %l_outer__index__15_d2_0 : i32, i32
		} do {
			// While Statement: Body Execution
		^tmp_9(%l_inner__index__14_d2_0: i32, %l_outer__index__15_d2_0: i32):
			// Assignment Statement: Start
			%tmp_10 = arith.constant 0 : i1
			%tmp_11 = arith.extui %tmp_10 : i1 to i32
			// l_inner__index__14_d2_1 aliased to tmp_11
			// Assignment Statement: End
			// While Statement: Begin
			%l_inner__index__14_d2_2 = scf.while(%l_inner__index__14_d3_0 = %tmp_11) : (i32) -> (i32) {
				// While Statement: Condition Check
				%tmp_12 = arith.constant 3 : i2
				%tmp_13 = arith.extui %tmp_12 : i2 to i32
				%tmp_14 = arith.cmpi ult, %l_inner__index__14_d3_0, %tmp_13 : i32
				scf.condition(%tmp_14) %l_inner__index__14_d3_0 : i32
			} do {
				// While Statement: Body Execution
			^tmp_15(%l_inner__index__14_d3_0: i32):
				// Assignment Statement: Start
				%tmp_16 = arith.index_cast %l_inner__index__14_d3_0: i32 to index
				%tmp_17 = arith.index_cast %l_outer__index__15_d2_0: i32 to index
				%tmp_18 = arith.constant 1 : i1
				%tmp_19 = arith.extui %tmp_18 : i1 to i32
				%tmp_20 = arith.addi %tmp_19, %l_inner__index__14_d3_0 : i32
				%tmp_21 = arith.constant 3 : i2
				%tmp_22 = arith.extui %tmp_21 : i2 to i32
				%tmp_23 = arith.muli %l_outer__index__15_d2_0, %tmp_22 : i32
				%tmp_24 = arith.addi %tmp_20, %tmp_23 : i32
				memref.store %tmp_24, %l_twoDList__13_d1_0[%tmp_17, %tmp_16] : memref<2x3xi32>
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_25 = arith.constant 1 : i1
				%tmp_26 = arith.extui %tmp_25 : i1 to i32
				%tmp_27 = arith.addi %l_inner__index__14_d3_0, %tmp_26 : i32
				// l_inner__index__14_d3_1 aliased to tmp_27
				// Assignment Statement: End
				scf.yield %tmp_27: i32
			}
			// While Statement: End
			// Assignment Statement: Start
			%tmp_28 = arith.constant 1 : i1
			%tmp_29 = arith.extui %tmp_28 : i1 to i32
			%tmp_30 = arith.addi %l_outer__index__15_d2_0, %tmp_29 : i32
			// l_outer__index__15_d2_1 aliased to tmp_30
			// Assignment Statement: End
			scf.yield %l_inner__index__14_d2_2, %tmp_30: i32, i32
		}
		// While Statement: End
		// Assignment Statement: Start
		%tmp_31 = arith.constant 0 : i1
		%tmp_32 = arith.extui %tmp_31 : i1 to i32
		// l_inner__index__14_d1_2 aliased to tmp_32
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_33 = arith.constant 0 : i1
		%tmp_34 = arith.extui %tmp_33 : i1 to i32
		// l_outer__index__15_d1_2 aliased to tmp_34
		// Assignment Statement: End
		// While Statement: Begin
		%l_inner__index__14_d1_3, %l_outer__index__15_d1_3 = scf.while(%l_inner__index__14_d2_0 = %tmp_32, %l_outer__index__15_d2_0 = %tmp_34) : (i32, i32) -> (i32, i32) {
			// While Statement: Condition Check
			%tmp_35 = arith.constant 2 : i2
			%tmp_36 = arith.extui %tmp_35 : i2 to i32
			%tmp_37 = arith.cmpi ult, %l_outer__index__15_d2_0, %tmp_36 : i32
			scf.condition(%tmp_37) %l_inner__index__14_d2_0, %l_outer__index__15_d2_0 : i32, i32
		} do {
			// While Statement: Body Execution
		^tmp_38(%l_inner__index__14_d2_0: i32, %l_outer__index__15_d2_0: i32):
			// Assignment Statement: Start
			%tmp_39 = arith.constant 0 : i1
			%tmp_40 = arith.extui %tmp_39 : i1 to i32
			// l_inner__index__14_d2_1 aliased to tmp_40
			// Assignment Statement: End
			// While Statement: Begin
			%l_inner__index__14_d2_2 = scf.while(%l_inner__index__14_d3_0 = %tmp_40) : (i32) -> (i32) {
				// While Statement: Condition Check
				%tmp_41 = arith.constant 3 : i2
				%tmp_42 = arith.extui %tmp_41 : i2 to i32
				%tmp_43 = arith.cmpi ult, %l_inner__index__14_d3_0, %tmp_42 : i32
				scf.condition(%tmp_43) %l_inner__index__14_d3_0 : i32
			} do {
				// While Statement: Body Execution
			^tmp_44(%l_inner__index__14_d3_0: i32):
				// Assignment Statement: Start
				%tmp_45 = arith.index_cast %l_inner__index__14_d3_0: i32 to index
				%tmp_46 = arith.index_cast %l_outer__index__15_d2_0: i32 to index
				%tmp_47 = arith.index_cast %l_inner__index__14_d3_0: i32 to index
				%tmp_48 = arith.index_cast %l_outer__index__15_d2_0: i32 to index
				%tmp_49 = memref.load %l_twoDList__13_d1_0[%tmp_48, %tmp_47] : memref<2x3xi32>
				%tmp_50 = arith.constant 20 : i5
				%tmp_51 = arith.extui %tmp_50 : i5 to i32
				%tmp_52 = arith.muli %tmp_49, %tmp_51 : i32
				memref.store %tmp_52, %l_twoDList__13_d1_0[%tmp_46, %tmp_45] : memref<2x3xi32>
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_53 = arith.constant 1 : i1
				%tmp_54 = arith.extui %tmp_53 : i1 to i32
				%tmp_55 = arith.addi %l_inner__index__14_d3_0, %tmp_54 : i32
				// l_inner__index__14_d3_1 aliased to tmp_55
				// Assignment Statement: End
				scf.yield %tmp_55: i32
			}
			// While Statement: End
			// Assignment Statement: Start
			%tmp_56 = arith.constant 1 : i1
			%tmp_57 = arith.extui %tmp_56 : i1 to i32
			%tmp_58 = arith.addi %l_outer__index__15_d2_0, %tmp_57 : i32
			// l_outer__index__15_d2_1 aliased to tmp_58
			// Assignment Statement: End
			scf.yield %l_inner__index__14_d2_2, %tmp_58: i32, i32
		}
		// While Statement: End
		// Assignment Statement: Start
		%tmp_59 = arith.constant 0 : i1
		%tmp_60 = arith.extui %tmp_59 : i1 to i32
		// l_inner__index__14_d1_4 aliased to tmp_60
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_61 = arith.constant 0 : i1
		%tmp_62 = arith.extui %tmp_61 : i1 to i32
		// l_outer__index__15_d1_4 aliased to tmp_62
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_63 = arith.constant 0 : i1
		%tmp_64 = arith.extui %tmp_63 : i1 to i32
		// l_out__list__index__16_d1_1 aliased to tmp_64
		// Assignment Statement: End
		// While Statement: Begin
		%l_inner__index__14_d1_5, %l_out__list__index__16_d1_2, %l_outer__index__15_d1_5 = scf.while(%l_inner__index__14_d2_0 = %tmp_60, %l_out__list__index__16_d2_0 = %tmp_64, %l_outer__index__15_d2_0 = %tmp_62) : (i32, i32, i32) -> (i32, i32, i32) {
			// While Statement: Condition Check
			%tmp_65 = arith.constant 2 : i2
			%tmp_66 = arith.extui %tmp_65 : i2 to i32
			%tmp_67 = arith.cmpi ult, %l_outer__index__15_d2_0, %tmp_66 : i32
			scf.condition(%tmp_67) %l_inner__index__14_d2_0, %l_out__list__index__16_d2_0, %l_outer__index__15_d2_0 : i32, i32, i32
		} do {
			// While Statement: Body Execution
		^tmp_68(%l_inner__index__14_d2_0: i32, %l_out__list__index__16_d2_0: i32, %l_outer__index__15_d2_0: i32):
			// Assignment Statement: Start
			%tmp_69 = arith.constant 0 : i1
			%tmp_70 = arith.extui %tmp_69 : i1 to i32
			// l_inner__index__14_d2_1 aliased to tmp_70
			// Assignment Statement: End
			// While Statement: Begin
			%l_inner__index__14_d2_2, %l_out__list__index__16_d2_1 = scf.while(%l_inner__index__14_d3_0 = %tmp_70, %l_out__list__index__16_d3_0 = %l_out__list__index__16_d2_0) : (i32, i32) -> (i32, i32) {
				// While Statement: Condition Check
				%tmp_71 = arith.constant 3 : i2
				%tmp_72 = arith.extui %tmp_71 : i2 to i32
				%tmp_73 = arith.cmpi ult, %l_inner__index__14_d3_0, %tmp_72 : i32
				scf.condition(%tmp_73) %l_inner__index__14_d3_0, %l_out__list__index__16_d3_0 : i32, i32
			} do {
				// While Statement: Body Execution
			^tmp_74(%l_inner__index__14_d3_0: i32, %l_out__list__index__16_d3_0: i32):
				// Assignment Statement: Start
				%tmp_75 = arith.index_cast %l_out__list__index__16_d3_0: i32 to index
				%tmp_76 = arith.index_cast %l_inner__index__14_d3_0: i32 to index
				%tmp_77 = arith.index_cast %l_outer__index__15_d2_0: i32 to index
				%tmp_78 = memref.load %l_twoDList__13_d1_0[%tmp_77, %tmp_76] : memref<2x3xi32>
				memref.store %tmp_78, %l_out__list__12_d1_0[%tmp_75] : memref<6xi32>
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_79 = arith.constant 1 : i1
				%tmp_80 = arith.extui %tmp_79 : i1 to i32
				%tmp_81 = arith.addi %l_out__list__index__16_d3_0, %tmp_80 : i32
				// l_out__list__index__16_d3_1 aliased to tmp_81
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_82 = arith.constant 1 : i1
				%tmp_83 = arith.extui %tmp_82 : i1 to i32
				%tmp_84 = arith.addi %l_inner__index__14_d3_0, %tmp_83 : i32
				// l_inner__index__14_d3_1 aliased to tmp_84
				// Assignment Statement: End
				scf.yield %tmp_84, %tmp_81: i32, i32
			}
			// While Statement: End
			// Assignment Statement: Start
			%tmp_85 = arith.constant 1 : i1
			%tmp_86 = arith.extui %tmp_85 : i1 to i32
			%tmp_87 = arith.addi %l_outer__index__15_d2_0, %tmp_86 : i32
			// l_outer__index__15_d2_1 aliased to tmp_87
			// Assignment Statement: End
			scf.yield %l_inner__index__14_d2_2, %l_out__list__index__16_d2_1, %tmp_87: i32, i32, i32
		}
		// While Statement: End
		// Output Expression: Start
		%tmp_88 = arith.constant 0 : index
		%tmp_89 = memref.load %l_out__list__12_d1_0[%tmp_88] : memref<6xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_89: i32)
		%tmp_90 = arith.constant 1 : index
		%tmp_91 = memref.load %l_out__list__12_d1_0[%tmp_90] : memref<6xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_91: i32)
		%tmp_92 = arith.constant 2 : index
		%tmp_93 = memref.load %l_out__list__12_d1_0[%tmp_92] : memref<6xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_93: i32)
		%tmp_94 = arith.constant 3 : index
		%tmp_95 = memref.load %l_out__list__12_d1_0[%tmp_94] : memref<6xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_95: i32)
		%tmp_96 = arith.constant 4 : index
		%tmp_97 = memref.load %l_out__list__12_d1_0[%tmp_96] : memref<6xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_97: i32)
		%tmp_98 = arith.constant 5 : index
		%tmp_99 = memref.load %l_out__list__12_d1_0[%tmp_98] : memref<6xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_99: i32)
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
	%queue_from_DUT_Out, %queue_to_sink_In = fifo.create<i32>(6) : !fifo.input_port<i32>, !fifo.output_port<i32>

	// -- Instantiate actors (also known as nodes/instances)
	cal.create_instance @source "source" ()
		ports_out(%queue_from_source_Out: !fifo.input_port<i32>)
	cal.create_instance @DUT "DUT" ()
		ports_in(%queue_to_DUT_In: !fifo.output_port<i32>)
		ports_out(%queue_from_DUT_Out: !fifo.input_port<i32>)
	cal.create_instance @sink "sink" ()
		ports_in(%queue_to_sink_In: !fifo.output_port<i32>)

}

