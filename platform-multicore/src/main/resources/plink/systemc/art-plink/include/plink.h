#ifndef __PLINK_H__
#define __PLINK_H__

#include "actors-rts.h"
#include "device-port.h"
#include "ocl-macros.h"
#include "simulation-kernel.h"
#include <condition_variable>
#include <memory>
#include <mutex>
#include <thread>
#include <vector>
namespace sim_device {

template <typename T> class ConcurrentQueue {

  std::vector<T> _container;
  std::atomic<std::size_t> _head;
  std::atomic<std::size_t> _tail;

public:
  ConcurrentQueue(std::size_t capacity = 1)
      : _container(capacity + 1), _head(0), _tail(0){

                                            };

  bool try_push(const T &val) {
    static_assert(std::is_copy_assignable<T>::value,
                  "T must be copy constructable");
    auto const head = _head.load(std::memory_order_relaxed);
    auto next_head = head + 1;
    if (head == _container.size() - 1) {
      // wrap around
      next_head = 0;
    }
    auto const tail = _tail.load(std::memory_order_acquire);
    if (next_head == tail) {
      return false;
    }
    // copy the val to the container
    _container[head] = val;
    _head.store(next_head, std::memory_order_release);
    return true;
  }

  T peek() {
    static_assert(std::is_copy_constructible<T>::value,
                  "T must be copy constructable");
    auto const tail = _tail.load(std::memory_order_relaxed);
    auto const head = _head.load(std::memory_order_acquire);
    if (tail == head) {
      throw std::runtime_error("Attemped to read an empty queue!");
    }
    return _container[tail];
  }
  bool empty() const {
    auto const tail = _tail.load(std::memory_order_relaxed);
    auto const head = _head.load(std::memory_order_acquire);
    if (tail == head)
      return true;
    else
      return false;
  }
  bool try_pop() {
    static_assert(std::is_copy_constructible<T>::value,
                  "T must be copy constructable");
    auto const tail = _tail.load(std::memory_order_relaxed);
    auto const head = _head.load(std::memory_order_acquire);
    if (tail == head) {
      return false;
    }
    auto next_tail = tail + 1;
    if (tail == _container.size() - 1) {
      // wrap around
      next_tail = 0;
    }
    _tail.store(next_tail, std::memory_order_release);
    return true;
  }
};
class PLink {

public:
  template <typename T> struct PortInfo {
    PortInfo(const std::string &name, uint32_t token_size)
        : name(name), token_size(token_size) {}
    const sim_device::PortAddress name;
    const uint32_t token_size;
  };

  PLink(const std::vector<PortInfo<LocalInputPort>> &input_info,
        const std::vector<PortInfo<LocalOutputPort>> &output_info,
        const uint32_t num_mems, const std::string kernel_name,
        char *profile_file_name, const int vcd_trace_level = 0);
  void allocateInput(const sim_device::PortAddress &name,
                     const std::size_t size);
  void allocateOutput(const sim_device::PortAddress &name,
                      const std::size_t size);

  enum class Action : int {
    FreeUpOutputBuffer,
    StartKernel,
    UpdateIndices,
    NoAction
  };

  Action actionScheduler(AbstractActorInstance *base);

  inline std::size_t getTripCount() { return call_index; }

  void dumpStats(const std::string &file_name);

  void terminate();

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

  /**
   * @brief thread runner for the simulation kernel.
   * This function is given to a thread at construction which takes care of
   * running the simulation
   *
   */
  void kernelRunner();

  bool should_retry;

  enum SimCommand { SIM_START, SIM_TERMINATE, SIM_FINISHED };

  std::unique_ptr<ap_rtl::SimulationKernel> kernel;
  State plink_state;

  std::thread kernel_thread;

  ConcurrentQueue<SimCommand> command_queue;
  ConcurrentQueue<bool> response_queue;

  std::size_t call_index;

  struct DeviceBuffer {
    uint8_t *data_buffer;
    uint32_t meta_buffer[1];
    uint32_t user_alloc_size;
    uint32_t head;
    uint32_t tail;
    uint32_t token_size;
    DeviceBuffer()
        : data_buffer(nullptr), user_alloc_size(0), head(0), tail(0), token_size(-1) {

    }
    void allocate(const std::size_t size, const uint32_t token_size) {
      if (data_buffer) {
        delete[] data_buffer;
      }
      ASSERT(size % token_size == 0, "Unaligned device buffer allocation\n");
      data_buffer = new uint8_t[size];
      user_alloc_size = size / token_size;
      this->token_size = token_size;
    }
    ~DeviceBuffer() {
      if (user_alloc_size)
        delete[] data_buffer;
    }
    char *getHostBufferAtTail() {
      return reinterpret_cast<char *>(&data_buffer[tail * token_size]);
    }
    char *getHostBufferAtHead() {
      return reinterpret_cast<char *>(&data_buffer[head * token_size]);
    }
    uint32_t writeToDeviceBuffer(const uint32_t from_index,
                                 const uint32_t to_index) {
      return __distance__(from_index, to_index);
    }

    uint32_t readFromDeviceBuffer(const uint32_t from_index,
                                  const uint32_t to_index) {
      return __distance__(from_index, to_index);
    }

    uint32_t __distance__(const uint32_t from_index, const uint32_t to_index) {
      if (from_index < to_index) {
        return to_index - from_index;
      } else if (from_index > to_index) {
        return user_alloc_size - from_index + to_index;
      } else {
        return 0;
      }
    }
    ap_rtl::Argument asArgument() {
      return ap_rtl::Argument(
          reinterpret_cast<ap_rtl::Argument::sim_ptr_t>(data_buffer),
          reinterpret_cast<ap_rtl::Argument::sim_ptr_t>(meta_buffer),
          user_alloc_size, head, tail);
    }
  };
  template <typename T> struct Port {
    T sw;
    DeviceBuffer hw;
    const PortAddress address;
    const uint32_t token_size;
    Port(const PortInfo<T> &info)
        : address(info.name), token_size(info.token_size) {}
    inline const PortAddress &getAddress() { return address; }
  };

  using InputPort = Port<LocalInputPort>;
  using OutputPort = Port<LocalOutputPort>;

  inline int pinAvailIn(const LocalInputPort *p) const { return p->available; }
  inline int pinAvailOut(const LocalOutputPort *p) const {
    return p->available;
  }
  inline uint32_t computeFreeSpace(const uint32_t head, const uint32_t tail,
                                   const uint32_t cap) const;

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

  char *profile_file_name;
};
}; // namespace sim_device

#endif // __PLINK_H__