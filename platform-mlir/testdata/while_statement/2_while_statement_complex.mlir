//-- Definition of actor class: ComplexWhile
dfg.process @ComplexWhile
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
		// l_x__5_d0_0 aliased to tmp_1
		%tmp_2 = arith.constant 0 : i1
		%tmp_3 = arith.extui %tmp_2 : i1 to i32
		// l_y__6_d0_0 aliased to tmp_3
		%tmp_4 = arith.constant 0 : i1
		%tmp_5 = arith.extui %tmp_4 : i1 to i32
		// l_z__3_d0_0 aliased to tmp_5
		%tmp_6 = arith.constant 0 : i1
		%tmp_7 = arith.extui %tmp_6 : i1 to i32
		// l_a__4_d0_0 aliased to tmp_7
		%tmp_8 = arith.constant 0 : i1
		%tmp_9 = arith.extui %tmp_8 : i1 to i32
		// l_b__7_d0_0 aliased to tmp_9
		//     Variable declarations attached to block statement: End
		// While Statement: Begin
		%l_x__5_d0_1, %l_y__6_d0_1, %l_z__3_d0_1 = scf.while(%l_x__5_d1_0 = %tmp_1, %l_y__6_d1_0 = %tmp_3, %l_z__3_d1_0 = %tmp_5) : (i32, i32, i32) -> (i32, i32, i32) {
			// While Statement: Condition Check
			%tmp_10 = arith.constant 4 : i3
			%tmp_11 = arith.extui %tmp_10 : i3 to i32
			%tmp_12 = arith.cmpi slt, %l_x__5_d1_0, %tmp_11 : i32
			scf.condition(%tmp_12) %l_x__5_d1_0, %l_y__6_d1_0, %l_z__3_d1_0 : i32, i32, i32
		} do {
			// While Statement: Body Execution
		^tmp_13(%l_x__5_d1_0: i32, %l_y__6_d1_0: i32, %l_z__3_d1_0: i32):
			// Assignment Statement: Start
			%tmp_14 = arith.constant 0 : i1
			%tmp_15 = arith.extui %tmp_14 : i1 to i32
			// l_y__6_d1_1 aliased to tmp_15
			// Assignment Statement: End
			// While Statement: Begin
			%l_y__6_d1_2, %l_z__3_d1_1 = scf.while(%l_y__6_d2_0 = %tmp_15, %l_z__3_d2_0 = %l_z__3_d1_0) : (i32, i32) -> (i32, i32) {
				// While Statement: Condition Check
				%tmp_16 = arith.constant 4 : i3
				%tmp_17 = arith.extui %tmp_16 : i3 to i32
				%tmp_18 = arith.cmpi slt, %l_y__6_d2_0, %tmp_17 : i32
				scf.condition(%tmp_18) %l_y__6_d2_0, %l_z__3_d2_0 : i32, i32
			} do {
				// While Statement: Body Execution
			^tmp_19(%l_y__6_d2_0: i32, %l_z__3_d2_0: i32):
				// Assignment Statement: Start
				%tmp_20 = arith.constant 1 : i1
				%tmp_21 = arith.extui %tmp_20 : i1 to i32
				%tmp_22 = arith.addi %l_y__6_d2_0, %tmp_21 : i32
				// l_y__6_d2_1 aliased to tmp_22
				// Assignment Statement: End
				// Assignment Statement: Start
				%tmp_23 = arith.constant 1 : i1
				%tmp_24 = arith.extui %tmp_23 : i1 to i32
				%tmp_25 = arith.addi %l_z__3_d2_0, %tmp_24 : i32
				// l_z__3_d2_1 aliased to tmp_25
				// Assignment Statement: End
				scf.yield %tmp_22, %tmp_25: i32, i32
			}
			// While Statement: End
			// Assignment Statement: Start
			%tmp_26 = arith.constant 1 : i1
			%tmp_27 = arith.extui %tmp_26 : i1 to i32
			%tmp_28 = arith.addi %l_x__5_d1_0, %tmp_27 : i32
			// l_x__5_d1_1 aliased to tmp_28
			// Assignment Statement: End
			scf.yield %tmp_28, %l_y__6_d1_2, %l_z__3_d1_1: i32, i32, i32
		}
		// While Statement: End
		// Assignment Statement: Start
		%tmp_29 = arith.constant 0 : i1
		%tmp_30 = arith.extui %tmp_29 : i1 to i32
		// l_b__7_d0_1 aliased to tmp_30
		// Assignment Statement: End
		// While Statement: Begin
		%l_a__4_d0_1, %l_b__7_d0_2 = scf.while(%l_a__4_d1_0 = %tmp_7, %l_b__7_d1_0 = %tmp_30) : (i32, i32) -> (i32, i32) {
			// While Statement: Condition Check
			%tmp_31 = arith.constant 10 : i4
			%tmp_32 = arith.extui %tmp_31 : i4 to i32
			%tmp_33 = arith.cmpi slt, %l_b__7_d1_0, %tmp_32 : i32
			scf.condition(%tmp_33) %l_a__4_d1_0, %l_b__7_d1_0 : i32, i32
		} do {
			// While Statement: Body Execution
		^tmp_34(%l_a__4_d1_0: i32, %l_b__7_d1_0: i32):
			// If Statement: Begin
			%tmp_35 = arith.constant 5 : i3
			%tmp_36 = arith.extui %tmp_35 : i3 to i32
			%tmp_37 = arith.cmpi slt, %l_b__7_d1_0, %tmp_36 : i32
			%l_b__7_d1_1 = scf.if %tmp_37 -> (i32) {
				// Assignment Statement: Start
				%tmp_38 = arith.constant 1 : i1
				%tmp_39 = arith.extui %tmp_38 : i1 to i32
				%tmp_40 = arith.addi %l_b__7_d1_0, %tmp_39 : i32
				// l_b__7_d2_0 aliased to tmp_40
				// Assignment Statement: End
				scf.yield %tmp_40 : i32
			} else {
				// Assignment Statement: Start
				%tmp_41 = arith.constant 2 : i2
				%tmp_42 = arith.extui %tmp_41 : i2 to i32
				%tmp_43 = arith.muli %l_b__7_d1_0, %tmp_42 : i32
				// l_b__7_d2_0 aliased to tmp_43
				// Assignment Statement: End
				scf.yield %tmp_43 : i32
			}
			// If Statement: End
			// Assignment Statement: Start
			%tmp_44 = arith.constant 1 : i1
			%tmp_45 = arith.extui %tmp_44 : i1 to i32
			%tmp_46 = arith.addi %l_a__4_d1_0, %tmp_45 : i32
			// l_a__4_d1_1 aliased to tmp_46
			// Assignment Statement: End
			scf.yield %tmp_46, %l_b__7_d1_1: i32, i32
		}
		// While Statement: End
		// Stmt Write Preprocessing: Begin
		%tmp_47 = arith.constant 10 : i4
		%tmp_48 = arith.extui %tmp_47 : i4 to i32
		%tmp_49 = arith.muli %l_t__1_d0_0, %tmp_48 : i32
		%tmp_50 = arith.addi %l_z__3_d0_1, %tmp_49 : i32
		%tmp_51 = arith.constant 10 : i4
		%tmp_52 = arith.extui %tmp_51 : i4 to i32
		%tmp_53 = arith.muli %l_t__1_d0_0, %tmp_52 : i32
		%tmp_54 = arith.addi %l_a__4_d0_1, %tmp_53 : i32
		// Stmt Write Preprocessing: End
		// StmtConsume not implemented: consume happens on peaking right now
		//     dfg.push operations deferred from StmtWrites: Begin
		dfg.push(%tmp_50) %Out : i32
		dfg.push(%tmp_54) %Out : i32
		//     dfg.push operations deferred from StmtWrites: End
		// Block Statement: End
	}

}

// -- Top Network: Defines structure of actor application
func.func @top(%In: i32) -> (i32)
{

	// -- Instantiate channels between actors
	%queue_from_In, %queue_to_ComplexWhile_In = dfg.channel(4096) : i32
	%queue_from_ComplexWhile_Out, %queue_to_Out = dfg.channel(4096) : i32

	// -- Connect input channels to arguments
	dfg.push(%In) %queue_from_In : i32

	// -- Instantiate actors (also known as nodes/instances)
	dfg.instantiate @ComplexWhile // Instance name: ComplexWhile
		inputs(%queue_to_ComplexWhile_In)
		outputs(%queue_from_ComplexWhile_Out) :
		(i32) -> (i32)

	// -- Connect output channels to return arguments
	%Out = dfg.pull %queue_to_Out : i32

	// -- Return
	func.return %Out: i32
}

