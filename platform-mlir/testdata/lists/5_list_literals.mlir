//-- Definition of actor class: ListLiterals
dfg.process @ListLiterals
	inputs(%In: i32)
	outputs(%Out: i32)
{
	dfg.loop inputs(%In: i32)
	{
		// -- Actor body
		// Block Statement: Begin
		//     Variable declarations attached to block statement: Begin
		%l_t1__1_d0_0 = dfg.pull %In : i32
		%tmp_0 = memref.alloca() : memref<4xi7>
		%tmp_1 = arith.constant 100 : i7
		%tmp_2 = arith.constant 0: index
		memref.store %tmp_1, %tmp_0[%tmp_2] : memref<4xi7>
		%tmp_3 = arith.constant 100 : i7
		%tmp_4 = arith.constant 1: index
		memref.store %tmp_3, %tmp_0[%tmp_4] : memref<4xi7>
		%tmp_5 = arith.constant 100 : i7
		%tmp_6 = arith.constant 2: index
		memref.store %tmp_5, %tmp_0[%tmp_6] : memref<4xi7>
		%tmp_7 = arith.constant 100 : i7
		%tmp_8 = arith.constant 3: index
		memref.store %tmp_7, %tmp_0[%tmp_8] : memref<4xi7>
		%tmp_9 = memref.alloca() : memref<4xi32>
		%tmp_10 = arith.constant 0: index
		%tmp_11 = memref.load %tmp_0[%tmp_10] : memref<4xi7>
		%tmp_12 = arith.extui %tmp_11 : i7 to i32
		memref.store %tmp_12, %tmp_9[%tmp_10] : memref<4xi32>
		%tmp_13 = arith.constant 1: index
		%tmp_14 = memref.load %tmp_0[%tmp_13] : memref<4xi7>
		%tmp_15 = arith.extui %tmp_14 : i7 to i32
		memref.store %tmp_15, %tmp_9[%tmp_13] : memref<4xi32>
		%tmp_16 = arith.constant 2: index
		%tmp_17 = memref.load %tmp_0[%tmp_16] : memref<4xi7>
		%tmp_18 = arith.extui %tmp_17 : i7 to i32
		memref.store %tmp_18, %tmp_9[%tmp_16] : memref<4xi32>
		%tmp_19 = arith.constant 3: index
		%tmp_20 = memref.load %tmp_0[%tmp_19] : memref<4xi7>
		%tmp_21 = arith.extui %tmp_20 : i7 to i32
		memref.store %tmp_21, %tmp_9[%tmp_19] : memref<4xi32>
		// l_list__3_d0_0 aliased to tmp_9
		//     Variable declarations attached to block statement: End
		// Assignment Statement: Start
		%tmp_22 = memref.alloca() : memref<4xi32>
		%tmp_23 = arith.constant 100 : i7
		%tmp_24 = arith.extui %tmp_23 : i7 to i32
		%tmp_25 = arith.addi %tmp_24, %l_t1__1_d0_0 : i32
		%tmp_26 = arith.constant 0: index
		memref.store %tmp_25, %tmp_22[%tmp_26] : memref<4xi32>
		%tmp_27 = arith.constant 200 : i8
		%tmp_28 = arith.extui %tmp_27 : i8 to i32
		%tmp_29 = arith.addi %tmp_28, %l_t1__1_d0_0 : i32
		%tmp_30 = arith.constant 1: index
		memref.store %tmp_29, %tmp_22[%tmp_30] : memref<4xi32>
		%tmp_31 = arith.constant 300 : i9
		%tmp_32 = arith.extui %tmp_31 : i9 to i32
		%tmp_33 = arith.addi %tmp_32, %l_t1__1_d0_0 : i32
		%tmp_34 = arith.constant 2: index
		memref.store %tmp_33, %tmp_22[%tmp_34] : memref<4xi32>
		%tmp_35 = arith.constant 400 : i9
		%tmp_36 = arith.extui %tmp_35 : i9 to i32
		%tmp_37 = arith.addi %tmp_36, %l_t1__1_d0_0 : i32
		%tmp_38 = arith.constant 3: index
		memref.store %tmp_37, %tmp_22[%tmp_38] : memref<4xi32>
		%tmp_39 = memref.alloca() : memref<4xi32>
		%tmp_40 = arith.constant 0: index
		%tmp_41 = memref.load %tmp_22[%tmp_40] : memref<4xi32>
		memref.store %tmp_41, %tmp_39[%tmp_40] : memref<4xi32>
		%tmp_42 = arith.constant 1: index
		%tmp_43 = memref.load %tmp_22[%tmp_42] : memref<4xi32>
		memref.store %tmp_43, %tmp_39[%tmp_42] : memref<4xi32>
		%tmp_44 = arith.constant 2: index
		%tmp_45 = memref.load %tmp_22[%tmp_44] : memref<4xi32>
		memref.store %tmp_45, %tmp_39[%tmp_44] : memref<4xi32>
		%tmp_46 = arith.constant 3: index
		%tmp_47 = memref.load %tmp_22[%tmp_46] : memref<4xi32>
		memref.store %tmp_47, %tmp_39[%tmp_46] : memref<4xi32>
		// l_list__3_d0_1 aliased to tmp_39
		// Assignment Statement: End
		// Stmt Write Preprocessing: Begin
		%tmp_48 = arith.constant 0: index
		%tmp_49 = memref.load %tmp_39[%tmp_48] : memref<4xi32>
		%tmp_50 = arith.constant 1: index
		%tmp_51 = memref.load %tmp_39[%tmp_50] : memref<4xi32>
		%tmp_52 = arith.constant 2: index
		%tmp_53 = memref.load %tmp_39[%tmp_52] : memref<4xi32>
		%tmp_54 = arith.constant 3: index
		%tmp_55 = memref.load %tmp_39[%tmp_54] : memref<4xi32>
		// Stmt Write Preprocessing: End
		// StmtConsume not implemented: consume happens on peaking right now
		//     dfg.push operations deferred from StmtWrites: Begin
		dfg.push(%tmp_49) %Out : i32
		dfg.push(%tmp_51) %Out : i32
		dfg.push(%tmp_53) %Out : i32
		dfg.push(%tmp_55) %Out : i32
		//     dfg.push operations deferred from StmtWrites: End
		// Block Statement: End
	}

}

// -- Top Network: Defines structure of actor application
func.func @top(%In: i32) -> (i32)
{

	// -- Instantiate channels between actors
	%queue_from_In, %queue_to_ListLiterals_In = dfg.channel(4096) : i32
	%queue_from_ListLiterals_Out, %queue_to_Out = dfg.channel(4096) : i32

	// -- Connect input channels to arguments
	dfg.push(%In) %queue_from_In : i32

	// -- Instantiate actors (also known as nodes/instances)
	dfg.instantiate @ListLiterals // Instance name: ListLiterals
		inputs(%queue_to_ListLiterals_In)
		outputs(%queue_from_ListLiterals_Out) :
		(i32) -> (i32)

	// -- Connect output channels to return arguments
	%Out = dfg.pull %queue_to_Out : i32

	// -- Return
	func.return %Out: i32
}

