//-- Definition of actor class: IfSimple
dfg.operator @IfSimple
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
		%l_x__3_d0_0 = arith.bitcast %tmp_1: i32 to i32
		//     Variable declarations attached to block statement: End
		// If Statement: Begin
		%tmp_2 = arith.constant 20 : i5
		%tmp_3 = arith.extui %tmp_2 : i5 to i32
		%tmp_4 = arith.cmpi sge, %l_t__1_d0_0, %tmp_3 : i32
		%l_x__3_d0_1 = scf.if %tmp_4 -> (i32) {
			// Assignment Statement: Start
			%tmp_5 = arith.constant 1 : i1
			%tmp_6 = arith.extui %tmp_5 : i1 to i32
			%tmp_7 = arith.addi %l_x__3_d0_0, %tmp_6 : i32
			%l_x__3_d1_0 = arith.bitcast %tmp_7: i32 to i32
			// Assignment Statement: End
			scf.yield %l_x__3_d1_0 : i32
		} else {
			// Assignment Statement: Start
			%tmp_8 = arith.constant 1 : i1
			%tmp_9 = arith.extui %tmp_8 : i1 to i32
			%tmp_10 = arith.subi %l_x__3_d0_0, %tmp_9 : i32
			%l_x__3_d1_0 = arith.bitcast %tmp_10: i32 to i32
			// Assignment Statement: End
			scf.yield %l_x__3_d1_0 : i32
		}
		// If Statement: End
		// Stmt Write
		%tmp_11 = arith.addi %l_t__1_d0_0, %l_x__3_d0_1 : i32
		dfg.push(%tmp_11) %Out : i32
		// StmtConsume not implemented: consume happens on peaking right now
		// Block Statement: End
	}
}


// -- Top Network: Defines structure of actor application
func.func @top(%In: i32) -> i32
{

	// -- Instantiate channels between actors
	%queue_from_IfSimple_Out, %queue_to_Out = dfg.channel(4096) : i32
	%queue_from_In, %queue_to_IfSimple_In = dfg.channel(4096) : i32

	// -- Connect input channels to arguments
	dfg.push(%In) %queue_from_In : i32

	// -- Instantiate actors (also known as nodes/instances)
	dfg.instantiate @IfSimple // Instance name: IfSimple
		inputs(%queue_to_IfSimple_In)
		outputs(%queue_from_IfSimple_Out) :
		(i32) -> i32

	// -- Connect output channels to return arguments
	%Out = dfg.pull %queue_to_Out : i32

	// -- Return
	func.return %Out: i32
}

