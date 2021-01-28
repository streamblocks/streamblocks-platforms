#include "device-port.h"

namespace ocl_device {

void CL_CALLBACK callback_handler(cl_event event, cl_int cmd_status,
                                  void *info) {

  cl_command_type command;
  clGetEventInfo(event, CL_EVENT_COMMAND_TYPE, sizeof(cl_command_type),
                 &command, NULL);
  const char *command_str;
  EventInfo *event_info = (EventInfo *)info;

  switch (command) {
  case CL_COMMAND_NDRANGE_KERNEL:
    command_str = "kernel";
    break;
  case CL_COMMAND_MIGRATE_MEM_OBJECTS:
    command_str = "migrate";
    break;
  case CL_COMMAND_WRITE_BUFFER:
    command_str = "write";
    break;
  case CL_COMMAND_READ_BUFFER:
    command_str = "read";
    break;
  default:
    command_str = "unsupported";
  }

  std::string msg;
  if (OCL_VERBOSE) {
    std::stringstream msg_builder;
    msg_builder << "Completed " << command_str << " (" << event_info->counter
                << "): " << event_info->message << std::endl;
    msg = msg_builder.str();
  }
  event_info->counter++;
  OCL_MSG("%s", msg.c_str());
  fflush(stdout);
}

/*****************************************************************************
 *
 *                                Device Port
 *
 *****************************************************************************/
/**
 * Constructs a DevicePort object, the port buffer is not initialized and should
 * be later allocated using the allocate method
 * @param address port addres
 * @param port_type type of the port, which includes its direction (i/o) and the
 *                  token size on the port
 **/

DevicePort::DevicePort(PortAddress address, PortType port_type)
    : address(address), port_type(port_type) {

  std::stringstream builder;
  builder << address.getName() << " buffer event[0]";
  buffer_event_info[0].init(builder.str());

  std::stringstream builder1;
  builder1 << address.getName() << " buffer event[1]";
  buffer_event_info[1].init(builder.str());

  std::stringstream builder2;
  builder2 << address.getName() << " buffer size event";
  size_event_info.init(builder2.str());

  // ocl_buffer.system_alloc_size = 0;
  // ocl_buffer.user_alloc_size = 0;
  // ocl_buffer.user_tail = 0;
  // ocl_buffer.user_head = 0;
}

cl_int DevicePort::allocate(const cl::Context &context,
                            const cl::size_type size, const cl_int bank_id) {
  cl_int err;

  extensions.flags = bank_id;
  extensions.obj = 0;
  extensions.param = 0;
  OCL_MSG("Using bank %d\n", bank_id);

  OCL_ASSERT(size % port_type.token_size == 0,
             "Misaligned allocation on port %s\n", address.toString().c_str());

  cl_int flags = CL_MEM_EXT_PTR_XILINX;
  if (port_type.direction == IOType::INPUT) {
    flags |= CL_MEM_READ_ONLY;
  } else { // OUTPUT
    flags |= CL_MEM_WRITE_ONLY;
  }

  device_buffer.data_buffer = cl::Buffer(context, flags | CL_MEM_EXT_PTR_XILINX,
                                         size, &extensions, &err);

  device_buffer.user_alloc_size = size / port_type.token_size;

  host_buffer.data_buffer.resize(size);

  cl_int err2;
  device_buffer.meta_buffer =
      cl::Buffer(context, CL_MEM_WRITE_ONLY | CL_MEM_EXT_PTR_XILINX,
                 sizeof(uint32_t), &extensions, &err2);

  host_buffer.meta_buffer.resize(1);

  if (err == CL_SUCCESS && err2 == CL_SUCCESS)
    return CL_SUCCESS;
  else if (err != CL_SUCCESS)
    return err;
  else
    return err2;
}

/**
 * @brief transfers a sub buffer from device_head to host_head
 * This function transfers the host buffer from index device_head to
 * index host_head. It will not do anything if device_head == host_head
 *
 * @param q an opencl command queue
 * @return uint32_t the number of bytes transferred
 */

uint32_t DevicePort::writeToDeviceBuffer(const cl::CommandQueue &q) {
  cl_int err;

  uint32_t total_bytes = 0;
  OCL_ASSERT(port_type.direction == IOType::INPUT,
             "Can not write to output port %s\n", address.toString().c_str());

  OCL_ASSERT(buffer_event_info[0].active == false,
             "Illegal write buffer for %s\n", address.toString().c_str());
  OCL_ASSERT(buffer_event_info[1].active == false,
             "Illegal write buffer for %s\n", address.toString().c_str());

  // -- Some info about the circular buffer
  // -- We want to transfer the data in the host buffer to the device buffer
  // starting from host_buffer[head] to host_buffer[tail]. However, we make
  // sure that head is not equal to tail after the write since the input stage
  // would treat the case tail == head as the case that there are no valid
  // tokens in the buffer. The policy is that "tail can reach the head" but
  // "head can not reach the tail", i.e., "head can reach tail - 1" because
  // the head should point to the last entry in the buffer in which it can write
  // and then increase the head such that the new head still points to an empty
  // place. If the head reaches tail - 1, then the next place to write is tail -
  // 1 and that place is indeed empty, however, with head reaching tail the next
  // place to write will not be an empty place

  // The head of the buffer should be set by entity using the port, an INPUT
  // port head (in ocl_buffer.user_head) is not modified by the device port
  // object itself.

  // The follwoing are assumed (head an tail belong to differnt ends, i.e., one
  // is from the device and the other from the host)
  // (1) head == tail implies the buffer is empty
  // (2) head == tail - 1 implies the buffer is full, note
  // that the actual buffer capacity is therefore one token less than the
  // allocated amount If the head == tail, and new data for writing is
  // available, the PLINK (or any other entity) can optionally set the tail to 0
  // and increase the head from 0 up to alloc_size - 1 to avoid creating sub
  // buffers

  if (device_buffer.head == host_buffer.head ||
      host_buffer.head == device_buffer.tail) {
    // -- there is nothing new to be transferred to the device buffer
    OCL_MSG("%s::writeToDeviceBuffer::skipped\n", address.toString().c_str());

  } else { // there is something to transfer

    if (device_buffer.head < host_buffer.head) {
      // -- this is the easy case, a single transfer
      // transfer the data from device_head to host_head - 1 (inclusive)
      uint32_t tokens_to_transfer = host_buffer.head - device_buffer.head;
      uint32_t bytes_to_transfer = port_type.token_size * tokens_to_transfer;

      uint32_t transfer_offset = device_buffer.head * port_type.token_size;
      hostToDeviceTransfer(q, bytes_to_transfer, transfer_offset, 0);

      total_bytes += bytes_to_transfer;
    } else { // device_head > host_head
      // -- the uneasy case, we need to make two transfers
      // the first transfer is from device_head to alloc_size - 1 (inclusive)

      uint32_t tokens_to_transfer =
          device_buffer.user_alloc_size - device_buffer.head;
      uint32_t bytes_to_transfer = port_type.token_size * tokens_to_transfer;
      uint32_t transfer_offset = port_type.token_size * device_buffer.head;
      hostToDeviceTransfer(q, bytes_to_transfer, transfer_offset, 0);

      total_bytes += bytes_to_transfer;

      // now transfer from 0 to host_head - 1 (inclusive)
      tokens_to_transfer = device_buffer.head;
      bytes_to_transfer = port_type.token_size * tokens_to_transfer;
      hostToDeviceTransfer(q, bytes_to_transfer, 0, 1);

      total_bytes += bytes_to_transfer;
    }
  }

  return total_bytes;
}

/**
 * @brief helper method to transfer data from host to device
 *
 * @param q command queue
 * @param size size of the transfer in bytes
 * @param offset offset of the transfer in bytes
 * @param index transfer index, should be either 0 (primary) or 1 (secondary)
 */
void DevicePort::hostToDeviceTransfer(const cl::CommandQueue &q,
                                      const uint32_t size,
                                      const uint32_t offset,
                                      const uint32_t index) {

  OCL_MSG("%s::hostToDeviceTransfer[%u](bytes=%u, offset=%u, token_size=%u)\n",
          address.toString().c_str(), index, size, offset,
          port_type.token_size);
  cl_int err;
  OCL_CHECK(err,
            err = q.enqueueWriteBuffer(
                device_buffer.data_buffer, // -- device buffer
                CL_FALSE,                  // -- nonblocking transfer
                offset,                    // -- tranfser offset in bytes
                size,                      // -- size of the transfer in bytes
                host_buffer.data_buffer.data(), // -- host buffer pointer
                NULL,                           // -- do not wait on any events
                &buffer_event[index] // -- register an event for call back
                ));
  buffer_event_info[index].active = true;

  buffer_event[index].setCallback(CL_COMPLETE, callback_handler,
                                  &buffer_event_info[index]);
}

/**
 * @brief reads the device buffer back to the host buffer according to the head
 * and tail
 *
 * This function transfers the contents of
 * host_buffer.data_buffer[host_buffer.head .. device_buffer.head] from the
 * device to the host. Not that if the host_buffer.head = device_buffer.head
 * then this function does nothing.
 *
 * It is up to the calling entitiy to manage host_buffer.head and
 * device_buffer.head such that everytime this function is called, the
 * device_buffer.head has surpassed host_buffer.head
 *
 * @param q opencl command queue
 * @return uint32_t total bytes transferred
 */
uint32_t DevicePort::readFromDeviceBuffer(const cl::CommandQueue &q) {

  uint32_t total_bytes = 0;
  OCL_ASSERT(port_type.direction == IOType::OUTPUT,
             "Can not read from input port %s\n", address.toString().c_str());

  OCL_ASSERT(buffer_event_info[0].active == false,
             "Illegal write buffer for %s\n", address.toString().c_str());
  OCL_ASSERT(buffer_event_info[1].active == false,
             "Illegal write buffer for %s\n", address.toString().c_str());

  cl_int err;

  if (device_buffer.head == host_buffer.head) {
    // -- do nothing because the device has not produced anything
    // more than what was already transferred to the host buffer.
    OCL_MSG("%s::readFromDeviceBuffer::skipped\n", address.toString().c_str());
  } else {

    if (device_buffer.head > host_buffer.head) {
      // easy case, single transfer
      uint32_t tokens_to_transfer = device_buffer.head - host_buffer.head;
      uint32_t bytes_to_transfer = tokens_to_transfer * port_type.token_size;
      uint32_t transfer_offset = device_buffer.head * port_type.token_size;

      deviceToHostTransfer(q, bytes_to_transfer, transfer_offset, 0);

      total_bytes += tokens_to_transfer;
    } else { // device_buffer.head > host_buffer.head

      uint32_t tokens_to_transfer =
          device_buffer.user_alloc_size - host_buffer.head;
      uint32_t bytes_to_transfer = tokens_to_transfer * port_type.token_size;
      uint32_t transfer_offset = device_buffer.head * port_type.token_size;
      deviceToHostTransfer(q, bytes_to_transfer, transfer_offset, 0);
      total_bytes += bytes_to_transfer;

      tokens_to_transfer = device_buffer.head;
      bytes_to_transfer = tokens_to_transfer * port_type.token_size;
      deviceToHostTransfer(q, bytes_to_transfer, 0, 1);

      total_bytes += bytes_to_transfer;
    }
  }

  return total_bytes;
}
/**
 * @brief Helper method to transfer a (sub)buffer from device to host
 *
 * @param q command queue
 * @param size size of the transfer in bytes
 * @param offset offset of the transfer in bytes
 * @param index transfer index, should be either 0 (primary) or 1 (secondary)
 */
void DevicePort::deviceToHostTransfer(const cl::CommandQueue &q,
                                      const uint32_t size,
                                      const uint32_t offset,
                                      const uint32_t index) {
  OCL_MSG("%s::deviceToHostTransfer[%u](bytes=%u, offset=%u, tokens_size=%u)\n",
          address.toString().c_str(), index, size, offset,
          port_type.token_size);

  cl_int err;
  OCL_CHECK(err,
            err = q.enqueueReadBuffer(
                device_buffer.data_buffer, // -- device buffer
                CL_FALSE,                  // -- nonblocking read
                offset,                    // -- transfer offset in bytes
                size,                      // -- size of the transfer in bytes
                host_buffer.data_buffer.data(), // -- host buffer pointer
                NULL,                           // -- do not wait on any events
                &buffer_event[index]            // -- event to generate
                ));
  buffer_event_info[index].active = true;
  buffer_event[index].setCallback(CL_COMPLETE, callback_handler,
                                  &buffer_event_info[index]);
}

/**
 * @brief Enqueue reading the meta buffer from the device
 * Enqueues opencl read operation for the meta buffer on the device to the
 * host. Should be called appropriate preceeding events, e.g., events denoting
 * that the kernel is finished execution.
 * @param q opencl command queue
 * @param events events that should complete before the read operation is
 *               enqueued
 */
void DevicePort::equeueReadMeta(const cl::CommandQueue &q,
                                const std::vector<cl::Event> *events) {

  cl_int err;
  OCL_ASSERT(size_event_info.active == false, "Illegal read size for %s\n",
             address.toString().c_str());
  OCL_MSG("Enqueue read size %s\n", address.toString().c_str());
  OCL_CHECK(err, err = q.enqueueReadBuffer(
                     device_buffer.meta_buffer,      // device buffer object
                     CL_FALSE,                       // blocking = false
                     0,                              // offset, unsed
                     sizeof(uint32_t),               // size of read transfer
                     host_buffer.meta_buffer.data(), // pointer to host memory
                     events,                         // events to wait on
                     &buffer_size_event              // generated event
                     ));
  size_event_info.active = true;
  buffer_size_event.setCallback(CL_COMPLETE, callback_handler,
                                &size_event_info);
}
}; // namespace ocl_device