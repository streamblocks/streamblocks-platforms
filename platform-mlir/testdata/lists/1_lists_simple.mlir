//-- Definition of actor class: ListsSimple
dfg.process @ListsSimple
	inputs(%In: i32)
	outputs(%Out: i32)
{
	dfg.loop inputs(%In: i32)
	{
		// -- Actor body
		// Block Statement: Begin
		//     Variable declarations attached to block statement: Begin
		%l_t__1_d0_0 = dfg.pull %In : i32
		%l_list__4_d0_0 = memref.alloc() : memref<4xi32>
		%tmp_0 = arith.constant 0 : i1
		%tmp_1 = arith.extui %tmp_0 : i1 to i32
		// l_a__3_d0_0 aliased to tmp_1
		//     Variable declarations attached to block statement: End
		// Assignment Statement: Start
		%tmp_2 = arith.constant 0 : i1
		%tmp_3 = arith.extui %tmp_2 : i1 to i32
		%tmp_4 = arith.index_cast %tmp_3: i32 to index
		%tmp_5 = arith.constant 40 : i6
		%tmp_6 = arith.extui %tmp_5 : i6 to i32
		memref.store %tmp_6, %l_list__4_d0_0[%tmp_4] : memref<4xi32>
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_7 = arith.constant 1 : i1
		%tmp_8 = arith.extui %tmp_7 : i1 to i32
		%tmp_9 = arith.index_cast %tmp_8: i32 to index
		%tmp_10 = arith.constant 30 : i5
		%tmp_11 = arith.extui %tmp_10 : i5 to i32
		memref.store %tmp_11, %l_list__4_d0_0[%tmp_9] : memref<4xi32>
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_12 = arith.constant 2 : i2
		%tmp_13 = arith.extui %tmp_12 : i2 to i32
		%tmp_14 = arith.index_cast %tmp_13: i32 to index
		%tmp_15 = arith.constant 20 : i5
		%tmp_16 = arith.extui %tmp_15 : i5 to i32
		memref.store %tmp_16, %l_list__4_d0_0[%tmp_14] : memref<4xi32>
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_17 = arith.constant 3 : i2
		%tmp_18 = arith.extui %tmp_17 : i2 to i32
		%tmp_19 = arith.index_cast %tmp_18: i32 to index
		%tmp_20 = arith.constant 10 : i4
		%tmp_21 = arith.extui %tmp_20 : i4 to i32
		memref.store %tmp_21, %l_list__4_d0_0[%tmp_19] : memref<4xi32>
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_22 = arith.constant 0 : i1
		%tmp_23 = arith.extui %tmp_22 : i1 to i32
		%tmp_24 = arith.index_cast %tmp_23: i32 to index
		%tmp_25 = memref.load %l_list__4_d0_0[%tmp_24] : memref<4xi32>
		%tmp_26 = arith.constant 1 : i1
		%tmp_27 = arith.extui %tmp_26 : i1 to i32
		%tmp_28 = arith.index_cast %tmp_27: i32 to index
		%tmp_29 = memref.load %l_list__4_d0_0[%tmp_28] : memref<4xi32>
		%tmp_30 = arith.addi %tmp_25, %tmp_29 : i32
		%tmp_31 = arith.constant 2 : i2
		%tmp_32 = arith.extui %tmp_31 : i2 to i32
		%tmp_33 = arith.index_cast %tmp_32: i32 to index
		%tmp_34 = memref.load %l_list__4_d0_0[%tmp_33] : memref<4xi32>
		%tmp_35 = arith.addi %tmp_30, %tmp_34 : i32
		%tmp_36 = arith.constant 3 : i2
		%tmp_37 = arith.extui %tmp_36 : i2 to i32
		%tmp_38 = arith.index_cast %tmp_37: i32 to index
		%tmp_39 = memref.load %l_list__4_d0_0[%tmp_38] : memref<4xi32>
		%tmp_40 = arith.addi %tmp_35, %tmp_39 : i32
		%tmp_41 = arith.constant 4 : i32
		%tmp_42 = arith.addi %tmp_40, %tmp_41 : i32
		// l_a__3_d0_1 aliased to tmp_42
		// Assignment Statement: End
		// Stmt Write Preprocessing: Begin
		%tmp_43 = arith.addi %tmp_42, %l_t__1_d0_0 : i32
		// Stmt Write Preprocessing: End
		// StmtConsume not implemented: consume happens on peaking right now
		//     dfg.push operations deferred from StmtWrites: Begin
		dfg.push(%tmp_43) %Out : i32
		//     dfg.push operations deferred from StmtWrites: End
		// Block Statement: End
	}

}

// -- Top Network: Defines structure of actor application
func.func @top(%In: i32) -> (i32)
{

	// -- Instantiate channels between actors
	%queue_from_ListsSimple_Out, %queue_to_Out = dfg.channel(4096) : i32
	%queue_from_In, %queue_to_ListsSimple_In = dfg.channel(4096) : i32

	// -- Connect input channels to arguments
	dfg.push(%In) %queue_from_In : i32

	// -- Instantiate actors (also known as nodes/instances)
	dfg.instantiate @ListsSimple // Instance name: ListsSimple
		inputs(%queue_to_ListsSimple_In)
		outputs(%queue_from_ListsSimple_Out) :
		(i32) -> (i32)

	// -- Connect output channels to return arguments
	%Out = dfg.pull %queue_to_Out : i32

	// -- Return
	func.return %Out: i32
}

