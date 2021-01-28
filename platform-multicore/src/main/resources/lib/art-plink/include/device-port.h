#ifndef __DEVICE_PORT_H__
#define __DEVICE_PORT_H__

#include <cstdlib>
#include <fstream>
#include <map>
#include <memory>
#include <sstream>
#include <string>
#include <unistd.h>

#include "xcl2.h"
// #include <CL/cl_ext.h>
#include "ocl-macros.h"
#include <CL/cl_ext_xilinx.h>

namespace ocl_device {

struct TimePoints {
  cl_ulong queued;
  cl_ulong submit;
  cl_ulong start;
  cl_ulong end;
  TimePoints(cl::Event &event) {
    event.getProfilingInfo<cl_ulong>(CL_PROFILING_COMMAND_QUEUED, &queued);
    event.getProfilingInfo<cl_ulong>(CL_PROFILING_COMMAND_SUBMIT, &submit);
    event.getProfilingInfo<cl_ulong>(CL_PROFILING_COMMAND_START, &start);
    event.getProfilingInfo<cl_ulong>(CL_PROFILING_COMMAND_END, &end);
  }
};

struct EventInfo {
  std::size_t counter;
  bool active;
  std::string message;
  void init(std::string msg) {
    active = false;
    counter = 0;
    message = msg;
  }
};
void CL_CALLBACK callback_handler(cl_event event, cl_int cmd_status,
                                  void *info);
class PortAddress {
public:
  PortAddress(std::string port_name) : name(port_name){};
  std::string getName() const { return name; }
  std::string toString() const { return getName(); }
  friend bool operator==(const PortAddress &a1, const PortAddress &a2) {
    return a1.name == a2.name;
  }

private:
  std::string name;
};

enum class IOType : int { INPUT, OUTPUT };

struct PortType {
  const IOType direction;
  const uint32_t token_size;
  PortType(const IOType direction, const uint32_t token_size)
      : direction(direction), token_size(token_size) {}
};

struct DeviceBufferHandle {
  // -- opencl data buffer descriptor
  cl::Buffer data_buffer;

  // -- opencl meta buffer (contains meta written by either side) descriptor
  cl::Buffer meta_buffer;

  // -- size of the data buffer in tokens
  uint32_t user_alloc_size;

  // -- user head index, signifies the next place a token can be written to
  uint32_t head;

  // -- user tail index, signifies the next place a token can be read from
  uint32_t tail;
};

struct HostBufferHandle {
  // -- host memory buffer
  std::vector<char, aligned_allocator<char>> data_buffer;

  // -- host size buffer
  std::vector<uint32_t, aligned_allocator<uint32_t>> meta_buffer;

  // -- head index
  uint32_t head;

  // -- tail index
  uint32_t tail;
};

class DevicePort {
public:
  DevicePort(PortAddress address, const PortType port_type);

  // DevicePort(const cl::Context &context, PortAddress address,
  //            cl_mem_flags flags, cl::size_type size, cl_int bank_id);
  cl_int allocate(const cl::Context &context, cl::size_type size,
                  cl_int bank_id);

  inline PortAddress &getAddress() { return address; }

  inline uint32_t getTokenSize() { return port_type.token_size; }

  inline std::string toString() { return address.toString(); }

  /**
   * Get the host buffer
   */

  // char *getHostBufferRaw() { return host_buffer.data(); }
  char *getHostBufferAtHead() {

    char *at_head = host_buffer.data_buffer.data() +
                    port_type.token_size * host_buffer.head;
    return at_head;
  }
  char *getHostBufferAtTail() {

    char *at_tail = host_buffer.data_buffer.data() +
                    port_type.token_size * host_buffer.tail;
    return at_tail;
  }
  uint32_t writeToDeviceBuffer(const cl::CommandQueue &q,
                               const uint32_t from_index,
                               const uint32_t to_index);

  uint32_t readFromDeviceBuffer(const cl::CommandQueue &q,
                                const uint32_t from_index,
                                const uint32_t to_index);

  void equeueReadMeta(const cl::CommandQueue &q,
                      const std::vector<cl::Event> *events = NULL);

  void releaseEvents() {
    buffer_event_info[0].active = false;
    buffer_event_info[1].active = false;
    size_event_info.active = false;
  }

private:
  void hostToDeviceTransfer(const cl::CommandQueue &q, const uint32_t size,
                            const uint32_t offset, const uint32_t index);
  void deviceToHostTransfer(const cl::CommandQueue &q, const uint32_t size,
                            const uint32_t offset, const uint32_t index);

public:
  // -- type of the port, input or output?
  const PortType port_type;

  // -- Device circular buffer handle
  DeviceBufferHandle device_buffer;

  //-- Host circular buffer handle
  HostBufferHandle host_buffer;

  // -- address of the port
  PortAddress address;

  // -- buffer read/write event;
  cl::Event buffer_event[2];
  EventInfo buffer_event_info[2];

  // -- buffer size read event;
  cl::Event buffer_size_event;
  EventInfo size_event_info;

  cl_mem_ext_ptr_t extensions;

  // std::vector<Stats> stats;
};
};     // namespace ocl_device
#endif // __DEVICE_PORT_H__