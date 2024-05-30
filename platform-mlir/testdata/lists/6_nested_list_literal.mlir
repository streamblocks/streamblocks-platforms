//-- Definition of actor class: NestedLiterals
dfg.process @NestedLiterals
	inputs(%In: i32)
	outputs(%Out: i32)
{
	dfg.loop inputs(%In: i32)
	{
		// -- Actor body
		// Block Statement: Begin
		//     Variable declarations attached to block statement: Begin
		%l_t1__1_d0_0 = dfg.pull %In : i32
		%tmp_0 = memref.alloc() : memref<2x3xi32>
		%tmp_1 = arith.constant 0: index
		%tmp_2 = arith.constant 0: index
		%tmp_3 = arith.constant 10 : i4
		%tmp_4 = arith.extui %tmp_3 : i4 to i32
		memref.store %tmp_4, %tmp_0[%tmp_1, %tmp_2] : memref<2x3xi32>
		%tmp_5 = arith.constant 1: index
		%tmp_6 = arith.constant 20 : i5
		%tmp_7 = arith.extui %tmp_6 : i5 to i32
		memref.store %tmp_7, %tmp_0[%tmp_1, %tmp_5] : memref<2x3xi32>
		%tmp_8 = arith.constant 2: index
		%tmp_9 = arith.constant 30 : i5
		%tmp_10 = arith.extui %tmp_9 : i5 to i32
		memref.store %tmp_10, %tmp_0[%tmp_1, %tmp_8] : memref<2x3xi32>
		%tmp_11 = arith.constant 1: index
		%tmp_12 = arith.constant 0: index
		%tmp_13 = arith.constant 40 : i6
		%tmp_14 = arith.extui %tmp_13 : i6 to i32
		memref.store %tmp_14, %tmp_0[%tmp_11, %tmp_12] : memref<2x3xi32>
		%tmp_15 = arith.constant 1: index
		%tmp_16 = arith.constant 50 : i6
		%tmp_17 = arith.extui %tmp_16 : i6 to i32
		memref.store %tmp_17, %tmp_0[%tmp_11, %tmp_15] : memref<2x3xi32>
		%tmp_18 = arith.constant 2: index
		%tmp_19 = arith.constant 60 : i6
		%tmp_20 = arith.extui %tmp_19 : i6 to i32
		memref.store %tmp_20, %tmp_0[%tmp_11, %tmp_18] : memref<2x3xi32>
		// l_twoDList__4_d0_0 aliased to tmp_0
		%l_out__list__3_d0_0 = memref.alloc() : memref<6xi32>
		%tmp_21 = arith.constant 0 : i1
		%tmp_22 = arith.extui %tmp_21 : i1 to i32
		// l_inner__index__5_d0_0 aliased to tmp_22
		%tmp_23 = arith.constant 0 : i1
		%tmp_24 = arith.extui %tmp_23 : i1 to i32
		// l_outer__index__6_d0_0 aliased to tmp_24
		%tmp_25 = arith.constant 0 : i1
		%tmp_26 = arith.extui %tmp_25 : i1 to i32
		// l_out__list__index__7_d0_0 aliased to tmp_26
		//     Variable declarations attached to block statement: End
		// While Statement: Begin
		%l_inner__index__5_d0_1, %l_out__list__index__7_d0_1, %l_outer__index__6_d0_1 = scf.while(%l_inner__index__5_d1_0 = %tmp_22, %l_out__list__index__7_d1_0 = %tmp_26, %l_outer__index__6_d1_0 = %tmp_24) : (i32, i32, i32) -> (i32, i32, i32) {
			// While Statement: Condition Check
			%tmp_27 = arith.constant 2 : i2
			%tmp_28 = arith.extui %tmp_27 : i2 to i32
			%tmp_29 = arith.cmpi ult, %l_outer__index__6_d1_0, %tmp_28 : i32
			scf.condition(%tmp_29) %l_inner__index__5_d1_0, %l_out__list__index__7_d1_0, %l_outer__index__6_d1_0 : i32, i32, i32
		} do {
			// While Statement: Body Execution
		^tmp_30(%l_inner__index__5_d1_0: i32, %l_out__list__index__7_d1_0: i32, %l_outer__index__6_d1_0: i32):
			// Assignment Statement: Start
			%tmp_31 = arith.constant 0 : i1
			%tmp_32 = arith.extui %tmp_31 : i1 to i32
			// l_inner__index__5_d1_1 aliased to tmp_32
			// Assignment Statement: End
			// While Statement: Begin
			%l_inner__index__5_d1_2, %l_out__list__index__7_d1_1 = scf.while(%l_inner__index__5_d2_0 = %tmp_32, %l_out__list__index__7_d2_0 = %l_out__list__index__7_d1_0) : (i32, i32) -> (i32, i32) {
				// While Statement: Condition Check
				%tmp_33 = arith.constant 3 : i2
				%tmp_34 = arith.extui %tmp_33 : i2 to i32
				%tmp_35 = arith.cmpi ult, %l_inner__index__5_d2_0, %tmp_34 : i32
				scf.condition(%tmp_35) %l_inner__index__5_d2_0, %l_out__list__index__7_d2_0 : i32, i32
			} do {
				// While Statement: Body Execution
			^tmp_36(%l_inner__index__5_d2_0: i32, %l_out__list__index__7_d2_0: i32):
				// Assignment Statement: Start
				%tmp_37 = arith.index_cast %l_inner__index__5_d2_0: i32 to index
				%tmp_38 = arith.index_cast %l_outer__index__6_d1_0: i32 to index
				%tmp_39 = arith.index_cast %l_inner__index__5_d2_0: i32 to index
				%tmp_40 = arith.index_cast %l_outer__index__6_d1_0: i32 to index
				%tmp_41 = memref.load %tmp_0[%tmp_40, %tmp_39] : memref<2x3xi32>
				%tmp_42 = arith.constant 1 : i1
				%tmp_43 = arith.extui %tmp_42 : i1 to i32
				%tmp_44 = arith.addi %tmp_41, %tmp_43 : i32
				memref.store %tmp_44, %tmp_0[%tmp_38, %tmp_37] : memref<2x3xi32>
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_45 = arith.index_cast %l_out__list__index__7_d2_0: i32 to index
				%tmp_46 = arith.index_cast %l_inner__index__5_d2_0: i32 to index
				%tmp_47 = arith.index_cast %l_outer__index__6_d1_0: i32 to index
				%tmp_48 = memref.load %tmp_0[%tmp_47, %tmp_46] : memref<2x3xi32>
				memref.store %tmp_48, %l_out__list__3_d0_0[%tmp_45] : memref<6xi32>
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_49 = arith.constant 1 : i1
				%tmp_50 = arith.extui %tmp_49 : i1 to i32
				%tmp_51 = arith.addi %l_out__list__index__7_d2_0, %tmp_50 : i32
				// l_out__list__index__7_d2_1 aliased to tmp_51
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_52 = arith.constant 1 : i1
				%tmp_53 = arith.extui %tmp_52 : i1 to i32
				%tmp_54 = arith.addi %l_inner__index__5_d2_0, %tmp_53 : i32
				// l_inner__index__5_d2_1 aliased to tmp_54
				// Assignment Statement: End
				scf.yield %tmp_54, %tmp_51: i32, i32
			}
			// While Statement: End
			// Assignment Statement: Start
			%tmp_55 = arith.constant 1 : i1
			%tmp_56 = arith.extui %tmp_55 : i1 to i32
			%tmp_57 = arith.addi %l_outer__index__6_d1_0, %tmp_56 : i32
			// l_outer__index__6_d1_1 aliased to tmp_57
			// Assignment Statement: End
			scf.yield %l_inner__index__5_d1_2, %l_out__list__index__7_d1_1, %tmp_57: i32, i32, i32
		}
		// While Statement: End
		// Stmt Write Preprocessing: Begin
		%tmp_58 = arith.constant 0: index
		%tmp_59 = memref.load %l_out__list__3_d0_0[%tmp_58] : memref<6xi32>
		%tmp_60 = arith.constant 1: index
		%tmp_61 = memref.load %l_out__list__3_d0_0[%tmp_60] : memref<6xi32>
		%tmp_62 = arith.constant 2: index
		%tmp_63 = memref.load %l_out__list__3_d0_0[%tmp_62] : memref<6xi32>
		%tmp_64 = arith.constant 3: index
		%tmp_65 = memref.load %l_out__list__3_d0_0[%tmp_64] : memref<6xi32>
		%tmp_66 = arith.constant 4: index
		%tmp_67 = memref.load %l_out__list__3_d0_0[%tmp_66] : memref<6xi32>
		%tmp_68 = arith.constant 5: index
		%tmp_69 = memref.load %l_out__list__3_d0_0[%tmp_68] : memref<6xi32>
		// Stmt Write Preprocessing: End
		// StmtConsume not implemented: consume happens on peaking right now
		//     dfg.push operations deferred from StmtWrites: Begin
		dfg.push(%tmp_59) %Out : i32
		dfg.push(%tmp_61) %Out : i32
		dfg.push(%tmp_63) %Out : i32
		dfg.push(%tmp_65) %Out : i32
		dfg.push(%tmp_67) %Out : i32
		dfg.push(%tmp_69) %Out : i32
		//     dfg.push operations deferred from StmtWrites: End
		// Block Statement: End
	}

}

// -- Top Network: Defines structure of actor application
func.func @top(%In: i32) -> (i32)
{

	// -- Instantiate channels between actors
	%queue_from_In, %queue_to_NestedLiterals_In = dfg.channel(4096) : i32
	%queue_from_NestedLiterals_Out, %queue_to_Out = dfg.channel(4096) : i32

	// -- Connect input channels to arguments
	dfg.push(%In) %queue_from_In : i32

	// -- Instantiate actors (also known as nodes/instances)
	dfg.instantiate @NestedLiterals // Instance name: NestedLiterals
		inputs(%queue_to_NestedLiterals_In)
		outputs(%queue_from_NestedLiterals_Out) :
		(i32) -> (i32)

	// -- Connect output channels to return arguments
	%Out = dfg.pull %queue_to_Out : i32

	// -- Return
	func.return %Out: i32
}

