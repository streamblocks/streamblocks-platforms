//-- Definition of actor class: UnaryExpr
dfg.process @UnaryExpr
	inputs(%In: i32)
	outputs(%Out: i32)
{
	dfg.loop inputs(%In: i32)
	{
		// -- Actor body
		// Block Statement: Begin
		//     Variable declarations attached to block statement: Begin
		%l_t__1_d0_0 = dfg.pull %In : i32
		%tmp_0 = arith.constant 0 : i1
		%tmp_1 = arith.extui %tmp_0 : i1 to i32
		// l_x__3_d0_0 aliased to tmp_1
		//     Variable declarations attached to block statement: End
		// Assignment Statement: Start
		%tmp_2 = arith.constant 0 : i32
		%tmp_3 = arith.subi %tmp_2, %l_t__1_d0_0 : i32
		// l_x__3_d0_1 aliased to tmp_3
		// Assignment Statement: End
		// If Statement: Begin
		%tmp_4 = arith.constant 0 : i32
		%tmp_5 = arith.subi %tmp_4, %l_t__1_d0_0 : i32
		%tmp_6 = arith.cmpi ne, %tmp_3, %tmp_5 : i32
		%tmp_7 = arith.constant 1 : i1
		%tmp_8 = arith.xori %tmp_6, %tmp_7 : i1
		%l_x__3_d0_2 = scf.if %tmp_8 -> (i32) {
			// Assignment Statement: Start
			%tmp_9 = arith.constant 1 : i1
			%tmp_10 = arith.extui %tmp_9 : i1 to i32
			%tmp_11 = arith.addi %tmp_3, %tmp_10 : i32
			// l_x__3_d1_0 aliased to tmp_11
			// Assignment Statement: End
			scf.yield %tmp_11 : i32
		} else {
			scf.yield %tmp_3 : i32
		}
		// If Statement: End
		// Assignment Statement: Start
		%tmp_12 = arith.constant 0xffffffff : i32
		%tmp_13 = arith.xori %l_x__3_d0_2, %tmp_12 : i32
		%tmp_14 = arith.constant 1 : i1
		%tmp_15 = arith.extui %tmp_14 : i1 to i32
		%tmp_16 = arith.addi %tmp_13, %tmp_15 : i32
		// l_x__3_d0_3 aliased to tmp_16
		// Assignment Statement: End
		// Stmt Write: Begin
		dfg.push(%tmp_16) %Out : i32
		// Stmt Write: End
		// StmtConsume not implemented: consume happens on peaking right now
		// Block Statement: End
	}

}

// -- Top Network: Defines structure of actor application
func.func @top(%In: i32) -> (i32)
{

	// -- Instantiate channels between actors
	%queue_from_In, %queue_to_UnaryExpr_In = dfg.channel(4096) : i32
	%queue_from_UnaryExpr_Out, %queue_to_Out = dfg.channel(4096) : i32

	// -- Connect input channels to arguments
	dfg.push(%In) %queue_from_In : i32

	// -- Instantiate actors (also known as nodes/instances)
	dfg.instantiate @UnaryExpr // Instance name: UnaryExpr
		inputs(%queue_to_UnaryExpr_In)
		outputs(%queue_from_UnaryExpr_Out) :
		(i32) -> (i32)

	// -- Connect output channels to return arguments
	%Out = dfg.pull %queue_to_Out : i32

	// -- Return
	func.return %Out: i32
}

