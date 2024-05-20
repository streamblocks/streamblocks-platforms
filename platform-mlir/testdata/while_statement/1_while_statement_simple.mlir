//-- Definition of actor class: WhileSimple
dfg.operator @WhileSimple
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
		// While Statement: Begin
		%l_x__3_d0_1 = scf.while(%l_x__3_d1_0 = %l_x__3_d0_0) : (i32) -> (i32) {
			// While Statement: Condition Check
			%tmp_2 = arith.constant 5 : i3
			%tmp_3 = arith.extui %tmp_2 : i3 to i32
			%tmp_4 = arith.cmpi sge, %tmp_3, %l_x__3_d1_0 : i32
			scf.condition(%tmp_4) %l_x__3_d1_0 : i32
		} do {
			// While Statement: Body Execution
		^tmp_5(%l_x__3_d1_0: i32):
			// Assignment Statement: Start
			%tmp_6 = arith.constant 1 : i1
			%tmp_7 = arith.extui %tmp_6 : i1 to i32
			%tmp_8 = arith.addi %tmp_7, %l_x__3_d1_0 : i32
			%l_x__3_d1_1 = arith.bitcast %tmp_8: i32 to i32
			// Assignment Statement: End
			scf.yield %l_x__3_d1_1: i32
		}
		// While Statement: End
		// Stmt Write: Begin
		%tmp_9 = arith.addi %l_t__1_d0_0, %l_x__3_d0_1 : i32
		dfg.push(%tmp_9) %Out : i32
		// Stmt Write: End
		// StmtConsume not implemented: consume happens on peaking right now
		// Block Statement: End
	}

}

// -- Top Network: Defines structure of actor application
func.func @top(%In: i32) -> (i32)
{

	// -- Instantiate channels between actors
	%queue_from_In, %queue_to_WhileSimple_In = dfg.channel(4096) : i32
	%queue_from_WhileSimple_Out, %queue_to_Out = dfg.channel(4096) : i32

	// -- Connect input channels to arguments
	dfg.push(%In) %queue_from_In : i32

	// -- Instantiate actors (also known as nodes/instances)
	dfg.instantiate @WhileSimple // Instance name: WhileSimple
		inputs(%queue_to_WhileSimple_In)
		outputs(%queue_from_WhileSimple_Out) :
		(i32) -> (i32)

	// -- Connect output channels to return arguments
	%Out = dfg.pull %queue_to_Out : i32

	// -- Return
	func.return %Out: i32
}

