//-- Definition of actor class: IfComplex
dfg.process @IfComplex
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
		%tmp_2 = arith.constant 0 : i1
		%tmp_3 = arith.extui %tmp_2 : i1 to i32
		// l_y__4_d0_0 aliased to tmp_3
		//     Variable declarations attached to block statement: End
		// If Statement: Begin
		%tmp_4 = arith.constant 20 : i5
		%tmp_5 = arith.extui %tmp_4 : i5 to i32
		%tmp_6 = arith.cmpi sge, %l_t__1_d0_0, %tmp_5 : i32
		%l_x__3_d0_1 = scf.if %tmp_6 -> (i32) {
			// Assignment Statement: Start
			%tmp_7 = arith.constant 1 : i1
			%tmp_8 = arith.extui %tmp_7 : i1 to i32
			%tmp_9 = arith.addi %tmp_1, %tmp_8 : i32
			// l_x__3_d1_0 aliased to tmp_9
			// Assignment Statement: End
			scf.yield %tmp_9 : i32
		} else {
			scf.yield %tmp_1 : i32
		}
		// If Statement: End
		// If Statement: Begin
		%tmp_10 = arith.constant 40 : i6
		%tmp_11 = arith.extui %tmp_10 : i6 to i32
		%tmp_12 = arith.cmpi sge, %l_t__1_d0_0, %tmp_11 : i32
		%l_x__3_d0_2, %l_y__4_d0_1 = scf.if %tmp_12 -> (i32, i32) {
			// Assignment Statement: Start
			%tmp_13 = arith.constant 1 : i1
			%tmp_14 = arith.extui %tmp_13 : i1 to i32
			%tmp_15 = arith.addi %l_x__3_d0_1, %tmp_14 : i32
			// l_x__3_d1_0 aliased to tmp_15
			// Assignment Statement: End
			scf.yield %tmp_15, %tmp_3 : i32, i32
		} else {
			// Assignment Statement: Start
			%tmp_16 = arith.constant 1 : i1
			%tmp_17 = arith.extui %tmp_16 : i1 to i32
			%tmp_18 = arith.addi %tmp_3, %tmp_17 : i32
			// l_y__4_d1_0 aliased to tmp_18
			// Assignment Statement: End
			scf.yield %l_x__3_d0_1, %tmp_18 : i32, i32
		}
		// If Statement: End
		// If Statement: Begin
		%tmp_19 = arith.constant 60 : i6
		%tmp_20 = arith.extui %tmp_19 : i6 to i32
		%tmp_21 = arith.cmpi slt, %l_t__1_d0_0, %tmp_20 : i32
		%l_x__3_d0_3 = scf.if %tmp_21 -> (i32) {
			// Assignment Statement: Start
			%tmp_22 = arith.constant 0 : i1
			%tmp_23 = arith.extui %tmp_22 : i1 to i32
			%tmp_24 = arith.addi %l_x__3_d0_2, %tmp_23 : i32
			// l_x__3_d1_0 aliased to tmp_24
			// Assignment Statement: End
			// If Statement: Begin
			%tmp_25 = arith.constant 40 : i6
			%tmp_26 = arith.extui %tmp_25 : i6 to i32
			%tmp_27 = arith.cmpi slt, %l_t__1_d0_0, %tmp_26 : i32
			%l_x__3_d1_1 = scf.if %tmp_27 -> (i32) {
				// Assignment Statement: Start
				%tmp_28 = arith.constant 100000 : i17
				%tmp_29 = arith.extui %tmp_28 : i17 to i32
				%tmp_30 = arith.addi %tmp_24, %tmp_29 : i32
				// l_x__3_d2_0 aliased to tmp_30
				// Assignment Statement: End
				scf.yield %tmp_30 : i32
			} else {
				scf.yield %tmp_24 : i32
			}
			// If Statement: End
			scf.yield %l_x__3_d1_1 : i32
		} else {
			scf.yield %l_x__3_d0_2 : i32
		}
		// If Statement: End
		// If Statement: Begin
		%tmp_31 = arith.constant 80 : i7
		%tmp_32 = arith.extui %tmp_31 : i7 to i32
		%tmp_33 = arith.cmpi slt, %l_t__1_d0_0, %tmp_32 : i32
		scf.if %tmp_33 {
		} else {
		}
		// If Statement: End
		// Stmt Write: Begin
		%tmp_34 = arith.addi %l_t__1_d0_0, %l_x__3_d0_3 : i32
		%tmp_35 = arith.addi %tmp_34, %l_y__4_d0_1 : i32
		dfg.push(%tmp_35) %Out : i32
		// Stmt Write: End
		// StmtConsume not implemented: consume happens on peaking right now
		// Block Statement: End
	}

}

// -- Top Network: Defines structure of actor application
func.func @top(%In: i32) -> (i32)
{

	// -- Instantiate channels between actors
	%queue_from_In, %queue_to_IfComplex_In = dfg.channel(4096) : i32
	%queue_from_IfComplex_Out, %queue_to_Out = dfg.channel(4096) : i32

	// -- Connect input channels to arguments
	dfg.push(%In) %queue_from_In : i32

	// -- Instantiate actors (also known as nodes/instances)
	dfg.instantiate @IfComplex // Instance name: IfComplex
		inputs(%queue_to_IfComplex_In)
		outputs(%queue_from_IfComplex_Out) :
		(i32) -> (i32)

	// -- Connect output channels to return arguments
	%Out = dfg.pull %queue_to_Out : i32

	// -- Return
	func.return %Out: i32
}

