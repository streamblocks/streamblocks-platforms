//-- Definition of actor class: Source
cal.actor @Source ()
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
}

//-- Definition of actor class: Pass
cal.actor @Pass ()
	ports_in(%In: !fifo.output_port<i32>)
	ports_out(%Out: !fifo.input_port<i32>)
{
	// -- Actor body
}

//-- Definition of actor class: Sink
cal.actor @Sink ()
	ports_in(%In: !fifo.output_port<i32>)
{
	// -- Actor body
}

// -- Top Network: Defines structure of actor application
cal.network
{

	// -- Instantiate channels between actors
	%queue_from_pass_Out, %queue_to_sink_In = fifo.create<i32>(1) : !fifo.input_port<i32>, !fifo.output_port<i32>
	%queue_from_source_Out, %queue_to_pass_In = fifo.create<i32>(1) : !fifo.input_port<i32>, !fifo.output_port<i32>

	// -- Instantiate actors (also known as nodes/instances)
	cal.create_instance @Source "source" ()
		ports_out(%queue_from_source_Out: !fifo.input_port<i32>)
	cal.create_instance @Pass "pass" ()
		ports_in(%queue_to_pass_In: !fifo.output_port<i32>)
		ports_out(%queue_from_pass_Out: !fifo.input_port<i32>)
	cal.create_instance @Sink "sink" ()
		ports_in(%queue_to_sink_In: !fifo.output_port<i32>)

}

