#pragma once
#include <memory>

#include <boost/iostreams/device/file.hpp>
#include <boost/iostreams/filter/gzip.hpp>
#include <boost/iostreams/filtering_stream.hpp>
#include <boost/shared_ptr.hpp>
#include <fstream>
#include <iostream>
#include <string>

class TurnusTracer {
public:
  TurnusTracer(const char *fileName) {
    out = boost::shared_ptr<boost::iostreams::filtering_ostream>(
        new boost::iostreams::filtering_ostream());
    out->push(boost::iostreams::gzip_compressor());
    out->push(boost::iostreams::file_sink(fileName), std::ofstream::binary);
  };
  
  virtual ~TurnusTracer(){};

  boost::shared_ptr<std::ostream> getOutputStream() { return out; }

private:
  boost::shared_ptr<boost::iostreams::filtering_ostream> out;
};