//-- Definition of actor class: NestedListSimple
dfg.process @NestedListSimple
	inputs(%In: i32)
	outputs(%Out: i32)
{
	dfg.loop inputs(%In: i32)
	{
		// -- Actor body
		// Block Statement: Begin
		//     Variable declarations attached to block statement: Begin
		%l_t1__1_d0_0 = dfg.pull %In : i32
		%l_list__2d__4_d0_0 = memref.alloc() : memref<4x4xi32>
		%tmp_0 = arith.constant 0 : i1
		%tmp_1 = arith.extui %tmp_0 : i1 to i32
		// l_list2d__index__x__5_d0_0 aliased to tmp_1
		%tmp_2 = arith.constant 0 : i1
		%tmp_3 = arith.extui %tmp_2 : i1 to i32
		// l_list2d__index__y__6_d0_0 aliased to tmp_3
		%l_list__flat__3_d0_0 = memref.alloc() : memref<16xi32>
		%tmp_4 = arith.constant 0 : i1
		%tmp_5 = arith.extui %tmp_4 : i1 to i32
		// l_list__flat__index__7_d0_0 aliased to tmp_5
		//     Variable declarations attached to block statement: End
		// While Statement: Begin
		%l_list2d__index__x__5_d0_1, %l_list2d__index__y__6_d0_1 = scf.while(%l_list2d__index__x__5_d1_0 = %tmp_1, %l_list2d__index__y__6_d1_0 = %tmp_3) : (i32, i32) -> (i32, i32) {
			// While Statement: Condition Check
			%tmp_6 = arith.constant 4 : i3
			%tmp_7 = arith.extui %tmp_6 : i3 to i32
			%tmp_8 = arith.cmpi ult, %l_list2d__index__x__5_d1_0, %tmp_7 : i32
			scf.condition(%tmp_8) %l_list2d__index__x__5_d1_0, %l_list2d__index__y__6_d1_0 : i32, i32
		} do {
			// While Statement: Body Execution
		^tmp_9(%l_list2d__index__x__5_d1_0: i32, %l_list2d__index__y__6_d1_0: i32):
			// Assignment Statement: Start
			%tmp_10 = arith.constant 0 : i1
			%tmp_11 = arith.extui %tmp_10 : i1 to i32
			// l_list2d__index__y__6_d1_1 aliased to tmp_11
			// Assignment Statement: End
			// While Statement: Begin
			%l_list2d__index__y__6_d1_2 = scf.while(%l_list2d__index__y__6_d2_0 = %tmp_11) : (i32) -> (i32) {
				// While Statement: Condition Check
				%tmp_12 = arith.constant 4 : i3
				%tmp_13 = arith.extui %tmp_12 : i3 to i32
				%tmp_14 = arith.cmpi ult, %l_list2d__index__y__6_d2_0, %tmp_13 : i32
				scf.condition(%tmp_14) %l_list2d__index__y__6_d2_0 : i32
			} do {
				// While Statement: Body Execution
			^tmp_15(%l_list2d__index__y__6_d2_0: i32):
				// Assignment Statement: Start
				%tmp_16 = arith.index_cast %l_list2d__index__y__6_d2_0: i32 to index
				%tmp_17 = arith.index_cast %l_list2d__index__x__5_d1_0: i32 to index
				%tmp_18 = arith.constant 4 : i3
				%tmp_19 = arith.extui %tmp_18 : i3 to i32
				%tmp_20 = arith.muli %l_list2d__index__x__5_d1_0, %tmp_19 : i32
				%tmp_21 = arith.addi %tmp_20, %l_list2d__index__y__6_d2_0 : i32
				memref.store %tmp_21, %l_list__2d__4_d0_0[%tmp_16, %tmp_17] : memref<4x4xi32>
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_22 = arith.constant 1 : i1
				%tmp_23 = arith.extui %tmp_22 : i1 to i32
				%tmp_24 = arith.addi %l_list2d__index__y__6_d2_0, %tmp_23 : i32
				// l_list2d__index__y__6_d2_1 aliased to tmp_24
				// Assignment Statement: End
				scf.yield %tmp_24: i32
			}
			// While Statement: End
			// Assignment Statement: Start
			%tmp_25 = arith.constant 1 : i1
			%tmp_26 = arith.extui %tmp_25 : i1 to i32
			%tmp_27 = arith.addi %l_list2d__index__x__5_d1_0, %tmp_26 : i32
			// l_list2d__index__x__5_d1_1 aliased to tmp_27
			// Assignment Statement: End
			scf.yield %tmp_27, %l_list2d__index__y__6_d1_2: i32, i32
		}
		// While Statement: End
		// Assignment Statement: Start
		%tmp_28 = arith.constant 0 : i1
		%tmp_29 = arith.extui %tmp_28 : i1 to i32
		// l_list2d__index__x__5_d0_2 aliased to tmp_29
		// Assignment Statement: End
		// While Statement: Begin
		%l_list2d__index__x__5_d0_3, %l_list2d__index__y__6_d0_2, %l_list__flat__index__7_d0_1 = scf.while(%l_list2d__index__x__5_d1_0 = %tmp_29, %l_list2d__index__y__6_d1_0 = %l_list2d__index__y__6_d0_1, %l_list__flat__index__7_d1_0 = %tmp_5) : (i32, i32, i32) -> (i32, i32, i32) {
			// While Statement: Condition Check
			%tmp_30 = arith.constant 4 : i3
			%tmp_31 = arith.extui %tmp_30 : i3 to i32
			%tmp_32 = arith.cmpi ult, %l_list2d__index__x__5_d1_0, %tmp_31 : i32
			scf.condition(%tmp_32) %l_list2d__index__x__5_d1_0, %l_list2d__index__y__6_d1_0, %l_list__flat__index__7_d1_0 : i32, i32, i32
		} do {
			// While Statement: Body Execution
		^tmp_33(%l_list2d__index__x__5_d1_0: i32, %l_list2d__index__y__6_d1_0: i32, %l_list__flat__index__7_d1_0: i32):
			// Assignment Statement: Start
			%tmp_34 = arith.constant 0 : i1
			%tmp_35 = arith.extui %tmp_34 : i1 to i32
			// l_list2d__index__y__6_d1_1 aliased to tmp_35
			// Assignment Statement: End
			// While Statement: Begin
			%l_list2d__index__y__6_d1_2, %l_list__flat__index__7_d1_1 = scf.while(%l_list2d__index__y__6_d2_0 = %tmp_35, %l_list__flat__index__7_d2_0 = %l_list__flat__index__7_d1_0) : (i32, i32) -> (i32, i32) {
				// While Statement: Condition Check
				%tmp_36 = arith.constant 4 : i3
				%tmp_37 = arith.extui %tmp_36 : i3 to i32
				%tmp_38 = arith.cmpi ult, %l_list2d__index__y__6_d2_0, %tmp_37 : i32
				scf.condition(%tmp_38) %l_list2d__index__y__6_d2_0, %l_list__flat__index__7_d2_0 : i32, i32
			} do {
				// While Statement: Body Execution
			^tmp_39(%l_list2d__index__y__6_d2_0: i32, %l_list__flat__index__7_d2_0: i32):
				// Assignment Statement: Start
				%tmp_40 = arith.index_cast %l_list__flat__index__7_d2_0: i32 to index
				%tmp_41 = arith.index_cast %l_list2d__index__y__6_d2_0: i32 to index
				%tmp_42 = arith.index_cast %l_list2d__index__x__5_d1_0: i32 to index
				%tmp_43 = memref.load %l_list__2d__4_d0_0[%tmp_41, %tmp_42] : memref<4x4xi32>
				memref.store %tmp_43, %l_list__flat__3_d0_0[%tmp_40] : memref<16xi32>
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_44 = arith.constant 1 : i1
				%tmp_45 = arith.extui %tmp_44 : i1 to i32
				%tmp_46 = arith.addi %l_list2d__index__y__6_d2_0, %tmp_45 : i32
				// l_list2d__index__y__6_d2_1 aliased to tmp_46
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_47 = arith.constant 1 : i1
				%tmp_48 = arith.extui %tmp_47 : i1 to i32
				%tmp_49 = arith.addi %l_list__flat__index__7_d2_0, %tmp_48 : i32
				// l_list__flat__index__7_d2_1 aliased to tmp_49
				// Assignment Statement: End
				scf.yield %tmp_46, %tmp_49: i32, i32
			}
			// While Statement: End
			// Assignment Statement: Start
			%tmp_50 = arith.constant 1 : i1
			%tmp_51 = arith.extui %tmp_50 : i1 to i32
			%tmp_52 = arith.addi %l_list2d__index__x__5_d1_0, %tmp_51 : i32
			// l_list2d__index__x__5_d1_1 aliased to tmp_52
			// Assignment Statement: End
			scf.yield %tmp_52, %l_list2d__index__y__6_d1_2, %l_list__flat__index__7_d1_1: i32, i32, i32
		}
		// While Statement: End
		// Stmt Write Preprocessing: Begin
		%tmp_53 = arith.constant 0: index
		%tmp_54 = memref.load %l_list__flat__3_d0_0[%tmp_53] : memref<16xi32>
		%tmp_55 = arith.constant 1: index
		%tmp_56 = memref.load %l_list__flat__3_d0_0[%tmp_55] : memref<16xi32>
		%tmp_57 = arith.constant 2: index
		%tmp_58 = memref.load %l_list__flat__3_d0_0[%tmp_57] : memref<16xi32>
		%tmp_59 = arith.constant 3: index
		%tmp_60 = memref.load %l_list__flat__3_d0_0[%tmp_59] : memref<16xi32>
		%tmp_61 = arith.constant 4: index
		%tmp_62 = memref.load %l_list__flat__3_d0_0[%tmp_61] : memref<16xi32>
		%tmp_63 = arith.constant 5: index
		%tmp_64 = memref.load %l_list__flat__3_d0_0[%tmp_63] : memref<16xi32>
		%tmp_65 = arith.constant 6: index
		%tmp_66 = memref.load %l_list__flat__3_d0_0[%tmp_65] : memref<16xi32>
		%tmp_67 = arith.constant 7: index
		%tmp_68 = memref.load %l_list__flat__3_d0_0[%tmp_67] : memref<16xi32>
		%tmp_69 = arith.constant 8: index
		%tmp_70 = memref.load %l_list__flat__3_d0_0[%tmp_69] : memref<16xi32>
		%tmp_71 = arith.constant 9: index
		%tmp_72 = memref.load %l_list__flat__3_d0_0[%tmp_71] : memref<16xi32>
		%tmp_73 = arith.constant 10: index
		%tmp_74 = memref.load %l_list__flat__3_d0_0[%tmp_73] : memref<16xi32>
		%tmp_75 = arith.constant 11: index
		%tmp_76 = memref.load %l_list__flat__3_d0_0[%tmp_75] : memref<16xi32>
		%tmp_77 = arith.constant 12: index
		%tmp_78 = memref.load %l_list__flat__3_d0_0[%tmp_77] : memref<16xi32>
		%tmp_79 = arith.constant 13: index
		%tmp_80 = memref.load %l_list__flat__3_d0_0[%tmp_79] : memref<16xi32>
		%tmp_81 = arith.constant 14: index
		%tmp_82 = memref.load %l_list__flat__3_d0_0[%tmp_81] : memref<16xi32>
		%tmp_83 = arith.constant 15: index
		%tmp_84 = memref.load %l_list__flat__3_d0_0[%tmp_83] : memref<16xi32>
		// Stmt Write Preprocessing: End
		// StmtConsume not implemented: consume happens on peaking right now
		//     dfg.push operations deferred from StmtWrites: Begin
		dfg.push(%tmp_54) %Out : i32
		dfg.push(%tmp_56) %Out : i32
		dfg.push(%tmp_58) %Out : i32
		dfg.push(%tmp_60) %Out : i32
		dfg.push(%tmp_62) %Out : i32
		dfg.push(%tmp_64) %Out : i32
		dfg.push(%tmp_66) %Out : i32
		dfg.push(%tmp_68) %Out : i32
		dfg.push(%tmp_70) %Out : i32
		dfg.push(%tmp_72) %Out : i32
		dfg.push(%tmp_74) %Out : i32
		dfg.push(%tmp_76) %Out : i32
		dfg.push(%tmp_78) %Out : i32
		dfg.push(%tmp_80) %Out : i32
		dfg.push(%tmp_82) %Out : i32
		dfg.push(%tmp_84) %Out : i32
		//     dfg.push operations deferred from StmtWrites: End
		// Block Statement: End
	}

}

// -- Top Network: Defines structure of actor application
func.func @top(%In: i32) -> (i32)
{

	// -- Instantiate channels between actors
	%queue_from_In, %queue_to_NestedListSimple_In = dfg.channel(4096) : i32
	%queue_from_NestedListSimple_Out, %queue_to_Out = dfg.channel(4096) : i32

	// -- Connect input channels to arguments
	dfg.push(%In) %queue_from_In : i32

	// -- Instantiate actors (also known as nodes/instances)
	dfg.instantiate @NestedListSimple // Instance name: NestedListSimple
		inputs(%queue_to_NestedListSimple_In)
		outputs(%queue_from_NestedListSimple_Out) :
		(i32) -> (i32)

	// -- Connect output channels to return arguments
	%Out = dfg.pull %queue_to_Out : i32

	// -- Return
	func.return %Out: i32
}

