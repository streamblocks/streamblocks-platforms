//-- Definition of actor class: BinaryExpr
dfg.operator @BinaryExpr
	inputs(%In: i32)
	outputs(%Out: i32)
{
	dfg.loop inputs(%In: i32)
	{
		// -- Actor body
		// Block Statement: Begin
		//     Variable declarations attached to block statement: Begin
		%l_t__1_d0_0 = dfg.pull %In : i32
		%tmp_0 = arith.constant 100 : i7
		%tmp_1 = arith.extui %tmp_0 : i7 to i32
		%l_x__3_d0_0 = arith.bitcast %tmp_1: i32 to i32
		//     Variable declarations attached to block statement: End
		// Assignment Statement: Start
		%tmp_2 = arith.constant 1 : i1
		%tmp_3 = arith.extui %tmp_2 : i1 to i32
		%tmp_4 = arith.addi %l_x__3_d0_0, %tmp_3 : i32
		%l_x__3_d0_1 = arith.bitcast %tmp_4: i32 to i32
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_5 = arith.constant 2 : i2
		%tmp_6 = arith.extui %tmp_5 : i2 to i32
		%tmp_7 = arith.subi %l_x__3_d0_1, %tmp_6 : i32
		%l_x__3_d0_2 = arith.bitcast %tmp_7: i32 to i32
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_8 = arith.constant 10 : i4
		%tmp_9 = arith.extui %tmp_8 : i4 to i32
		%tmp_10 = arith.muli %l_x__3_d0_2, %tmp_9 : i32
		%l_x__3_d0_3 = arith.bitcast %tmp_10: i32 to i32
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_11 = arith.constant 2 : i2
		%tmp_12 = arith.extui %tmp_11 : i2 to i32
		%tmp_13 = arith.divsi %l_x__3_d0_3, %tmp_12 : i32
		%l_x__3_d0_4 = arith.bitcast %tmp_13: i32 to i32
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_14 = arith.constant 4 : i3
		%tmp_15 = arith.extui %tmp_14 : i3 to i32
		%tmp_16 = arith.remsi %l_x__3_d0_4, %tmp_15 : i32
		%l_x__3_d0_5 = arith.bitcast %tmp_16: i32 to i32
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_17 = arith.constant 1 : i1
		%tmp_18 = arith.extui %tmp_17 : i1 to i32
		%tmp_19 = arith.shrsi %l_x__3_d0_5, %tmp_18 : i32
		%l_x__3_d0_6 = arith.bitcast %tmp_19: i32 to i32
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_20 = arith.constant 6 : i3
		%tmp_21 = arith.extui %tmp_20 : i3 to i32
		%tmp_22 = arith.ori %l_x__3_d0_6, %tmp_21 : i32
		%l_x__3_d0_7 = arith.bitcast %tmp_22: i32 to i32
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_23 = arith.constant 5 : i3
		%tmp_24 = arith.extui %tmp_23 : i3 to i32
		%tmp_25 = arith.andi %l_x__3_d0_7, %tmp_24 : i32
		%l_x__3_d0_8 = arith.bitcast %tmp_25: i32 to i32
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_26 = arith.constant 1 : i1
		%tmp_27 = arith.extui %tmp_26 : i1 to i32
		%tmp_28 = arith.shli %l_x__3_d0_8, %tmp_27 : i32
		%l_x__3_d0_9 = arith.bitcast %tmp_28: i32 to i32
		// Assignment Statement: End
		// Stmt Write: Begin
		dfg.push(%l_x__3_d0_9) %Out : i32
		// Stmt Write: End
		// StmtConsume not implemented: consume happens on peaking right now
		// Block Statement: End
	}
}


// -- Top Network: Defines structure of actor application
func.func @top(%In: i32) -> (i32)
{

	// -- Instantiate channels between actors
	%queue_from_In, %queue_to_BinaryExpr_In = dfg.channel(4096) : i32
	%queue_from_BinaryExpr_Out, %queue_to_Out = dfg.channel(4096) : i32

	// -- Connect input channels to arguments
	dfg.push(%In) %queue_from_In : i32

	// -- Instantiate actors (also known as nodes/instances)
	dfg.instantiate @BinaryExpr // Instance name: BinaryExpr
		inputs(%queue_to_BinaryExpr_In)
		outputs(%queue_from_BinaryExpr_Out) :
		(i32) -> (i32)

	// -- Connect output channels to return arguments
	%Out = dfg.pull %queue_to_Out : i32

	// -- Return
	func.return %Out: i32
}

