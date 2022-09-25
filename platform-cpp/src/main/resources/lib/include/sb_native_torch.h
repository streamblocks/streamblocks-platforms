#pragma once

#include <torch/torch.h>

namespace torch{
    torch::Tensor load_tensor_from_file(std::string fileName);
}