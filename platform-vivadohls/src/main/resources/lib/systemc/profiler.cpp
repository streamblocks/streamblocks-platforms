#include "profiler.h"
#include <sstream>
namespace streamblocks_rtl {
ActionProfileInterface::ActionProfileInterface(const uint32_t index,
                                               const std::string &id)
    : action_index(index), action_id(id){};

M3ActionProfile::M3ActionProfile(const uint32_t index, const std::string &id)
    : ActionProfileInterface(index, id), total_ticks(0), min_ticks(-1),
      max_ticks(0), firings(0) {}

void M3ActionProfile::registerStat(const uint64_t ticks) {
  total_ticks += ticks;
  min_ticks = std::min(ticks, min_ticks);
  max_ticks = std::max(ticks, max_ticks);
  firings++;
}

std::string M3ActionProfile::serialized(const uint32_t indent) {
  std::stringstream ss;
  double average_ticks =
      firings > 0 ? double(total_ticks) / double(firings) : 0;
  ss << (indent > 0 ? std::string(indent, '\t') : "") << "<action id=\""
     << getActionId() << "\" clockcyles=\"" << average_ticks
     << "\" clockcycles-min=\"" << min_ticks << "\" clockcyles-max=\""
     << max_ticks << "\" clockcycles-total=\"" << total_ticks << "\" firings=\""
     << firings << "\" />";
  return ss.str();
}

ActorProfiler::ActorProfiler(const std::string actor_id)
    : actor_id(actor_id), min_ticks(-1), max_ticks(0), total_ticks(0),
      firings(0), miss_firings(0), miss_ticks(0) {}

void ActorProfiler::start(const uint64_t start_ticks) {
  p_queue.push(start_ticks);
}
void ActorProfiler::end(const uint32_t action_index, const uint64_t end_ticks) {

  auto duration = std::max(uint64_t(1), end_ticks - p_queue.front());
  p_queue.pop();
  total_ticks += duration;
  firings++;
  min_ticks = std::min(duration, min_ticks);
  max_ticks = std::max(duration, max_ticks);

  if (stats.find(action_index) != stats.end()) {
    stats[action_index]->registerStat(duration);
  }
}

void ActorProfiler::discard(const uint64_t end_ticks) {

  auto duration = end_ticks - p_queue.front();
  p_queue.pop();
  miss_firings++;
  miss_ticks += duration;
}

void ActorProfiler::registerAction(const uint32_t action_index,
                                   const std::string &actino_id) {
  stats.emplace(action_index,
                std::make_unique<M3ActionProfile>(action_index, actino_id));
}

std::string ActorProfiler::serialized(const uint32_t indent,
                                      const std::string append_entry) {

  std::stringstream ss;

  double average_ticks =
      firings > 0 ? double(total_ticks) / double(firings) : 0;
  std::string indent_str = (indent > 0 ? std::string(indent, '\t') : "");
  ss << indent_str << "<actor id=\"" << actor_id << "\" clockcyles=\""
     << average_ticks << "\" clockcycles-min=\"" << min_ticks
     << "\" clockcyles-max=\"" << max_ticks << "\" clockcycles-total=\""
     << total_ticks << "\" firings=\"" << firings << "\" />" << std::endl
     << std::endl;
  for (auto &_stat : stats) {
    ss << _stat.second->serialized(indent + 1) << std::endl;
  }
  ss << std::string(indent + 1, '\t') << "<wait clockcyles-total=\""
     << miss_ticks << "\" miss-firings=\"" << miss_firings << "\"/>"
     << std::endl;
  ss << append_entry << std::endl;
  ss << indent_str << "</actor>" << std::endl;

  return ss.str();
}
} // namespace streamblocks_rtl