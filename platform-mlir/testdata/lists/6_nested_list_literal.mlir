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
		%tmp_0 = memref.alloc() : memref<2x3xi6>
		%tmp_1 = arith.constant 0: index
		%tmp_2 = arith.constant 0: index
		%tmp_3 = arith.constant 10 : i4
		%tmp_4 = arith.extui %tmp_3 : i4 to i6
		memref.store %tmp_4, %tmp_0[%tmp_1, %tmp_2] : memref<2x3xi6>
		%tmp_5 = arith.constant 1: index
		%tmp_6 = arith.constant 20 : i5
		%tmp_7 = arith.extui %tmp_6 : i5 to i6
		memref.store %tmp_7, %tmp_0[%tmp_1, %tmp_5] : memref<2x3xi6>
		%tmp_8 = arith.constant 2: index
		%tmp_9 = arith.constant 30 : i5
		%tmp_10 = arith.extui %tmp_9 : i5 to i6
		memref.store %tmp_10, %tmp_0[%tmp_1, %tmp_8] : memref<2x3xi6>
		%tmp_11 = arith.constant 1: index
		%tmp_12 = arith.constant 0: index
		%tmp_13 = arith.constant 40 : i6
		memref.store %tmp_13, %tmp_0[%tmp_11, %tmp_12] : memref<2x3xi6>
		%tmp_14 = arith.constant 1: index
		%tmp_15 = arith.constant 50 : i6
		memref.store %tmp_15, %tmp_0[%tmp_11, %tmp_14] : memref<2x3xi6>
		%tmp_16 = arith.constant 2: index
		%tmp_17 = arith.constant 60 : i6
		memref.store %tmp_17, %tmp_0[%tmp_11, %tmp_16] : memref<2x3xi6>
		%tmp_18 = memref.alloc() : memref<2x3xi32>
		%tmp_19 = arith.constant 0: index
		%tmp_20 = arith.constant 0: index
		%tmp_21 = memref.load %tmp_0[%tmp_19, %tmp_20] : memref<2x3xi6>
		%tmp_22 = arith.extui %tmp_21 : i6 to i32
		memref.store %tmp_22, %tmp_18[%tmp_19, %tmp_20] : memref<2x3xi32>
		%tmp_23 = arith.constant 1: index
		%tmp_24 = memref.load %tmp_0[%tmp_19, %tmp_23] : memref<2x3xi6>
		%tmp_25 = arith.extui %tmp_24 : i6 to i32
		memref.store %tmp_25, %tmp_18[%tmp_19, %tmp_23] : memref<2x3xi32>
		%tmp_26 = arith.constant 2: index
		%tmp_27 = memref.load %tmp_0[%tmp_19, %tmp_26] : memref<2x3xi6>
		%tmp_28 = arith.extui %tmp_27 : i6 to i32
		memref.store %tmp_28, %tmp_18[%tmp_19, %tmp_26] : memref<2x3xi32>
		%tmp_29 = arith.constant 1: index
		%tmp_30 = arith.constant 0: index
		%tmp_31 = memref.load %tmp_0[%tmp_29, %tmp_30] : memref<2x3xi6>
		%tmp_32 = arith.extui %tmp_31 : i6 to i32
		memref.store %tmp_32, %tmp_18[%tmp_29, %tmp_30] : memref<2x3xi32>
		%tmp_33 = arith.constant 1: index
		%tmp_34 = memref.load %tmp_0[%tmp_29, %tmp_33] : memref<2x3xi6>
		%tmp_35 = arith.extui %tmp_34 : i6 to i32
		memref.store %tmp_35, %tmp_18[%tmp_29, %tmp_33] : memref<2x3xi32>
		%tmp_36 = arith.constant 2: index
		%tmp_37 = memref.load %tmp_0[%tmp_29, %tmp_36] : memref<2x3xi6>
		%tmp_38 = arith.extui %tmp_37 : i6 to i32
		memref.store %tmp_38, %tmp_18[%tmp_29, %tmp_36] : memref<2x3xi32>
		// l_twoDList__4_d0_0 aliased to tmp_18
		%l_out__list__3_d0_0 = memref.alloc() : memref<6xi32>
		%tmp_39 = arith.constant 0 : i1
		%tmp_40 = arith.extui %tmp_39 : i1 to i32
		// l_inner__index__5_d0_0 aliased to tmp_40
		%tmp_41 = arith.constant 0 : i1
		%tmp_42 = arith.extui %tmp_41 : i1 to i32
		// l_outer__index__6_d0_0 aliased to tmp_42
		%tmp_43 = arith.constant 0 : i1
		%tmp_44 = arith.extui %tmp_43 : i1 to i32
		// l_out__list__index__7_d0_0 aliased to tmp_44
		//     Variable declarations attached to block statement: End
		// While Statement: Begin
		%l_inner__index__5_d0_1, %l_out__list__index__7_d0_1, %l_outer__index__6_d0_1 = scf.while(%l_inner__index__5_d1_0 = %tmp_40, %l_out__list__index__7_d1_0 = %tmp_44, %l_outer__index__6_d1_0 = %tmp_42) : (i32, i32, i32) -> (i32, i32, i32) {
			// While Statement: Condition Check
			%tmp_45 = arith.constant 2 : i2
			%tmp_46 = arith.extui %tmp_45 : i2 to i32
			%tmp_47 = arith.cmpi ult, %l_outer__index__6_d1_0, %tmp_46 : i32
			scf.condition(%tmp_47) %l_inner__index__5_d1_0, %l_out__list__index__7_d1_0, %l_outer__index__6_d1_0 : i32, i32, i32
		} do {
			// While Statement: Body Execution
		^tmp_48(%l_inner__index__5_d1_0: i32, %l_out__list__index__7_d1_0: i32, %l_outer__index__6_d1_0: i32):
			// Assignment Statement: Start
			%tmp_49 = arith.constant 0 : i1
			%tmp_50 = arith.extui %tmp_49 : i1 to i32
			// l_inner__index__5_d1_1 aliased to tmp_50
			// Assignment Statement: End
			// While Statement: Begin
			%l_inner__index__5_d1_2, %l_out__list__index__7_d1_1 = scf.while(%l_inner__index__5_d2_0 = %tmp_50, %l_out__list__index__7_d2_0 = %l_out__list__index__7_d1_0) : (i32, i32) -> (i32, i32) {
				// While Statement: Condition Check
				%tmp_51 = arith.constant 3 : i2
				%tmp_52 = arith.extui %tmp_51 : i2 to i32
				%tmp_53 = arith.cmpi ult, %l_inner__index__5_d2_0, %tmp_52 : i32
				scf.condition(%tmp_53) %l_inner__index__5_d2_0, %l_out__list__index__7_d2_0 : i32, i32
			} do {
				// While Statement: Body Execution
			^tmp_54(%l_inner__index__5_d2_0: i32, %l_out__list__index__7_d2_0: i32):
				// Assignment Statement: Start
				%tmp_55 = arith.index_cast %l_inner__index__5_d2_0: i32 to index
				%tmp_56 = arith.index_cast %l_outer__index__6_d1_0: i32 to index
				%tmp_57 = arith.index_cast %l_inner__index__5_d2_0: i32 to index
				%tmp_58 = arith.index_cast %l_outer__index__6_d1_0: i32 to index
				%tmp_59 = memref.load %tmp_18[%tmp_58, %tmp_57] : memref<2x3xi32>
				%tmp_60 = arith.constant 1 : i1
				%tmp_61 = arith.extui %tmp_60 : i1 to i32
				%tmp_62 = arith.addi %tmp_59, %tmp_61 : i32
				memref.store %tmp_62, %tmp_18[%tmp_56, %tmp_55] : memref<2x3xi32>
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_63 = arith.index_cast %l_out__list__index__7_d2_0: i32 to index
				%tmp_64 = arith.index_cast %l_inner__index__5_d2_0: i32 to index
				%tmp_65 = arith.index_cast %l_outer__index__6_d1_0: i32 to index
				%tmp_66 = memref.load %tmp_18[%tmp_65, %tmp_64] : memref<2x3xi32>
				memref.store %tmp_66, %l_out__list__3_d0_0[%tmp_63] : memref<6xi32>
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_67 = arith.constant 1 : i1
				%tmp_68 = arith.extui %tmp_67 : i1 to i32
				%tmp_69 = arith.addi %l_out__list__index__7_d2_0, %tmp_68 : i32
				// l_out__list__index__7_d2_1 aliased to tmp_69
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_70 = arith.constant 1 : i1
				%tmp_71 = arith.extui %tmp_70 : i1 to i32
				%tmp_72 = arith.addi %l_inner__index__5_d2_0, %tmp_71 : i32
				// l_inner__index__5_d2_1 aliased to tmp_72
				// Assignment Statement: End
				scf.yield %tmp_72, %tmp_69: i32, i32
			}
			// While Statement: End
			// Assignment Statement: Start
			%tmp_73 = arith.constant 1 : i1
			%tmp_74 = arith.extui %tmp_73 : i1 to i32
			%tmp_75 = arith.addi %l_outer__index__6_d1_0, %tmp_74 : i32
			// l_outer__index__6_d1_1 aliased to tmp_75
			// Assignment Statement: End
			scf.yield %l_inner__index__5_d1_2, %l_out__list__index__7_d1_1, %tmp_75: i32, i32, i32
		}
		// While Statement: End
		// Stmt Write Preprocessing: Begin
		%tmp_76 = arith.constant 0: index
		%tmp_77 = memref.load %l_out__list__3_d0_0[%tmp_76] : memref<6xi32>
		%tmp_78 = arith.constant 1: index
		%tmp_79 = memref.load %l_out__list__3_d0_0[%tmp_78] : memref<6xi32>
		%tmp_80 = arith.constant 2: index
		%tmp_81 = memref.load %l_out__list__3_d0_0[%tmp_80] : memref<6xi32>
		%tmp_82 = arith.constant 3: index
		%tmp_83 = memref.load %l_out__list__3_d0_0[%tmp_82] : memref<6xi32>
		%tmp_84 = arith.constant 4: index
		%tmp_85 = memref.load %l_out__list__3_d0_0[%tmp_84] : memref<6xi32>
		%tmp_86 = arith.constant 5: index
		%tmp_87 = memref.load %l_out__list__3_d0_0[%tmp_86] : memref<6xi32>
		// Stmt Write Preprocessing: End
		// StmtConsume not implemented: consume happens on peaking right now
		//     dfg.push operations deferred from StmtWrites: Begin
		dfg.push(%tmp_77) %Out : i32
		dfg.push(%tmp_79) %Out : i32
		dfg.push(%tmp_81) %Out : i32
		dfg.push(%tmp_83) %Out : i32
		dfg.push(%tmp_85) %Out : i32
		dfg.push(%tmp_87) %Out : i32
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

