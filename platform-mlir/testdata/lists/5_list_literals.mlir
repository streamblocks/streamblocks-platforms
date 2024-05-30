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
		%tmp_0 = memref.alloc() : memref<4xi32>
		%tmp_1 = arith.constant 0: index
		%tmp_2 = arith.constant 100 : i7
		%tmp_3 = arith.extui %tmp_2 : i7 to i32
		memref.store %tmp_3, %tmp_0[%tmp_1] : memref<4xi32>
		%tmp_4 = arith.constant 1: index
		%tmp_5 = arith.constant 100 : i7
		%tmp_6 = arith.extui %tmp_5 : i7 to i32
		memref.store %tmp_6, %tmp_0[%tmp_4] : memref<4xi32>
		%tmp_7 = arith.constant 2: index
		%tmp_8 = arith.constant 100 : i7
		%tmp_9 = arith.extui %tmp_8 : i7 to i32
		memref.store %tmp_9, %tmp_0[%tmp_7] : memref<4xi32>
		%tmp_10 = arith.constant 3: index
		%tmp_11 = arith.constant 100 : i7
		%tmp_12 = arith.extui %tmp_11 : i7 to i32
		memref.store %tmp_12, %tmp_0[%tmp_10] : memref<4xi32>
		// l_list__3_d0_0 aliased to tmp_0
		//     Variable declarations attached to block statement: End
		// Assignment Statement: Start
		%tmp_13 = memref.alloc() : memref<4xi32>
		%tmp_14 = arith.constant 0: index
		%tmp_15 = arith.constant 100 : i7
		%tmp_16 = arith.extui %tmp_15 : i7 to i32
		%tmp_17 = arith.addi %tmp_16, %l_t1__1_d0_0 : i32
		memref.store %tmp_17, %tmp_13[%tmp_14] : memref<4xi32>
		%tmp_18 = arith.constant 1: index
		%tmp_19 = arith.constant 200 : i8
		%tmp_20 = arith.extui %tmp_19 : i8 to i32
		%tmp_21 = arith.addi %tmp_20, %l_t1__1_d0_0 : i32
		memref.store %tmp_21, %tmp_13[%tmp_18] : memref<4xi32>
		%tmp_22 = arith.constant 2: index
		%tmp_23 = arith.constant 300 : i9
		%tmp_24 = arith.extui %tmp_23 : i9 to i32
		%tmp_25 = arith.addi %tmp_24, %l_t1__1_d0_0 : i32
		memref.store %tmp_25, %tmp_13[%tmp_22] : memref<4xi32>
		%tmp_26 = arith.constant 3: index
		%tmp_27 = arith.constant 400 : i9
		%tmp_28 = arith.extui %tmp_27 : i9 to i32
		%tmp_29 = arith.addi %tmp_28, %l_t1__1_d0_0 : i32
		memref.store %tmp_29, %tmp_13[%tmp_26] : memref<4xi32>
		// l_list__3_d0_1 aliased to tmp_13
		// Assignment Statement: End
		// Stmt Write Preprocessing: Begin
		%tmp_30 = arith.constant 0: index
		%tmp_31 = memref.load %tmp_13[%tmp_30] : memref<4xi32>
		%tmp_32 = arith.constant 1: index
		%tmp_33 = memref.load %tmp_13[%tmp_32] : memref<4xi32>
		%tmp_34 = arith.constant 2: index
		%tmp_35 = memref.load %tmp_13[%tmp_34] : memref<4xi32>
		%tmp_36 = arith.constant 3: index
		%tmp_37 = memref.load %tmp_13[%tmp_36] : memref<4xi32>
		// Stmt Write Preprocessing: End
		// StmtConsume not implemented: consume happens on peaking right now
		//     dfg.push operations deferred from StmtWrites: Begin
		dfg.push(%tmp_31) %Out : i32
		dfg.push(%tmp_33) %Out : i32
		dfg.push(%tmp_35) %Out : i32
		dfg.push(%tmp_37) %Out : i32
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

