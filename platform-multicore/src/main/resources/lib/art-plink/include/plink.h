#ifndef __PLINK_H__
#define __PLINK_H__

#include "actors-rts.h"
#include "device-port.h"
#include "ocl-macros.h"

#include <vector>
namespace ocl_device {
class PLink {

public:
  template <typename T> struct PortInfo {
    PortInfo(const std::string& name, uint32_t token_size)
        : name(name), token_size(token_size) {}
    const ocl_device::PortAddress name;
    const uint32_t token_size;
  };

  PLink(const std::vector<PortInfo<LocalInputPort>> &input_info,
        const std::vector<PortInfo<LocalOutputPort>> &output_info,
        const uint32_t num_mems, const std::string kernel_name,
        const std::string dir, const bool enable_stats = false);
  void allocateInput(const ocl_device::PortAddress &name,
                     const cl::size_type size);
  void allocateOutput(const ocl_device::PortAddress &name,
                      const cl::size_type size);

  enum class Action : int {
    FreeUpOutputBuffer,
    StartKernel,
    UpdateIndices,
    NoAction
  };

  Action actionScheduler(AbstractActorInstance *base);

  inline std::size_t getTripCount() { return call_index; }

  void dumpStats(const std::string& file_name);

private:
  // -- STATES
  enum class State {
    INIT,
    FREE_UP_OUTPUT_SPACE,
    CHECK_KERNEL_CONDITIONS,
    START_KERNEL,
    POLL_KERNEL,
    UPDATE_INDICES,
    POLL_READ
  };

  // -- CONDITIONS
  /**
   * @brief C1: Checks whether plink can write to the software output to free up
   * device buffers
   *
   * @return true If there is space in the output and if there is data in a host
   * buffer
   * @return false If there is no space in the output or no data available
   * internally
   */
  bool checkCanWriteToOutput() const;
  /**
   * @brief C2.1: Checks whether new data can be sent to the hardware
   *
   * @return true If new data is available and there is space in the device
   * buffer
   * @return false otherwise
   */
  bool checkCanSendInput() const;

  /**
   * @brief C2.2: checkes whether there is some space in the output device
   * buffer
   *
   * @return true if there is space in the output device buffer
   * @return false
   */
  bool checkHardwareOutputSpace() const;
  /**
   * @brief C2.3: Checks whether hardware should be enabled for leftover data
   *
   * @return true If the previous invokaction of the hardware resulted in output
   * back pressure
   * @return false
   */
  bool checkShouldRetry() const;

  /**
   * @brief C3: Checkes whether kernel execution and reading of meta buffers is
   * finished
   *
   * @return true
   * @return false
   */
  bool checkKernelFinished() const;

  /**
   * @brief C4: Checks whether reading of device buffers back to host buffers is
   * finished
   *
   * @return true
   * @return false
   */
  bool checkReadFinished() const;

  // -- ACTIONS

  /**
   * @brief read from the host buffers and write to software buffers
   *
   */
  void actionFreeUpOutputBuffer();

  /**
   * @brief enqueue kernel execution
   * Three steps are taken:
   * 1. Data is copied from LocalInput buffers to host buffers
   * 2. Kernel aguments are set (sync)
   * 3. host buffers are transferred to the device buffers (async)
   * 4. Kernel is started (async)
   * 5. Reading meta buffers is enqueued (async)
   */
  void actionStartKernel();

  /**
   * @brief Updates the head and tail indices
   *
   * For every input, the host and device tails are according to the
   * values in their corresponding meta buffers. The head for both the host
   * and the device are updated to the value for the host (newer one).
   *
   * For every output, the device head updated from its corresponding meta
   * buffer. Then using the new device head and the old host head, data buffer
   * reads are asynchronously enqueued. The condtiion for retrying is
   * evaluated and the host head is set to the device head (newer value).
   *
   *
   * After this function it is ensured that
   * forall port in {inputs, outputs}:
   *  port.hw.device_buffer.head == port.hw.host_buffer.head
   *  port.hw.device_buffer.tail == port.hw.host_buffer.tail
   *
   */
  void actionUpdateIndices();

  /**
   * @brief Cleans ups the execution state of the kernel, should be called
   * before calling the actionStartKernel again.
   *
   */
  void actionCleanUp();

  State plink_state;
  bool should_retry;

  cl::Context context;
  cl::Platform platform;
  cl::Device device;
  cl::CommandQueue command_queue;
  cl::Program program;

  cl::Kernel kernel;

  std::vector<cl::Event> kernel_event;
  EventInfo kernel_event_info;

  std::vector<cl::Event> kernel_wait_events;

  std::size_t call_index;

  template <typename T> struct Port {
    T sw;
    DevicePort hw;
    Port(const PortAddress& address, PortType port_type, const bool enable_stats = false) : hw(address, port_type, enable_stats) {


    }

  };

  using InputPort = Port<LocalInputPort>;
  using OutputPort = Port<LocalOutputPort>;

  inline int pinAvailIn(const LocalInputPort *p) const { return p->available; }
  inline int pinAvailOut(const LocalOutputPort *p) const {
    return p->available;
  }
  inline uint32_t computeFreeSpace(const uint32_t head, const uint32_t tail,
                                   const uint32_t cap) const;

  inline bool eventComplete(const cl::Event &event) const;
  /**
   * @brief
   *
   * @param port
   * @param buff source buffer
   * @param n number of tokens (not bytes) to write to the software fifo
   */
  void pinWriteRepeat(OutputPort &port, char *buff, int n);

  /**
   * @brief
   *
   * @param port
   * @param buff
   * @param n number of tokens (not bytes) to read from the software fifo
   */
  void pinReadRepeat(InputPort &port, char *buff, int n);

  std::vector<InputPort> inputs;
  std::vector<OutputPort> outputs;

  const bool collect_stats;

public:
  const cl_int banks[4] = {XCL_MEM_DDR_BANK0, XCL_MEM_DDR_BANK1,
                               XCL_MEM_DDR_BANK2, XCL_MEM_DDR_BANK3};
};
}; // namespace ocl_device

#endif // __PLINK_H__