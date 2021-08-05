#ifndef __ACTION_PROFILER_H__
#define __ACTION_PROFILER_H__
#include <map>
#include <memory>
#include <queue>
#include <string>
#include <fstream>
#include <iostream>
namespace streamblocks_rtl {

class ActionProfileInterface {
public:
  ActionProfileInterface(const uint32_t index, const std::string &id);

  /**
   * @brief Register and action tick count
   *
   * @param ticks_count number of ticks an action took
   */
  virtual void registerStat(const uint64_t ticks_count) = 0;

  /**
   * @brief Get the action id
   *
   * @return std::string
   */
  std::string getActionId() const { return action_id; }

  /**
   * @brief serialize the statistics into an XML element
   *
   * @return std::string an xml element of representing the action profile
   */
  virtual std::string serialized(const uint32_t indent = 0) = 0;

private:
  const uint32_t action_index;
  const std::string action_id;
};

/**
 * @brief Min, Mean and Max (M3) Action profile container
 *
 */
class M3ActionProfile : public ActionProfileInterface {

public:
  M3ActionProfile(const uint32_t index, const std::string &id);
  void registerStat(const uint64_t ticks_count) override;
  std::string serialized(const uint32_t indent = 0) override;

private:
  struct ActionTrace {
    std::vector<std::pair<uint64_t, uint64_t>> trace;
    uint64_t start;
    uint64_t end;
    ActionTrace(const uint64_t start) : start(start), end(0) {};
    std::string serialized(const uint32_t indent) const;
  };

  uint64_t total_ticks;
  uint64_t min_ticks;
  uint64_t max_ticks;
  uint64_t firings;
};



class ActorProfiler {
public:
  ActorProfiler(const std::string actor_id, bool dump_trace = false);
  void start(const uint64_t start_ticks);
  void end(const uint32_t action_index, const uint64_t end_ticks);
  void discard(const uint64_t end_ticks);
  void registerAction(const uint32_t action_index,
                      const std::string &action_id);
  std::string serialized(const uint32_t indent = 0,
                         const std::string append_entry = "");
  void kernelStart(const uint64_t start_ticks);
  void kernelEnd(const uint64_t end_ticks);
  void syncStart(const uint64_t start_ticks, const bool sleep);
  void syncEnd(const uint64_t end_ticks, const bool sleep);
  std::string getId() const { return actor_id; }
  void dumpTrace();

private:
  // action stats
  std::map<uint32_t, std::unique_ptr<M3ActionProfile>> stats;
  // actor stats
  const std::string actor_id;
  std::queue<uint64_t> p_queue;

  struct KernelTrace {
    std::vector<std::pair<uint64_t, uint64_t>> sleep_trace;
    std::vector<std::pair<uint64_t, uint64_t>> sync_trace;
    uint64_t exec; // last exec
    uint64_t wait; // last wait
    uint64_t start;
    uint64_t end;
    KernelTrace(const uint64_t start) : exec(0), wait(0), end(0), start(start) {};
    std::map<std::string, std::vector<std::pair<uint64_t, uint64_t>>> action_trace;
    void dump(std::ofstream& os, const uint32_t inden);
  };


  uint64_t call_index;
  std::vector<KernelTrace> kernel_trace;
  uint64_t total_ticks;
  uint64_t min_ticks;
  uint64_t max_ticks;
  uint64_t firings;
  uint64_t miss_firings;
  uint64_t miss_ticks;

  std::ofstream m_trace;

};
} // namespace streamblocks_rtl
#endif