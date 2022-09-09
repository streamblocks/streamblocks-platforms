#pragma once
#include <memory>
#include <sstream>
#include <string>

class TurnusTracer {
public:
  TurnusTracer(const char *fileName) {
    out = std::shared_ptr<std::stringstream>(new std::stringstream);
  };

  virtual ~TurnusTracer(){};

  std::shared_ptr<std::ostream> getOutputStream() { return out; }

private:
  std::shared_ptr<std::stringstream> out;
};