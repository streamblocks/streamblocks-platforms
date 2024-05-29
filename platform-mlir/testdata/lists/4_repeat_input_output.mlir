//-- Definition of actor class: RepeatInputOutput
dfg.process @RepeatInputOutput
	inputs(%In1: i32,%In2: i32)
	outputs(%Out1: i32,%Out2: i32)
{
	dfg.loop inputs(%In1: i32,%In2: i32)
	{
		// -- Actor body
		// Block Statement: Begin
		//     Variable declarations attached to block statement: Begin
		%tmp_0 = dfg.pull %In1 : i32
		%tmp_1 = dfg.pull %In1 : i32
		%tmp_2 = dfg.pull %In1 : i32
		%tmp_3 = dfg.pull %In1 : i32
		%l_t2__4_d0_0 = dfg.pull %In2 : i32
		%l_t1__1_d0_0 = memref.alloc() : memref<4xi32>
		%tmp_4 = arith.constant 0: index
		memref.store %tmp_0, %l_t1__1_d0_0[%tmp_4] : memref<4xi32>
		%tmp_5 = arith.constant 1: index
		memref.store %tmp_1, %l_t1__1_d0_0[%tmp_5] : memref<4xi32>
		%tmp_6 = arith.constant 2: index
		memref.store %tmp_2, %l_t1__1_d0_0[%tmp_6] : memref<4xi32>
		%tmp_7 = arith.constant 3: index
		memref.store %tmp_3, %l_t1__1_d0_0[%tmp_7] : memref<4xi32>
		//     Variable declarations attached to block statement: End
		// Stmt Write Preprocessing: Begin
		%tmp_8 = arith.constant 0: index
		%tmp_9 = memref.load %l_t1__1_d0_0[%tmp_8] : memref<4xi32>
		%tmp_10 = arith.constant 1: index
		%tmp_11 = memref.load %l_t1__1_d0_0[%tmp_10] : memref<4xi32>
		%tmp_12 = arith.constant 2: index
		%tmp_13 = memref.load %l_t1__1_d0_0[%tmp_12] : memref<4xi32>
		%tmp_14 = arith.constant 3: index
		%tmp_15 = memref.load %l_t1__1_d0_0[%tmp_14] : memref<4xi32>
		// Stmt Write Preprocessing: End
		// Stmt Write Preprocessing: Begin
		%tmp_16 = arith.constant 1 : i1
		%tmp_17 = arith.extui %tmp_16 : i1 to i32
		%tmp_18 = arith.addi %l_t2__4_d0_0, %tmp_17 : i32
		// Stmt Write Preprocessing: End
		// StmtConsume not implemented: consume happens on peaking right now
		// StmtConsume not implemented: consume happens on peaking right now
		//     dfg.push operations deferred from StmtWrites: Begin
		dfg.push(%tmp_9) %Out1 : i32
		dfg.push(%tmp_11) %Out1 : i32
		dfg.push(%tmp_13) %Out1 : i32
		dfg.push(%tmp_15) %Out1 : i32
		dfg.push(%tmp_18) %Out2 : i32
		//     dfg.push operations deferred from StmtWrites: End
		// Block Statement: End
	}

}

// -- Top Network: Defines structure of actor application
func.func @top(%In1: i32, %In2: i32) -> (i32, i32)
{

	// -- Instantiate channels between actors
	%queue_from_RepeatInputOutput_Out2, %queue_to_Out2 = dfg.channel(4096) : i32
	%queue_from_RepeatInputOutput_Out1, %queue_to_Out1 = dfg.channel(4096) : i32
	%queue_from_In1, %queue_to_RepeatInputOutput_In1 = dfg.channel(4096) : i32
	%queue_from_In2, %queue_to_RepeatInputOutput_In2 = dfg.channel(4096) : i32

	// -- Connect input channels to arguments
	dfg.push(%In1) %queue_from_In1 : i32
	dfg.push(%In2) %queue_from_In2 : i32

	// -- Instantiate actors (also known as nodes/instances)
	dfg.instantiate @RepeatInputOutput // Instance name: RepeatInputOutput
		inputs(%queue_to_RepeatInputOutput_In1, %queue_to_RepeatInputOutput_In2)
		outputs(%queue_from_RepeatInputOutput_Out1, %queue_from_RepeatInputOutput_Out2) :
		(i32, i32) -> (i32, i32)

	// -- Connect output channels to return arguments
	%Out2 = dfg.pull %queue_to_Out2 : i32
	%Out1 = dfg.pull %queue_to_Out1 : i32

	// -- Return
	func.return %Out1, %Out2: i32, i32
}

