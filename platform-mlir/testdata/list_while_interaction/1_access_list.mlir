//-- Definition of actor class: AccessList
dfg.process @AccessList
	inputs(%In: i32)
	outputs(%Out: i32)
{
	dfg.loop inputs(%In: i32)
	{
		// -- Actor body
		// Block Statement: Begin
		//     Variable declarations attached to block statement: Begin
		%tmp_0 = dfg.pull %In : i32
		%tmp_1 = dfg.pull %In : i32
		%tmp_2 = dfg.pull %In : i32
		%tmp_3 = dfg.pull %In : i32
		%tmp_4 = arith.constant 0 : i1
		%tmp_5 = arith.extui %tmp_4 : i1 to i32
		// l_sum__3_d0_0 aliased to tmp_5
		%l_t1__1_d0_0 = memref.alloca() : memref<4xi32>
		%tmp_6 = arith.constant 0: index
		memref.store %tmp_0, %l_t1__1_d0_0[%tmp_6] : memref<4xi32>
		%tmp_7 = arith.constant 1: index
		memref.store %tmp_1, %l_t1__1_d0_0[%tmp_7] : memref<4xi32>
		%tmp_8 = arith.constant 2: index
		memref.store %tmp_2, %l_t1__1_d0_0[%tmp_8] : memref<4xi32>
		%tmp_9 = arith.constant 3: index
		memref.store %tmp_3, %l_t1__1_d0_0[%tmp_9] : memref<4xi32>
		%tmp_10 = arith.constant 0 : i1
		%tmp_11 = arith.extui %tmp_10 : i1 to i32
		// l_index__4_d0_0 aliased to tmp_11
		//     Variable declarations attached to block statement: End
		// While Statement: Begin
		%l_index__4_d0_1, %l_sum__3_d0_1 = scf.while(%l_index__4_d1_0 = %tmp_11, %l_sum__3_d1_0 = %tmp_5) : (i32, i32) -> (i32, i32) {
			// While Statement: Condition Check
			%tmp_12 = arith.constant 4 : i3
			%tmp_13 = arith.extui %tmp_12 : i3 to i32
			%tmp_14 = arith.cmpi slt, %l_index__4_d1_0, %tmp_13 : i32
			scf.condition(%tmp_14) %l_index__4_d1_0, %l_sum__3_d1_0 : i32, i32
		} do {
			// While Statement: Body Execution
		^tmp_15(%l_index__4_d1_0: i32, %l_sum__3_d1_0: i32):
			// Assignment Statement: Start
			%tmp_16 = arith.index_cast %l_index__4_d1_0: i32 to index
			%tmp_17 = memref.load %l_t1__1_d0_0[%tmp_16] : memref<4xi32>
			%tmp_18 = arith.addi %l_sum__3_d1_0, %tmp_17 : i32
			// l_sum__3_d1_1 aliased to tmp_18
			// Assignment Statement: End
			// Assignment Statement: Start
			%tmp_19 = arith.constant 1 : i1
			%tmp_20 = arith.extui %tmp_19 : i1 to i32
			%tmp_21 = arith.addi %l_index__4_d1_0, %tmp_20 : i32
			// l_index__4_d1_1 aliased to tmp_21
			// Assignment Statement: End
			scf.yield %tmp_21, %tmp_18: i32, i32
		}
		// While Statement: End
		// Stmt Write Preprocessing: Begin
		// Stmt Write Preprocessing: End
		// StmtConsume not implemented: consume happens on peaking right now
		//     dfg.push operations deferred from StmtWrites: Begin
		dfg.push(%l_sum__3_d0_1) %Out : i32
		//     dfg.push operations deferred from StmtWrites: End
		// Block Statement: End
	}

}

// -- Top Network: Defines structure of actor application
func.func @top(%In: i32) -> (i32)
{

	// -- Instantiate channels between actors
	%queue_from_In, %queue_to_AccessList_In = dfg.channel(4096) : i32
	%queue_from_AccessList_Out, %queue_to_Out = dfg.channel(4096) : i32

	// -- Connect input channels to arguments
	dfg.push(%In) %queue_from_In : i32

	// -- Instantiate actors (also known as nodes/instances)
	dfg.instantiate @AccessList // Instance name: AccessList
		inputs(%queue_to_AccessList_In)
		outputs(%queue_from_AccessList_Out) :
		(i32) -> (i32)

	// -- Connect output channels to return arguments
	%Out = dfg.pull %queue_to_Out : i32

	// -- Return
	func.return %Out: i32
}

