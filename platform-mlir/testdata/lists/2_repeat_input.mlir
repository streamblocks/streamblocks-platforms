//-- Definition of actor class: RepeatInput
dfg.process @RepeatInput
	inputs(%In1: i32,%In2: i32)
	outputs(%Out: i32)
{
	dfg.loop inputs(%In1: i32,%In2: i32)
	{
		// -- Actor body
		// Block Statement: Begin
		//     Variable declarations attached to block statement: Begin
		%tmp_0 = dfg.pull %In1 : i32
		%tmp_1 = dfg.pull %In1 : i32
		%tmp_2 = dfg.pull %In2 : i32
		%tmp_3 = dfg.pull %In2 : i32
		%tmp_4 = arith.constant 1 : i1
		%tmp_5 = arith.extui %tmp_4 : i1 to i32
		// l_b__7_d0_0 aliased to tmp_5
		%l_t1__1_d0_0 = memref.alloca() : memref<2xi32>
		%tmp_6 = arith.constant 0: index
		memref.store %tmp_0, %l_t1__1_d0_0[%tmp_6] : memref<2xi32>
		%tmp_7 = arith.constant 1: index
		memref.store %tmp_1, %l_t1__1_d0_0[%tmp_7] : memref<2xi32>
		%l_t2__4_d0_0 = memref.alloca() : memref<2xi32>
		%tmp_8 = arith.constant 0: index
		memref.store %tmp_2, %l_t2__4_d0_0[%tmp_8] : memref<2xi32>
		%tmp_9 = arith.constant 1: index
		memref.store %tmp_3, %l_t2__4_d0_0[%tmp_9] : memref<2xi32>
		%l_a__6_d0_0 = arith.constant 0 : i32
		//     Variable declarations attached to block statement: End
		// Assignment Statement: Start
		%tmp_10 = arith.constant 0 : i1
		%tmp_11 = arith.extui %tmp_10 : i1 to i32
		%tmp_12 = arith.index_cast %tmp_11: i32 to index
		%tmp_13 = memref.load %l_t1__1_d0_0[%tmp_12] : memref<2xi32>
		%tmp_14 = arith.constant 1 : i1
		%tmp_15 = arith.extui %tmp_14 : i1 to i32
		%tmp_16 = arith.index_cast %tmp_15: i32 to index
		%tmp_17 = memref.load %l_t1__1_d0_0[%tmp_16] : memref<2xi32>
		%tmp_18 = arith.addi %tmp_13, %tmp_17 : i32
		%tmp_19 = arith.constant 0 : i1
		%tmp_20 = arith.extui %tmp_19 : i1 to i32
		%tmp_21 = arith.index_cast %tmp_20: i32 to index
		%tmp_22 = memref.load %l_t2__4_d0_0[%tmp_21] : memref<2xi32>
		%tmp_23 = arith.addi %tmp_18, %tmp_22 : i32
		%tmp_24 = arith.constant 1 : i1
		%tmp_25 = arith.extui %tmp_24 : i1 to i32
		%tmp_26 = arith.index_cast %tmp_25: i32 to index
		%tmp_27 = memref.load %l_t2__4_d0_0[%tmp_26] : memref<2xi32>
		%tmp_28 = arith.addi %tmp_23, %tmp_27 : i32
		%tmp_29 = arith.addi %tmp_28, %tmp_5 : i32
		// l_a__6_d0_1 aliased to tmp_29
		// Assignment Statement: End
		// Stmt Write Preprocessing: Begin
		// Stmt Write Preprocessing: End
		// StmtConsume not implemented: consume happens on peaking right now
		// StmtConsume not implemented: consume happens on peaking right now
		//     dfg.push operations deferred from StmtWrites: Begin
		dfg.push(%tmp_29) %Out : i32
		//     dfg.push operations deferred from StmtWrites: End
		// Block Statement: End
	}

}

// -- Top Network: Defines structure of actor application
func.func @top(%In1: i32, %In2: i32) -> (i32)
{

	// -- Instantiate channels between actors
	%queue_from_In1, %queue_to_RepeatInput_In1 = dfg.channel(4096) : i32
	%queue_from_RepeatInput_Out, %queue_to_Out = dfg.channel(4096) : i32
	%queue_from_In2, %queue_to_RepeatInput_In2 = dfg.channel(4096) : i32

	// -- Connect input channels to arguments
	dfg.push(%In1) %queue_from_In1 : i32
	dfg.push(%In2) %queue_from_In2 : i32

	// -- Instantiate actors (also known as nodes/instances)
	dfg.instantiate @RepeatInput // Instance name: RepeatInput
		inputs(%queue_to_RepeatInput_In1, %queue_to_RepeatInput_In2)
		outputs(%queue_from_RepeatInput_Out) :
		(i32, i32) -> (i32)

	// -- Connect output channels to return arguments
	%Out = dfg.pull %queue_to_Out : i32

	// -- Return
	func.return %Out: i32
}

