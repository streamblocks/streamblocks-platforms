//-- Definition of actor class: BinaryExpr
dfg.process @BinaryExpr
	inputs(%In: i32)
	outputs(%Out: i32)
{
	dfg.loop inputs(%In: i32)
	{
		// -- Actor body
		// Block Statement: Begin
		//     Variable declarations attached to block statement: Begin
		%l_t__1_d0_0 = dfg.pull %In : i32
		%tmp_0 = arith.constant 25 : i5
		%tmp_1 = arith.constant 25 : i5
		%tmp_2 = arith.extui %tmp_0 : i5 to i6
		%tmp_3 = arith.extui %tmp_1 : i5 to i6
		%tmp_4 = arith.addi %tmp_2, %tmp_3 : i6
		%tmp_5 = arith.constant 25 : i5
		%tmp_6 = arith.extui %tmp_4 : i6 to i7
		%tmp_7 = arith.extui %tmp_5 : i5 to i7
		%tmp_8 = arith.addi %tmp_6, %tmp_7 : i7
		%tmp_9 = arith.constant 25 : i5
		%tmp_10 = arith.extui %tmp_8 : i7 to i8
		%tmp_11 = arith.extui %tmp_9 : i5 to i8
		%tmp_12 = arith.addi %tmp_10, %tmp_11 : i8
		%tmp_13 = arith.extui %tmp_12 : i8 to i32
		// l_x__3_d0_0 aliased to tmp_13
		//     Variable declarations attached to block statement: End
		// Assignment Statement: Start
		%tmp_14 = arith.constant 1 : i1
		%tmp_15 = arith.extui %tmp_14 : i1 to i32
		%tmp_16 = arith.addi %tmp_13, %tmp_15 : i32
		// l_x__3_d0_1 aliased to tmp_16
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_17 = arith.constant 2 : i2
		%tmp_18 = arith.extui %tmp_17 : i2 to i32
		%tmp_19 = arith.subi %tmp_16, %tmp_18 : i32
		// l_x__3_d0_2 aliased to tmp_19
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_20 = arith.constant 10 : i4
		%tmp_21 = arith.extui %tmp_20 : i4 to i32
		%tmp_22 = arith.muli %tmp_19, %tmp_21 : i32
		// l_x__3_d0_3 aliased to tmp_22
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_23 = arith.constant 2 : i2
		%tmp_24 = arith.extui %tmp_23 : i2 to i32
		%tmp_25 = arith.divsi %tmp_22, %tmp_24 : i32
		// l_x__3_d0_4 aliased to tmp_25
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_26 = arith.constant 4 : i3
		%tmp_27 = arith.extui %tmp_26 : i3 to i32
		%tmp_28 = arith.remsi %tmp_25, %tmp_27 : i32
		%tmp_29 = arith.trunci %tmp_28 : i32 to i3
		%tmp_30 = arith.extui %tmp_29 : i3 to i32
		// l_x__3_d0_5 aliased to tmp_30
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_31 = arith.constant 1 : i1
		%tmp_32 = arith.extui %tmp_31 : i1 to i32
		%tmp_33 = arith.shrsi %tmp_30, %tmp_32 : i32
		// l_x__3_d0_6 aliased to tmp_33
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_34 = arith.constant 6 : i3
		%tmp_35 = arith.extui %tmp_34 : i3 to i32
		%tmp_36 = arith.ori %tmp_33, %tmp_35 : i32
		// l_x__3_d0_7 aliased to tmp_36
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_37 = arith.constant 5 : i3
		%tmp_38 = arith.extui %tmp_37 : i3 to i32
		%tmp_39 = arith.andi %tmp_36, %tmp_38 : i32
		%tmp_40 = arith.trunci %tmp_39 : i32 to i3
		%tmp_41 = arith.extui %tmp_40 : i3 to i32
		// l_x__3_d0_8 aliased to tmp_41
		// Assignment Statement: End
		// Assignment Statement: Start
		%tmp_42 = arith.constant 1 : i1
		%tmp_43 = arith.extui %tmp_42 : i1 to i32
		%tmp_44 = arith.shli %tmp_41, %tmp_43 : i32
		// l_x__3_d0_9 aliased to tmp_44
		// Assignment Statement: End
		// Stmt Write: Begin
		dfg.push(%tmp_44) %Out : i32
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

