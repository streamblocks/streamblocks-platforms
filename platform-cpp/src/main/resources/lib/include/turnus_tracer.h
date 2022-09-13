#pragma once
#include <memory>
#include <sstream>
#include <string>
#include <zlib.h>

class TurnusTracer {
public:
  TurnusTracer(std::string fileName) : fileName(fileName) {
    data = std::shared_ptr<std::stringstream>(new std::stringstream);
  };

  virtual ~TurnusTracer(){};

  std::shared_ptr<std::stringstream> getOutputStream() { return data; }

  void write() {

    gzFile gz_file;

    // -- Open the file for writing in binary mode
    gz_file = gzopen(fileName.c_str(), "wb");
    unsigned long int file_size = sizeof(char) * data->str().size();

    //Write the data
    gzwrite(gz_file, (void*) (data->str().data()), file_size);

    //close the file
    gzclose(gz_file);
  }

private:
  std::string fileName;
  std::shared_ptr<std::stringstream> data;
};