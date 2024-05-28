//-- Definition of actor class: ConditionsSimple
dfg.process @ConditionsSimple
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
		// l_x__3_d0_0 aliased to tmp_1
		//     Variable declarations attached to block statement: End
		// If Statement: Begin
		%tmp_2 = arith.constant 20 : i5
		%tmp_3 = arith.extui %tmp_2 : i5 to i32
		%tmp_4 = arith.cmpi sge, %l_t__1_d0_0, %tmp_3 : i32
		%tmp_5 = arith.constant 40 : i6
		%tmp_6 = arith.extui %tmp_5 : i6 to i32
		%tmp_7 = arith.cmpi sle, %l_t__1_d0_0, %tmp_6 : i32
		%tmp_8 = arith.andi %tmp_4, %tmp_7 : i1
		%l_x__3_d0_1 = scf.if %tmp_8 -> (i32) {
			// Assignment Statement: Start
			%tmp_9 = arith.constant 1000 : i10
			%tmp_10 = arith.extui %tmp_9 : i10 to i32
			// l_x__3_d1_0 aliased to tmp_10
			// Assignment Statement: End
			scf.yield %tmp_10 : i32
		} else {
			scf.yield %tmp_1 : i32
		}
		// If Statement: End
		// If Statement: Begin
		%tmp_11 = arith.constant 20 : i5
		%tmp_12 = arith.extui %tmp_11 : i5 to i32
		%tmp_13 = arith.cmpi slt, %l_t__1_d0_0, %tmp_12 : i32
		%tmp_14 = arith.constant 40 : i6
		%tmp_15 = arith.extui %tmp_14 : i6 to i32
		%tmp_16 = arith.cmpi sgt, %l_t__1_d0_0, %tmp_15 : i32
		%tmp_17 = arith.ori %tmp_13, %tmp_16 : i1
		%l_x__3_d0_2 = scf.if %tmp_17 -> (i32) {
			// Assignment Statement: Start
			%tmp_18 = arith.constant 10000 : i14
			%tmp_19 = arith.extui %tmp_18 : i14 to i32
			// l_x__3_d1_0 aliased to tmp_19
			// Assignment Statement: End
			scf.yield %tmp_19 : i32
		} else {
			scf.yield %l_x__3_d0_1 : i32
		}
		// If Statement: End
		// If Statement: Begin
		%tmp_20 = arith.constant 20 : i5
		%tmp_21 = arith.extui %tmp_20 : i5 to i32
		%tmp_22 = arith.cmpi ne, %l_t__1_d0_0, %tmp_21 : i32
		%l_x__3_d0_3 = scf.if %tmp_22 -> (i32) {
			// Assignment Statement: Start
			%tmp_23 = arith.constant 1 : i1
			%tmp_24 = arith.extui %tmp_23 : i1 to i32
			%tmp_25 = arith.addi %l_x__3_d0_2, %tmp_24 : i32
			// l_x__3_d1_0 aliased to tmp_25
			// Assignment Statement: End
			scf.yield %tmp_25 : i32
		} else {
			scf.yield %l_x__3_d0_2 : i32
		}
		// If Statement: End
		// If Statement: Begin
		%tmp_26 = arith.constant 20 : i5
		%tmp_27 = arith.extui %tmp_26 : i5 to i32
		%tmp_28 = arith.cmpi eq, %l_t__1_d0_0, %tmp_27 : i32
		%l_x__3_d0_4 = scf.if %tmp_28 -> (i32) {
			// Assignment Statement: Start
			%tmp_29 = arith.constant 2 : i2
			%tmp_30 = arith.extui %tmp_29 : i2 to i32
			%tmp_31 = arith.addi %l_x__3_d0_3, %tmp_30 : i32
			// l_x__3_d1_0 aliased to tmp_31
			// Assignment Statement: End
			scf.yield %tmp_31 : i32
		} else {
			scf.yield %l_x__3_d0_3 : i32
		}
		// If Statement: End
		// Stmt Write Preprocessing: Begin
		%tmp_32 = arith.addi %l_t__1_d0_0, %l_x__3_d0_4 : i32
		// Stmt Write Preprocessing: End
		// StmtConsume not implemented: consume happens on peaking right now
		//     dfg.push operations deferred from StmtWrites: Begin
		dfg.push(%tmp_32) %Out : i32
		//     dfg.push operations deferred from StmtWrites: End
		// Block Statement: End
	}

}

// -- Top Network: Defines structure of actor application
func.func @top(%In: i32) -> (i32)
{

	// -- Instantiate channels between actors
	%queue_from_ConditionsSimple_Out, %queue_to_Out = dfg.channel(4096) : i32
	%queue_from_In, %queue_to_ConditionsSimple_In = dfg.channel(4096) : i32

	// -- Connect input channels to arguments
	dfg.push(%In) %queue_from_In : i32

	// -- Instantiate actors (also known as nodes/instances)
	dfg.instantiate @ConditionsSimple // Instance name: ConditionsSimple
		inputs(%queue_to_ConditionsSimple_In)
		outputs(%queue_from_ConditionsSimple_Out) :
		(i32) -> (i32)

	// -- Connect output channels to return arguments
	%Out = dfg.pull %queue_to_Out : i32

	// -- Return
	func.return %Out: i32
}

