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

ActorProfiler::ActorProfiler(const std::string actor_id, bool dump_trace)
    : actor_id(actor_id), call_index(0), min_ticks(-1), max_ticks(0),
      total_ticks(0), firings(0), miss_firings(0), miss_ticks(0) {
        if (dump_trace) {
          m_trace.open(actor_id + std::string(".trace.xml"), std::ios_base::out);
        }
      }

void ActorProfiler::start(const uint64_t start_ticks) {
  p_queue.push(start_ticks);
}
void ActorProfiler::end(const uint32_t action_index, const uint64_t end_ticks) {

  auto begin_ticks = p_queue.front();
  auto duration = end_ticks - begin_ticks;
  // hack to get around Vivado's weired ap_done
  if (duration == 0) {
    duration = 1;
  } else if (duration == 1) {
    duration = 2;
  }
  // auto duration = std::max(uint64_t(1), end_ticks - begin_ticks);

  p_queue.pop();
  total_ticks += duration;
  firings++;
  min_ticks = std::min(duration, min_ticks);
  max_ticks = std::max(duration, max_ticks);
  auto found = stats.find(action_index);
  if (found != stats.end()) {
    stats[action_index]->registerStat(duration);
    if (m_trace.is_open()) {
      std::string action = found->second->getActionId();
      kernel_trace[call_index].action_trace[action].emplace_back(
        begin_ticks, end_ticks
      );
    }
  }
  kernel_trace[call_index].exec = end_ticks;
}

void ActorProfiler::discard(const uint64_t end_ticks) {

  auto duration = end_ticks - p_queue.front();
  p_queue.pop();
  miss_firings++;
  miss_ticks += duration;
  kernel_trace[call_index].wait = end_ticks;
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
     << total_ticks << "\" firings=\"" << firings << "\" >" << std::endl
     << std::endl;
  for (auto &_stat : stats) {
    ss << _stat.second->serialized(indent + 1) << std::endl;
  }
  ss << std::string(indent + 1, '\t') << "<wait clockcyles-total=\""
     << miss_ticks << "\" miss-firings=\"" << miss_firings << "\"/>"
     << std::endl;
  ss << append_entry << std::endl;

  // sync events
  // for (const auto &k : kernel_trace) {
  //   ss << k.serialized(indent + 1);
  // }
  ss << indent_str << "</actor>" << std::endl;

  return ss.str();
}

void ActorProfiler::syncStart(const uint64_t start_ticks, const bool sleep) {
  if (m_trace.is_open()) {
    if (sleep) {
      kernel_trace[call_index].sleep_trace.emplace_back(start_ticks, 0);
    } else {
      kernel_trace[call_index].sync_trace.emplace_back(start_ticks, 0);
    }
  }

}

void ActorProfiler::syncEnd(const uint64_t end_ticks, const bool sleep) {
  if (m_trace.is_open()) {
    if (sleep) {
      kernel_trace[call_index].sleep_trace.back().second = end_ticks;
    } else {
      kernel_trace[call_index].sync_trace.back().second = end_ticks;
    }
  }
}

void ActorProfiler::kernelStart(const uint64_t start_ticks) {
  if (m_trace.is_open()) {
    kernel_trace.emplace_back(start_ticks);
  }

}

void ActorProfiler::kernelEnd(const uint64_t end_ticks) {
  if (m_trace.is_open()) {
    kernel_trace[call_index++].end = end_ticks;
  }
}

void ActorProfiler::dumpTrace()  {
  if (m_trace) {

    for(auto& k: kernel_trace) {
      k.dump(m_trace, 0);
    }

    // m_trace.close();
  }
}

void
ActorProfiler::KernelTrace::dump(std::ofstream& os, const uint32_t indent) {



  os << std::string(indent, '\t') << "<kernel start=\"" << start << "\" end=\""
     << end << "\" exec=\"" << exec << "\" wait=\"" << wait << "\">" << std::endl;
  for (const auto &t : sync_trace) {
    os << std::string(indent + 1, '\t') << "<sync start=\"" << t.first
       << "\" end=\"" << t.second << "\"/>" << std::endl;
  }

  for (const auto &t : sleep_trace) {
    os << std::string(indent + 1, '\t') << "<sleep start=\"" << t.first
       << "\" end=\"" << t.second << "\"/>" << std::endl;
  }

  for(const auto& action : action_trace) {
    os << std::string(indent + 1, '\t') << "<trace id=\"" <<
      action.first << "\">" << std::endl;
    for(const auto& t: action.second) {
      os << std::string(indent + 2, '\t') << "<inteval start=\"" << t.first
       << "\" end=\"" << t.second << "\"/>" << std::endl;
    }
    os << std::string(indent + 1, '\t') << "</trace>" << std::endl;
  }


  os << std::string(indent, '\t') << "</kernel>" << std::endl;

  sync_trace.clear();
  sleep_trace.clear();
  action_trace.clear();

}




} // namespace streamblocks_rtl