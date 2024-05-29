//-- Definition of actor class: ListContainsIndex
dfg.process @ListContainsIndex
	inputs(%In: i32)
	outputs(%Out: i32)
{
	dfg.loop inputs(%In: i32)
	{
		// -- Actor body
		// Block Statement: Begin
		//     Variable declarations attached to block statement: Begin
		%l_t1__1_d0_0 = dfg.pull %In : i32
		%l_list__3_d0_0 = memref.alloc() : memref<4xi32>
		%tmp_0 = arith.constant 0 : i1
		%tmp_1 = arith.extui %tmp_0 : i1 to i32
		// l_index__4_d0_0 aliased to tmp_1
		//     Variable declarations attached to block statement: End
		// Assignment Statement: Start
		%tmp_2 = arith.constant 0 : i1
		%tmp_3 = arith.extui %tmp_2 : i1 to i32
		%tmp_4 = arith.index_cast %tmp_3: i32 to index
		%tmp_5 = arith.constant 1 : i1
		%tmp_6 = arith.extui %tmp_5 : i1 to i32
		memref.store %tmp_6, %l_list__3_d0_0[%tmp_4] : memref<4xi32>
		// Assignment Statement: End
		// While Statement: Begin
		scf.while() : () -> () {
			// While Statement: Condition Check
			%tmp_7 = arith.constant 0 : i1
			%tmp_8 = arith.extui %tmp_7 : i1 to i32
			%tmp_9 = arith.index_cast %tmp_8: i32 to index
			%tmp_10 = memref.load %l_list__3_d0_0[%tmp_9] : memref<4xi32>
			%tmp_11 = arith.constant 4 : i3
			%tmp_12 = arith.extui %tmp_11 : i3 to i32
			%tmp_13 = arith.cmpi ult, %tmp_10, %tmp_12 : i32
			scf.condition(%tmp_13)
		} do {
			// While Statement: Body Execution
		^tmp_14():
			// Assignment Statement: Start
			%tmp_15 = arith.constant 0 : i1
			%tmp_16 = arith.extui %tmp_15 : i1 to i32
			%tmp_17 = arith.index_cast %tmp_16: i32 to index
			%tmp_18 = memref.load %l_list__3_d0_0[%tmp_17] : memref<4xi32>
			%tmp_19 = arith.index_cast %tmp_18: i32 to index
			%tmp_20 = arith.constant 0 : i1
			%tmp_21 = arith.extui %tmp_20 : i1 to i32
			%tmp_22 = arith.index_cast %tmp_21: i32 to index
			%tmp_23 = memref.load %l_list__3_d0_0[%tmp_22] : memref<4xi32>
			%tmp_24 = arith.addi %l_t1__1_d0_0, %tmp_23 : i32
			memref.store %tmp_24, %l_list__3_d0_0[%tmp_19] : memref<4xi32>
			// Assignment Statement: End
			// Assignment Statement: Start
			%tmp_25 = arith.constant 0 : i1
			%tmp_26 = arith.extui %tmp_25 : i1 to i32
			%tmp_27 = arith.index_cast %tmp_26: i32 to index
			%tmp_28 = arith.constant 0 : i1
			%tmp_29 = arith.extui %tmp_28 : i1 to i32
			%tmp_30 = arith.index_cast %tmp_29: i32 to index
			%tmp_31 = memref.load %l_list__3_d0_0[%tmp_30] : memref<4xi32>
			%tmp_32 = arith.constant 1 : i1
			%tmp_33 = arith.extui %tmp_32 : i1 to i32
			%tmp_34 = arith.addi %tmp_31, %tmp_33 : i32
			memref.store %tmp_34, %l_list__3_d0_0[%tmp_27] : memref<4xi32>
			// Assignment Statement: End
			scf.yield
		}
		// While Statement: End
		// Stmt Write Preprocessing: Begin
		%tmp_35 = arith.constant 0: index
		%tmp_36 = memref.load %l_list__3_d0_0[%tmp_35] : memref<4xi32>
		%tmp_37 = arith.constant 1: index
		%tmp_38 = memref.load %l_list__3_d0_0[%tmp_37] : memref<4xi32>
		%tmp_39 = arith.constant 2: index
		%tmp_40 = memref.load %l_list__3_d0_0[%tmp_39] : memref<4xi32>
		%tmp_41 = arith.constant 3: index
		%tmp_42 = memref.load %l_list__3_d0_0[%tmp_41] : memref<4xi32>
		// Stmt Write Preprocessing: End
		// StmtConsume not implemented: consume happens on peaking right now
		//     dfg.push operations deferred from StmtWrites: Begin
		dfg.push(%tmp_36) %Out : i32
		dfg.push(%tmp_38) %Out : i32
		dfg.push(%tmp_40) %Out : i32
		dfg.push(%tmp_42) %Out : i32
		//     dfg.push operations deferred from StmtWrites: End
		// Block Statement: End
	}

}

// -- Top Network: Defines structure of actor application
func.func @top(%In: i32) -> (i32)
{

	// -- Instantiate channels between actors
	%queue_from_In, %queue_to_ListContainsIndex_In = dfg.channel(4096) : i32
	%queue_from_ListContainsIndex_Out, %queue_to_Out = dfg.channel(4096) : i32

	// -- Connect input channels to arguments
	dfg.push(%In) %queue_from_In : i32

	// -- Instantiate actors (also known as nodes/instances)
	dfg.instantiate @ListContainsIndex // Instance name: ListContainsIndex
		inputs(%queue_to_ListContainsIndex_In)
		outputs(%queue_from_ListContainsIndex_Out) :
		(i32) -> (i32)

	// -- Connect output channels to return arguments
	%Out = dfg.pull %queue_to_Out : i32

	// -- Return
	func.return %Out: i32
}

