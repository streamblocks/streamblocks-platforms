//-- Definition of actor class: ProduceConsume
dfg.operator @ProduceConsume
	inputs(%In1: i32,%In2: i32)
	outputs(%Out1: i32,%Out2: i32)
{
	dfg.loop inputs(%In1: i32,%In2: i32)
	{
		// -- Actor body
		// Block Statement: Begin
		//     Variable declarations attached to block statement: Begin
		%l_t1__1_d0_0 = dfg.pull %In1 : i32
		%l_t2__4_d0_0 = dfg.pull %In2 : i32
		//     Variable declarations attached to block statement: End
		// Stmt Write: Begin
		dfg.push(%l_t2__4_d0_0) %Out1 : i32
		// Stmt Write: End
		// Stmt Write: Begin
		dfg.push(%l_t1__1_d0_0) %Out2 : i32
		// Stmt Write: End
		// StmtConsume not implemented: consume happens on peaking right now
		// StmtConsume not implemented: consume happens on peaking right now
		// Block Statement: End
	}
}


// -- Top Network: Defines structure of actor application
func.func @top(%In1: i32, %In2: i32) -> (i32, i32)
{

	// -- Instantiate channels between actors
	%queue_from_ProduceConsume_Out1, %queue_to_Out1 = dfg.channel(4096) : i32
	%queue_from_ProduceConsume_Out2, %queue_to_Out2 = dfg.channel(4096) : i32
	%queue_from_In1, %queue_to_ProduceConsume_In1 = dfg.channel(4096) : i32
	%queue_from_In2, %queue_to_ProduceConsume_In2 = dfg.channel(4096) : i32

	// -- Connect input channels to arguments
	dfg.push(%In1) %queue_from_In1 : i32
	dfg.push(%In2) %queue_from_In2 : i32

	// -- Instantiate actors (also known as nodes/instances)
	dfg.instantiate @ProduceConsume // Instance name: ProduceConsume
		inputs(%queue_to_ProduceConsume_In1, %queue_to_ProduceConsume_In2)
		outputs(%queue_from_ProduceConsume_Out1, %queue_from_ProduceConsume_Out2) :
		(i32, i32) -> (i32, i32)

	// -- Connect output channels to return arguments
	%Out1 = dfg.pull %queue_to_Out1 : i32
	%Out2 = dfg.pull %queue_to_Out2 : i32

	// -- Return
	func.return %Out1, %Out2: i32, i32
}

