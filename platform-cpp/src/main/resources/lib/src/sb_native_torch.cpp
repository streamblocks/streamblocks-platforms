#include "sb_native_torch.h"

torch::Tensor torch::load_tensor_from_file(std::string fileName) {
  std::ifstream file(fileName, std::ios::binary);
  std::vector<char> data((std::istreambuf_iterator<char>(file)),
                         std::istreambuf_iterator<char>());
  torch::IValue ivalue = torch::pickle_load(data);

  torch::Tensor tensor = ivalue.toTensor();
  return tensor;
}
