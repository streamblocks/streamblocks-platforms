#include "device-port.h"

namespace ocl_device {


int PortAddress::count = 0;
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

DevicePort::DevicePort(const PortAddress& address, PortType port_type,
  const bool enable_stats)
    : address(address), port_type(port_type), collect_stats(enable_stats) {
  if (collect_stats) {
    printf("Stat collection is enable for port %s\n", address.toString().c_str());
  }
  OCL_MSG("Constructing port %s\n", address.getName().c_str());
  std::stringstream builder;
  builder << this->address.getName() << " buffer event[0]";
  buffer_event_info[0].init(builder.str());

  std::stringstream builder1;
  builder1 << this->address.getName() << " buffer event[1]";
  buffer_event_info[1].init(builder.str());

  std::stringstream builder2;
  builder2 << this->address.getName() << " buffer size event";
  size_event_info.init(builder2.str());
  device_buffer.head = 0;
  device_buffer.tail = 0;
  host_buffer.head = 0;
  host_buffer.tail = 0;
}

cl_int DevicePort::allocate(const cl::Context &context,
                            const cl::size_type size, const cl_int bank_id) {
  cl_int err;

  extensions.flags = bank_id;
  extensions.obj = 0;
  extensions.param = 0;

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

  OCL_MSG("%s::allocate(bytes=%lu, bank=%u, tokens=%u)\n",
          address.toString().c_str(), size, bank_id,
          device_buffer.user_alloc_size);

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

uint32_t DevicePort::writeToDeviceBuffer(const cl::CommandQueue &q,
                                         const uint32_t from_index,
                                         const uint32_t to_index) {
  cl_int err;

  uint32_t total_bytes = 0;
  OCL_ASSERT(port_type.direction == IOType::INPUT,
             "Can not write to output port %s\n", address.toString().c_str());

  OCL_ASSERT(buffer_event_info[0].active == false,
             "Illegal write buffer for %s\n", address.toString().c_str());
  OCL_ASSERT(buffer_event_info[1].active == false,
             "Illegal write buffer for %s\n", address.toString().c_str());

  if (to_index == from_index) {
    // -- there is nothing new to be transferred to the device buffer
    OCL_MSG("%s::writeToDeviceBuffer::skipped\n", address.toString().c_str());

  } else { // there is something to transfer

    uint32_t cap = device_buffer.user_alloc_size;
    uint32_t tokens_to_write = 0;

    if (from_index < to_index) {
      tokens_to_write = to_index - from_index;

    } else { // from_index > to_index, i.e., wrap arround
      tokens_to_write = cap - from_index + to_index;
    }

    uint32_t tx_ix = 0;
    const uint32_t token_size = port_type.token_size;

    total_bytes = tokens_to_write * token_size;

    auto offset_index = from_index;
    if (tokens_to_write + from_index >= cap) {
      // wrap around

      hostToDeviceTransfer(q, (cap - from_index) * token_size,
                           offset_index * token_size, tx_ix);
      tx_ix++;
      tokens_to_write -= cap - from_index;
      offset_index = 0;
    }

    if (tokens_to_write > 0) {

      hostToDeviceTransfer(q, tokens_to_write * token_size,
                           offset_index * token_size, tx_ix);
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
  OCL_CHECK(
      err,
      err = q.enqueueWriteBuffer(
          device_buffer.data_buffer, // -- device buffer
          CL_FALSE,                  // -- nonblocking transfer
          offset,                    // -- tranfser offset in bytes
          size,                      // -- size of the transfer in bytes
          host_buffer.data_buffer.data() + offset, // -- host buffer pointer
          NULL,                // -- do not wait on any events
          &buffer_event[index] // -- register an event for call back
          ));
  buffer_event_info[index].active = true;

  buffer_event[index].setCallback(CL_COMPLETE, callback_handler,
                                  &buffer_event_info[index]);

  if (collect_stats) {
    buffer_event_info[index].setTransferMetrics(size, offset);
  }
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
uint32_t DevicePort::readFromDeviceBuffer(const cl::CommandQueue &q,
                                          const uint32_t from_index,
                                          const uint32_t to_index) {

  uint32_t total_bytes = 0;
  OCL_ASSERT(port_type.direction == IOType::OUTPUT,
             "Can not read from input port %s\n", address.toString().c_str());

  OCL_ASSERT(buffer_event_info[0].active == false,
             "Illegal write buffer for %s\n", address.toString().c_str());
  OCL_ASSERT(buffer_event_info[1].active == false,
             "Illegal write buffer for %s\n", address.toString().c_str());

  cl_int err;

  if (from_index == to_index) {
    // -- do nothing
    OCL_MSG("%s::readFromDeviceBuffer::skipped\n", address.toString().c_str());
  } else {

    uint32_t cap = device_buffer.user_alloc_size;
    uint32_t tokens_to_read = 0;
    if (from_index < to_index) {
      tokens_to_read = to_index - from_index;
    } else { // from_index > to_index, i.e., wrap around read
      tokens_to_read = cap - from_index + to_index;
    }

    uint32_t tx_ix = 0;
    const uint32_t token_size = port_type.token_size;
    auto offset_index = from_index;

    total_bytes = tokens_to_read * token_size;

    if (tokens_to_read + from_index >= cap) {
      // wrap around
      deviceToHostTransfer(q, (cap - from_index) * token_size,
                           offset_index * token_size, tx_ix);
      tx_ix++;
      tokens_to_read -= cap - from_index;
      offset_index = 0;
    }

    if (tokens_to_read > 0) {
      deviceToHostTransfer(q, tokens_to_read * token_size,
                           offset_index * token_size, tx_ix);
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
  OCL_CHECK(
      err,
      err = q.enqueueReadBuffer(
          device_buffer.data_buffer, // -- device buffer
          CL_FALSE,                  // -- nonblocking read
          offset,                    // -- transfer offset in bytes
          size,                      // -- size of the transfer in bytes
          host_buffer.data_buffer.data() + offset, // -- host buffer pointer
          NULL,                // -- do not wait on any events
          &buffer_event[index] // -- event to generate
          ));
  buffer_event_info[index].active = true;
  buffer_event[index].setCallback(CL_COMPLETE, callback_handler,
                                  &buffer_event_info[index]);
  if (collect_stats) {
    buffer_event_info[index].setTransferMetrics(size, offset);
  }
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
void DevicePort::enqueueReadMeta(const cl::CommandQueue &q,
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

std::string DevicePort::serializedStats(const int indent) {
  std::stringstream ss;
  std::string outer_indent = "";
  for (int i = 0; i < indent; i++)
    outer_indent += "\t";

  std::string inner_indent = outer_indent + "\t";

  ss << outer_indent << "{" << std::endl;
  {
    ss << inner_indent << "\"name\": \"" << this->address.toString() << "\"," << std::endl;
    ss << inner_indent << "\"token_size\":  "<< this->port_type.token_size << "," << std::endl;
    ss << inner_indent << "\"alloc_size\":" << this->device_buffer.user_alloc_size << "," << std::endl;
    ss << inner_indent << "\"logged_transfers\":" << this->stats.size() << "," << std::endl;
    ss << inner_indent << "\"stats\": [" << std::endl;
    {
      for(auto it = this->stats.begin(); it != this->stats.end(); it++) {
        ss << it->serialized(indent + 2);
        if (it != this->stats.end() - 1) {
          ss << ",";
        }
        ss << std::endl;
      }
    }
    ss << inner_indent << "]" << std::endl;
  }
  ss << outer_indent << "}";

  return ss.str();
}
}; // namespace ocl_device