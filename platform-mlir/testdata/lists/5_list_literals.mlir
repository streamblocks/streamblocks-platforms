//-- Definition of actor class: ListLiterals
dfg.process @ListLiterals
	inputs(%In: i32)
	outputs(%Out: i32)
{
	dfg.loop inputs(%In: i32)
	{
		// -- Actor body
		// Block Statement: Begin
		//     Variable declarations attached to block statement: Begin
		%l_t1__1_d0_0 = dfg.pull %In : i32
		%tmp_0 = memref.alloc() : memref<4xi7>
		%tmp_1 = arith.constant 0: index
		%tmp_2 = arith.constant 100 : i7
		memref.store %tmp_2, %tmp_0[%tmp_1] : memref<4xi7>
		%tmp_3 = arith.constant 1: index
		%tmp_4 = arith.constant 100 : i7
		memref.store %tmp_4, %tmp_0[%tmp_3] : memref<4xi7>
		%tmp_5 = arith.constant 2: index
		%tmp_6 = arith.constant 100 : i7
		memref.store %tmp_6, %tmp_0[%tmp_5] : memref<4xi7>
		%tmp_7 = arith.constant 3: index
		%tmp_8 = arith.constant 100 : i7
		memref.store %tmp_8, %tmp_0[%tmp_7] : memref<4xi7>
		%tmp_9 = memref.alloc() : memref<4xi32>
		%tmp_10 = arith.constant 0: index
		%tmp_11 = memref.load %tmp_0[%tmp_10] : memref<4xi7>
		%tmp_12 = arith.extui %tmp_11 : i7 to i32
		memref.store %tmp_12, %tmp_9[%tmp_10] : memref<4xi32>
		%tmp_13 = arith.constant 1: index
		%tmp_14 = memref.load %tmp_0[%tmp_13] : memref<4xi7>
		%tmp_15 = arith.extui %tmp_14 : i7 to i32
		memref.store %tmp_15, %tmp_9[%tmp_13] : memref<4xi32>
		%tmp_16 = arith.constant 2: index
		%tmp_17 = memref.load %tmp_0[%tmp_16] : memref<4xi7>
		%tmp_18 = arith.extui %tmp_17 : i7 to i32
		memref.store %tmp_18, %tmp_9[%tmp_16] : memref<4xi32>
		%tmp_19 = arith.constant 3: index
		%tmp_20 = memref.load %tmp_0[%tmp_19] : memref<4xi7>
		%tmp_21 = arith.extui %tmp_20 : i7 to i32
		memref.store %tmp_21, %tmp_9[%tmp_19] : memref<4xi32>
		// l_list__3_d0_0 aliased to tmp_9
		//     Variable declarations attached to block statement: End
		// Assignment Statement: Start
		%tmp_22 = memref.alloc() : memref<4xi32>
		%tmp_23 = arith.constant 0: index
		%tmp_24 = arith.constant 100 : i7
		%tmp_25 = arith.extui %tmp_24 : i7 to i32
		%tmp_26 = arith.addi %tmp_25, %l_t1__1_d0_0 : i32
		memref.store %tmp_26, %tmp_22[%tmp_23] : memref<4xi32>
		%tmp_27 = arith.constant 1: index
		%tmp_28 = arith.constant 200 : i8
		%tmp_29 = arith.extui %tmp_28 : i8 to i32
		%tmp_30 = arith.addi %tmp_29, %l_t1__1_d0_0 : i32
		memref.store %tmp_30, %tmp_22[%tmp_27] : memref<4xi32>
		%tmp_31 = arith.constant 2: index
		%tmp_32 = arith.constant 300 : i9
		%tmp_33 = arith.extui %tmp_32 : i9 to i32
		%tmp_34 = arith.addi %tmp_33, %l_t1__1_d0_0 : i32
		memref.store %tmp_34, %tmp_22[%tmp_31] : memref<4xi32>
		%tmp_35 = arith.constant 3: index
		%tmp_36 = arith.constant 400 : i9
		%tmp_37 = arith.extui %tmp_36 : i9 to i32
		%tmp_38 = arith.addi %tmp_37, %l_t1__1_d0_0 : i32
		memref.store %tmp_38, %tmp_22[%tmp_35] : memref<4xi32>
		// l_list__3_d0_1 aliased to tmp_22
		// Assignment Statement: End
		// Stmt Write Preprocessing: Begin
		%tmp_39 = arith.constant 0: index
		%tmp_40 = memref.load %tmp_22[%tmp_39] : memref<4xi32>
		%tmp_41 = arith.constant 1: index
		%tmp_42 = memref.load %tmp_22[%tmp_41] : memref<4xi32>
		%tmp_43 = arith.constant 2: index
		%tmp_44 = memref.load %tmp_22[%tmp_43] : memref<4xi32>
		%tmp_45 = arith.constant 3: index
		%tmp_46 = memref.load %tmp_22[%tmp_45] : memref<4xi32>
		// Stmt Write Preprocessing: End
		// StmtConsume not implemented: consume happens on peaking right now
		//     dfg.push operations deferred from StmtWrites: Begin
		dfg.push(%tmp_40) %Out : i32
		dfg.push(%tmp_42) %Out : i32
		dfg.push(%tmp_44) %Out : i32
		dfg.push(%tmp_46) %Out : i32
		//     dfg.push operations deferred from StmtWrites: End
		// Block Statement: End
	}

}

// -- Top Network: Defines structure of actor application
func.func @top(%In: i32) -> (i32)
{

	// -- Instantiate channels between actors
	%queue_from_In, %queue_to_ListLiterals_In = dfg.channel(4096) : i32
	%queue_from_ListLiterals_Out, %queue_to_Out = dfg.channel(4096) : i32

	// -- Connect input channels to arguments
	dfg.push(%In) %queue_from_In : i32

	// -- Instantiate actors (also known as nodes/instances)
	dfg.instantiate @ListLiterals // Instance name: ListLiterals
		inputs(%queue_to_ListLiterals_In)
		outputs(%queue_from_ListLiterals_Out) :
		(i32) -> (i32)

	// -- Connect output channels to return arguments
	%Out = dfg.pull %queue_to_Out : i32

	// -- Return
	func.return %Out: i32
}

