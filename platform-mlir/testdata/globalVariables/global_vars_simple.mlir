//-- Definition of actor class: GlobalVariables
dfg.process @GlobalVariables
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
		// Evaluate global variable P.
		%tmp_2 = arith.constant 1 : i1
		// Evaluate global variable Q.
		%tmp_3 = arith.constant 4 : i3
		%tmp_4 = arith.constant 1 : i1
		%tmp_5 = arith.extui %tmp_3 : i3 to i4
		%tmp_6 = arith.extui %tmp_4 : i1 to i4
		%tmp_7 = arith.addi %tmp_5, %tmp_6 : i4
		%tmp_8 = arith.extui %tmp_7 : i4 to i32
		// Evaluate global variable Q done: assigned to tmp_8 above in this context.
		%tmp_9 = arith.extui %tmp_2 : i1 to i32
		%tmp_10 = arith.addi %tmp_9, %tmp_8 : i32
		// Evaluate global variable P done: assigned to tmp_10 above in this context.
		%tmp_11 = arith.addi %tmp_1, %tmp_10 : i32
		// l_x__3_d0_1 aliased to tmp_11
		// Assignment Statement: End
		// Stmt Write: Begin
		%tmp_12 = arith.addi %l_t__1_d0_0, %tmp_11 : i32
		dfg.push(%tmp_12) %Out : i32
		// Stmt Write: End
		// StmtConsume not implemented: consume happens on peaking right now
		// Block Statement: End
	}

}

// -- Top Network: Defines structure of actor application
func.func @top(%In: i32) -> (i32)
{

	// -- Instantiate channels between actors
	%queue_from_In, %queue_to_GlobalVariables_In = dfg.channel(4096) : i32
	%queue_from_GlobalVariables_Out, %queue_to_Out = dfg.channel(4096) : i32

	// -- Connect input channels to arguments
	dfg.push(%In) %queue_from_In : i32

	// -- Instantiate actors (also known as nodes/instances)
	dfg.instantiate @GlobalVariables // Instance name: GlobalVariables
		inputs(%queue_to_GlobalVariables_In)
		outputs(%queue_from_GlobalVariables_Out) :
		(i32) -> (i32)

	// -- Connect output channels to return arguments
	%Out = dfg.pull %queue_to_Out : i32

	// -- Return
	func.return %Out: i32
}

