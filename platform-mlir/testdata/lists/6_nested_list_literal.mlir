//-- Definition of actor class: Source
cal.actor @source ()
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
			%tmp_5 = arith.constant 1 : i1
			%tmp_6 = arith.extui %tmp_5 : i1 to i32
			// Evaluate global variable $eval2 done: assigned to tmp_6 above in this context.
			%tmp_7 = arith.cmpi slt, %tmp_4, %tmp_6 : i32
			cal.predicate_result %tmp_7 : i1
		}
		// Guard: End
		// Action Local Variable Decl: Start
		%tmp_8 = cal.get(%l_counter__7: !cal.state_ref<i32>) : i32
		%tmp_9 = arith.constant 100 : i7
		// Evaluate global variable $eval1.
		%tmp_10 = arith.constant 0 : i1
		%tmp_11 = arith.extui %tmp_10 : i1 to i32
		// Evaluate global variable $eval1 done: assigned to tmp_11 above in this context.
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
		fifo.print("Tx0: %i\n\00", %tmp_17) : (i32)
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

//-- Definition of actor class: NestedLiterals
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
		%l_twoDList__13_d1_0 = memref.alloc() : memref<2x3xi32>
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%l_out__list__12_d1_0 = memref.alloc() : memref<6xi32>
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_2 = arith.constant 0 : i1
		%tmp_3 = arith.extui %tmp_2 : i1 to i32
		// l_inner__index__14_d1_0 aliased to tmp_3
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_4 = arith.constant 0 : i1
		%tmp_5 = arith.extui %tmp_4 : i1 to i32
		// l_outer__index__15_d1_0 aliased to tmp_5
		// Action Local Variable Decl: End
		// Action Local Variable Decl: Start
		%tmp_6 = arith.constant 0 : i1
		%tmp_7 = arith.extui %tmp_6 : i1 to i32
		// l_out__list__index__16_d1_0 aliased to tmp_7
		// Action Local Variable Decl: End
		// While Statement: Begin
		%l_inner__index__14_d1_1, %l_outer__index__15_d1_1 = scf.while(%l_inner__index__14_d2_0 = %tmp_3, %l_outer__index__15_d2_0 = %tmp_5) : (i32, i32) -> (i32, i32) {
			// While Statement: Condition Check
			%tmp_8 = arith.constant 2 : i2
			%tmp_9 = arith.extui %tmp_8 : i2 to i32
			%tmp_10 = arith.cmpi ult, %l_outer__index__15_d2_0, %tmp_9 : i32
			scf.condition(%tmp_10) %l_inner__index__14_d2_0, %l_outer__index__15_d2_0 : i32, i32
		} do {
			// While Statement: Body Execution
		^tmp_11(%l_inner__index__14_d2_0: i32, %l_outer__index__15_d2_0: i32):
			// Assignment Statement: Start
			%tmp_12 = arith.constant 0 : i1
			%tmp_13 = arith.extui %tmp_12 : i1 to i32
			// l_inner__index__14_d2_1 aliased to tmp_13
			// Assignment Statement: End
			// While Statement: Begin
			%l_inner__index__14_d2_2 = scf.while(%l_inner__index__14_d3_0 = %tmp_13) : (i32) -> (i32) {
				// While Statement: Condition Check
				%tmp_14 = arith.constant 3 : i2
				%tmp_15 = arith.extui %tmp_14 : i2 to i32
				%tmp_16 = arith.cmpi ult, %l_inner__index__14_d3_0, %tmp_15 : i32
				scf.condition(%tmp_16) %l_inner__index__14_d3_0 : i32
			} do {
				// While Statement: Body Execution
			^tmp_17(%l_inner__index__14_d3_0: i32):
				// Assignment Statement: Start
				%tmp_18 = arith.index_cast %l_inner__index__14_d3_0: i32 to index
				%tmp_19 = arith.index_cast %l_outer__index__15_d2_0: i32 to index
				%tmp_20 = arith.constant 1 : i1
				%tmp_21 = arith.extui %tmp_20 : i1 to i32
				%tmp_22 = arith.addi %tmp_21, %l_inner__index__14_d3_0 : i32
				%tmp_23 = arith.constant 3 : i2
				%tmp_24 = arith.extui %tmp_23 : i2 to i32
				%tmp_25 = arith.muli %l_outer__index__15_d2_0, %tmp_24 : i32
				%tmp_26 = arith.addi %tmp_22, %tmp_25 : i32
				memref.store %tmp_26, %l_twoDList__13_d1_0[%tmp_19, %tmp_18] : memref<2x3xi32>
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_27 = arith.constant 1 : i1
				%tmp_28 = arith.extui %tmp_27 : i1 to i32
				%tmp_29 = arith.addi %l_inner__index__14_d3_0, %tmp_28 : i32
				// l_inner__index__14_d3_1 aliased to tmp_29
				// Assignment Statement: End
				scf.yield %tmp_29: i32
			}
			// While Statement: End
			// Assignment Statement: Start
			%tmp_30 = arith.constant 1 : i1
			%tmp_31 = arith.extui %tmp_30 : i1 to i32
			%tmp_32 = arith.addi %l_outer__index__15_d2_0, %tmp_31 : i32
			// l_outer__index__15_d2_1 aliased to tmp_32
			// Assignment Statement: End
			scf.yield %l_inner__index__14_d2_2, %tmp_32: i32, i32
		}
		// While Statement: End
		// Assignment Statement: Start
		%tmp_33 = arith.constant 0 : i1
		%tmp_34 = arith.extui %tmp_33 : i1 to i32
		// l_inner__index__14_d1_2 aliased to tmp_34
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_35 = arith.constant 0 : i1
		%tmp_36 = arith.extui %tmp_35 : i1 to i32
		// l_outer__index__15_d1_2 aliased to tmp_36
		// Assignment Statement: End
		// While Statement: Begin
		%l_inner__index__14_d1_3, %l_outer__index__15_d1_3 = scf.while(%l_inner__index__14_d2_0 = %tmp_34, %l_outer__index__15_d2_0 = %tmp_36) : (i32, i32) -> (i32, i32) {
			// While Statement: Condition Check
			%tmp_37 = arith.constant 2 : i2
			%tmp_38 = arith.extui %tmp_37 : i2 to i32
			%tmp_39 = arith.cmpi ult, %l_outer__index__15_d2_0, %tmp_38 : i32
			scf.condition(%tmp_39) %l_inner__index__14_d2_0, %l_outer__index__15_d2_0 : i32, i32
		} do {
			// While Statement: Body Execution
		^tmp_40(%l_inner__index__14_d2_0: i32, %l_outer__index__15_d2_0: i32):
			// Assignment Statement: Start
			%tmp_41 = arith.constant 0 : i1
			%tmp_42 = arith.extui %tmp_41 : i1 to i32
			// l_inner__index__14_d2_1 aliased to tmp_42
			// Assignment Statement: End
			// While Statement: Begin
			%l_inner__index__14_d2_2 = scf.while(%l_inner__index__14_d3_0 = %tmp_42) : (i32) -> (i32) {
				// While Statement: Condition Check
				%tmp_43 = arith.constant 3 : i2
				%tmp_44 = arith.extui %tmp_43 : i2 to i32
				%tmp_45 = arith.cmpi ult, %l_inner__index__14_d3_0, %tmp_44 : i32
				scf.condition(%tmp_45) %l_inner__index__14_d3_0 : i32
			} do {
				// While Statement: Body Execution
			^tmp_46(%l_inner__index__14_d3_0: i32):
				// Assignment Statement: Start
				%tmp_47 = arith.index_cast %l_inner__index__14_d3_0: i32 to index
				%tmp_48 = arith.index_cast %l_outer__index__15_d2_0: i32 to index
				%tmp_49 = arith.index_cast %l_inner__index__14_d3_0: i32 to index
				%tmp_50 = arith.index_cast %l_outer__index__15_d2_0: i32 to index
				%tmp_51 = memref.load %l_twoDList__13_d1_0[%tmp_50, %tmp_49] : memref<2x3xi32>
				%tmp_52 = arith.constant 20 : i5
				%tmp_53 = arith.extui %tmp_52 : i5 to i32
				%tmp_54 = arith.muli %tmp_51, %tmp_53 : i32
				memref.store %tmp_54, %l_twoDList__13_d1_0[%tmp_48, %tmp_47] : memref<2x3xi32>
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_55 = arith.constant 1 : i1
				%tmp_56 = arith.extui %tmp_55 : i1 to i32
				%tmp_57 = arith.addi %l_inner__index__14_d3_0, %tmp_56 : i32
				// l_inner__index__14_d3_1 aliased to tmp_57
				// Assignment Statement: End
				scf.yield %tmp_57: i32
			}
			// While Statement: End
			// Assignment Statement: Start
			%tmp_58 = arith.constant 1 : i1
			%tmp_59 = arith.extui %tmp_58 : i1 to i32
			%tmp_60 = arith.addi %l_outer__index__15_d2_0, %tmp_59 : i32
			// l_outer__index__15_d2_1 aliased to tmp_60
			// Assignment Statement: End
			scf.yield %l_inner__index__14_d2_2, %tmp_60: i32, i32
		}
		// While Statement: End
		// Assignment Statement: Start
		%tmp_61 = arith.constant 0 : i1
		%tmp_62 = arith.extui %tmp_61 : i1 to i32
		// l_inner__index__14_d1_4 aliased to tmp_62
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_63 = arith.constant 0 : i1
		%tmp_64 = arith.extui %tmp_63 : i1 to i32
		// l_outer__index__15_d1_4 aliased to tmp_64
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_65 = arith.constant 0 : i1
		%tmp_66 = arith.extui %tmp_65 : i1 to i32
		// l_out__list__index__16_d1_1 aliased to tmp_66
		// Assignment Statement: End
		// While Statement: Begin
		%l_inner__index__14_d1_5, %l_out__list__index__16_d1_2, %l_outer__index__15_d1_5 = scf.while(%l_inner__index__14_d2_0 = %tmp_62, %l_out__list__index__16_d2_0 = %tmp_66, %l_outer__index__15_d2_0 = %tmp_64) : (i32, i32, i32) -> (i32, i32, i32) {
			// While Statement: Condition Check
			%tmp_67 = arith.constant 2 : i2
			%tmp_68 = arith.extui %tmp_67 : i2 to i32
			%tmp_69 = arith.cmpi ult, %l_outer__index__15_d2_0, %tmp_68 : i32
			scf.condition(%tmp_69) %l_inner__index__14_d2_0, %l_out__list__index__16_d2_0, %l_outer__index__15_d2_0 : i32, i32, i32
		} do {
			// While Statement: Body Execution
		^tmp_70(%l_inner__index__14_d2_0: i32, %l_out__list__index__16_d2_0: i32, %l_outer__index__15_d2_0: i32):
			// Assignment Statement: Start
			%tmp_71 = arith.constant 0 : i1
			%tmp_72 = arith.extui %tmp_71 : i1 to i32
			// l_inner__index__14_d2_1 aliased to tmp_72
			// Assignment Statement: End
			// While Statement: Begin
			%l_inner__index__14_d2_2, %l_out__list__index__16_d2_1 = scf.while(%l_inner__index__14_d3_0 = %tmp_72, %l_out__list__index__16_d3_0 = %l_out__list__index__16_d2_0) : (i32, i32) -> (i32, i32) {
				// While Statement: Condition Check
				%tmp_73 = arith.constant 3 : i2
				%tmp_74 = arith.extui %tmp_73 : i2 to i32
				%tmp_75 = arith.cmpi ult, %l_inner__index__14_d3_0, %tmp_74 : i32
				scf.condition(%tmp_75) %l_inner__index__14_d3_0, %l_out__list__index__16_d3_0 : i32, i32
			} do {
				// While Statement: Body Execution
			^tmp_76(%l_inner__index__14_d3_0: i32, %l_out__list__index__16_d3_0: i32):
				// Assignment Statement: Start
				%tmp_77 = arith.index_cast %l_out__list__index__16_d3_0: i32 to index
				%tmp_78 = arith.index_cast %l_inner__index__14_d3_0: i32 to index
				%tmp_79 = arith.index_cast %l_outer__index__15_d2_0: i32 to index
				%tmp_80 = memref.load %l_twoDList__13_d1_0[%tmp_79, %tmp_78] : memref<2x3xi32>
				memref.store %tmp_80, %l_out__list__12_d1_0[%tmp_77] : memref<6xi32>
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_81 = arith.constant 1 : i1
				%tmp_82 = arith.extui %tmp_81 : i1 to i32
				%tmp_83 = arith.addi %l_out__list__index__16_d3_0, %tmp_82 : i32
				// l_out__list__index__16_d3_1 aliased to tmp_83
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_84 = arith.constant 1 : i1
				%tmp_85 = arith.extui %tmp_84 : i1 to i32
				%tmp_86 = arith.addi %l_inner__index__14_d3_0, %tmp_85 : i32
				// l_inner__index__14_d3_1 aliased to tmp_86
				// Assignment Statement: End
				scf.yield %tmp_86, %tmp_83: i32, i32
			}
			// While Statement: End
			// Assignment Statement: Start
			%tmp_87 = arith.constant 1 : i1
			%tmp_88 = arith.extui %tmp_87 : i1 to i32
			%tmp_89 = arith.addi %l_outer__index__15_d2_0, %tmp_88 : i32
			// l_outer__index__15_d2_1 aliased to tmp_89
			// Assignment Statement: End
			scf.yield %l_inner__index__14_d2_2, %l_out__list__index__16_d2_1, %tmp_89: i32, i32, i32
		}
		// While Statement: End
		// Assignment Statement: Start
		%tmp_90 = arith.constant 0 : i1
		%tmp_91 = arith.extui %tmp_90 : i1 to i32
		cal.set(%l_$state: !cal.state_ref<i32>, %tmp_91: i32)
		// Assignment Statement: End
		// Output Expression: Start
		%tmp_92 = arith.constant 0 : index
		%tmp_93 = memref.load %l_out__list__12_d1_0[%tmp_92] : memref<6xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_93: i32)
		%tmp_94 = arith.constant 1 : index
		%tmp_95 = memref.load %l_out__list__12_d1_0[%tmp_94] : memref<6xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_95: i32)
		%tmp_96 = arith.constant 2 : index
		%tmp_97 = memref.load %l_out__list__12_d1_0[%tmp_96] : memref<6xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_97: i32)
		%tmp_98 = arith.constant 3 : index
		%tmp_99 = memref.load %l_out__list__12_d1_0[%tmp_98] : memref<6xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_99: i32)
		%tmp_100 = arith.constant 4 : index
		%tmp_101 = memref.load %l_out__list__12_d1_0[%tmp_100] : memref<6xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_101: i32)
		%tmp_102 = arith.constant 5 : index
		%tmp_103 = memref.load %l_out__list__12_d1_0[%tmp_102] : memref<6xi32>
		fifo.push(%Out: !fifo.input_port<i32>, %tmp_103: i32)
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
		%l_t__5_d1_0 = fifo.pop(%In: !fifo.output_port<i32>) : i32
		// Input Pattern: End
		// Call Statement: Start
		fifo.print("Rx0: %i\n\00", %l_t__5_d1_0) : (i32)
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

