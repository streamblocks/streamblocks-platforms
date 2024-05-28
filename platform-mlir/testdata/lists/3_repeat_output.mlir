//-- Definition of actor class: RepeatOutput
dfg.process @RepeatOutput
	inputs(%In: i32)
	outputs(%Out: i32)
{
	dfg.loop inputs(%In: i32)
	{
		// -- Actor body
		// Block Statement: Begin
		//     Variable declarations attached to block statement: Begin
		%l_t__1_d0_0 = dfg.pull %In : i32
		%l_list__3_d0_0 = memref.alloca() : memref<4xi32>
		//     Variable declarations attached to block statement: End
		// Assignment Statement: Start
		%tmp_0 = arith.constant 0 : i1
		%tmp_1 = arith.extui %tmp_0 : i1 to i32
		%tmp_2 = arith.index_cast %tmp_1: i32 to index
		%tmp_3 = arith.constant 40 : i6
		%tmp_4 = arith.extui %tmp_3 : i6 to i32
		%tmp_5 = arith.addi %tmp_4, %l_t__1_d0_0 : i32
		memref.store %tmp_5, %l_list__3_d0_0[%tmp_2] : memref<4xi32>
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_6 = arith.constant 1 : i1
		%tmp_7 = arith.extui %tmp_6 : i1 to i32
		%tmp_8 = arith.index_cast %tmp_7: i32 to index
		%tmp_9 = arith.constant 30 : i5
		%tmp_10 = arith.extui %tmp_9 : i5 to i32
		%tmp_11 = arith.addi %tmp_10, %l_t__1_d0_0 : i32
		memref.store %tmp_11, %l_list__3_d0_0[%tmp_8] : memref<4xi32>
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_12 = arith.constant 2 : i2
		%tmp_13 = arith.extui %tmp_12 : i2 to i32
		%tmp_14 = arith.index_cast %tmp_13: i32 to index
		%tmp_15 = arith.constant 20 : i5
		%tmp_16 = arith.extui %tmp_15 : i5 to i32
		%tmp_17 = arith.addi %tmp_16, %l_t__1_d0_0 : i32
		memref.store %tmp_17, %l_list__3_d0_0[%tmp_14] : memref<4xi32>
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_18 = arith.constant 3 : i2
		%tmp_19 = arith.extui %tmp_18 : i2 to i32
		%tmp_20 = arith.index_cast %tmp_19: i32 to index
		%tmp_21 = arith.constant 10 : i4
		%tmp_22 = arith.extui %tmp_21 : i4 to i32
		%tmp_23 = arith.addi %tmp_22, %l_t__1_d0_0 : i32
		memref.store %tmp_23, %l_list__3_d0_0[%tmp_20] : memref<4xi32>
		// Assignment Statement: End
		// Stmt Write Preprocessing: Begin
		%tmp_24 = arith.constant 0: index
		%tmp_25 = memref.load %l_list__3_d0_0[%tmp_24] : memref<4xi32>
		%tmp_26 = arith.constant 1: index
		%tmp_27 = memref.load %l_list__3_d0_0[%tmp_26] : memref<4xi32>
		%tmp_28 = arith.constant 2: index
		%tmp_29 = memref.load %l_list__3_d0_0[%tmp_28] : memref<4xi32>
		%tmp_30 = arith.constant 3: index
		%tmp_31 = memref.load %l_list__3_d0_0[%tmp_30] : memref<4xi32>
		// Stmt Write Preprocessing: End
		// StmtConsume not implemented: consume happens on peaking right now
		//     dfg.push operations deferred from StmtWrites: Begin
		dfg.push(%tmp_25) %Out : i32
		dfg.push(%tmp_27) %Out : i32
		dfg.push(%tmp_29) %Out : i32
		dfg.push(%tmp_31) %Out : i32
		//     dfg.push operations deferred from StmtWrites: End
		// Block Statement: End
	}

}

// -- Top Network: Defines structure of actor application
func.func @top(%In: i32) -> (i32)
{

	// -- Instantiate channels between actors
	%queue_from_In, %queue_to_RepeatOutput_In = dfg.channel(4096) : i32
	%queue_from_RepeatOutput_Out, %queue_to_Out = dfg.channel(4096) : i32

	// -- Connect input channels to arguments
	dfg.push(%In) %queue_from_In : i32

	// -- Instantiate actors (also known as nodes/instances)
	dfg.instantiate @RepeatOutput // Instance name: RepeatOutput
		inputs(%queue_to_RepeatOutput_In)
		outputs(%queue_from_RepeatOutput_Out) :
		(i32) -> (i32)

	// -- Connect output channels to return arguments
	%Out = dfg.pull %queue_to_Out : i32

	// -- Return
	func.return %Out: i32
}

