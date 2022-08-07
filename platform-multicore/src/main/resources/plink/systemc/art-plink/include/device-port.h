#ifndef __DEVICE_PORT_H__
#define __DEVICE_PORT_H__

#include <cstdlib>
#include <fstream>
#include <map>
#include <memory>
#include <sstream>
#include <string>
#include <unistd.h>

#include "ocl-macros.h"

namespace sim_device {

class PortAddress {
public:
  explicit PortAddress(const std::string &port_name) : name(port_name) {
    OCL_MSG("Constructing PortAddress %s \n", port_name.c_str());
  };
  explicit PortAddress(const PortAddress &port) : name(port.toString()) {
    OCL_MSG("Copy constructing PortAddress %s \n", name.c_str());
  }
  const std::string &getName() const { return name; }
  const std::string &toString() const { return name; }
  friend bool operator==(const PortAddress &a1, const PortAddress &a2) {
    return a1.name == a2.name;
  }
  PortAddress &operator=(const PortAddress &p) = delete;
  ~PortAddress() {
    OCL_MSG("Destroying port %s \n", name.c_str());
  }

private:
  const std::string name;
};

};     // namespace sim_device
#endif // __DEVICE_PORT_H__