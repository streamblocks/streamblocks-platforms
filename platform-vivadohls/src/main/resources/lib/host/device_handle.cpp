#include "device_handle.hpp"

void CL_CALLBACK completion_handler(cl_event event, cl_int cmd_status,
                                    void *info) {

  cl_command_type command;
  OCL_CHECK(clGetEventInfo(event, CL_EVENT_COMMAND_TYPE,
                           sizeof(cl_command_type), &command, nullptr));
  const char *command_str;
  eventInfo *event_info = (eventInfo *)info;

  switch (command) {
  case CL_COMMAND_NDRANGE_KERNEL:
    command_str = "kernel";
    break;
  case CL_COMMAND_MIGRATE_MEM_OBJECTS:
    command_str = "migrate";
    break;
  case CL_COMMAND_WRITE_BUFFER:
    command_str = "write";
    break;
  case CL_COMMAND_READ_BUFFER:
    command_str = "read";
    break;
  default:
    command_str = "unsupported";
  }
  char callback_msg[1024];
  sprintf(callback_msg, "<<Completed %s (%d)>>: %s\n", command_str,
          event_info->counter, (char *)event_info->msg);
  event_info->counter++;
  OCL_MSG("%s", callback_msg);
  fflush(stdout);
}
void on_completion(cl_event event, void *info) {
  OCL_CHECK(
      clSetEventCallback(event, CL_COMPLETE, completion_handler, (void *)info));
}

DeviceHandle::DeviceHandle(char *kernel_name, char *target_device_name,
                           char *dir, bool hw_emu) {
  // cl_int err;
  buffer_size = BUFFER_SIZE;
  num_inputs = NUM_INPUTS;
  num_outputs = NUM_OUTPUTS;
  mem_alignment = MEM_ALIGNMENT;

  OCL_MSG("Initializing device\n");
  char cl_platform_vendor[1001];
  // Get all platforms and then select Xilinx platform
  cl_platform_id platforms[16]; // platform id
  cl_uint platform_count;
  cl_uint platform_found = 0;
  cl_int err;
  err = clGetPlatformIDs(16, platforms, &platform_count);
  if (err != CL_SUCCESS) {
    OCL_ERR("Failed to find an OpenCL platform!\n");
    exit(EXIT_FAILURE);
  }
  OCL_MSG("Found %d platforms\n", platform_count);
  // Find Xilinx Plaftorm
  for (cl_uint iplat = 0; iplat < platform_count; iplat++) {
    err = clGetPlatformInfo(platforms[iplat], CL_PLATFORM_VENDOR, 1000,
                            (void *)cl_platform_vendor, NULL);
    if (err != CL_SUCCESS) {
      OCL_ERR("clGetPlatformInfo(CL_PLATFORM_VENDOR) failed!\n");

      exit(EXIT_FAILURE);
    }
    if (strcmp(cl_platform_vendor, "Xilinx") == 0) {
      OCL_MSG("Selected platform %d from %s\n", iplat, cl_platform_vendor);
      world.platform_id = platforms[iplat];
      platform_found = 1;
    }
  }
  if (!platform_found) {
    OCL_MSG("Platform Xilinx not found. Exit.\n");
    exit(EXIT_FAILURE);
  }
  cl_uint num_devices;
  cl_uint device_found = 0;
  cl_device_id devices[16]; // compute device id
  char cl_device_name[1001];
  err = clGetDeviceIDs(world.platform_id, CL_DEVICE_TYPE_ACCELERATOR, 16,
                       devices, &num_devices);
  OCL_MSG("Found %d devices\n", num_devices);
  if (err != CL_SUCCESS) {
    OCL_ERR("Failed to create a device group!\n");
    exit(EXIT_FAILURE);
  }
  // iterate all devices to select the target device.
  for (cl_uint i = 0; i < num_devices; i++) {
    err = clGetDeviceInfo(devices[i], CL_DEVICE_NAME, 1024, cl_device_name, 0);
    if (err != CL_SUCCESS) {
      OCL_ERR("Failed to get device name for device %d!\n", i);
      exit(EXIT_FAILURE);
    }
    OCL_MSG("CL_DEVICE_NAME %s\n", cl_device_name);
    if (strcmp(cl_device_name, target_device_name) == 0) {
      world.device_id = devices[i];
      device_found = 1;
      OCL_MSG("Selected %s as the target device\n", cl_device_name);
    }
  }

  if (!device_found) {
    OCL_ERR("Target device %s not found. Exit.\n", target_device_name);
    exit(EXIT_FAILURE);
  }

  // Create a compute context
  //
  world.context = clCreateContext(0, 1, &world.device_id, NULL, NULL, &err);
  if (!world.context) {
    OCL_ERR("Error: Failed to create a compute context!\n");
    exit(EXIT_FAILURE);
  }

  // Create a command commands
  world.command_queue =
      clCreateCommandQueue(world.context, world.device_id,
                           CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE, &err);
  if (!world.command_queue) {
    OCL_ERR("Failed to create a command commands!\n");
    OCL_ERR("code %i\n", err);
    exit(EXIT_FAILURE);
  }

  cl_int status;

  // Create Program Objects
  // Load binary from disk
  unsigned char *kernelbinary;
  char xclbin[1024];
  // "./xclbin/SW_kernel.hw.xilinx_kcu1500_dynamic_5_0.xclbin";
  if (hw_emu)
    sprintf(xclbin, "%s/%s.hw_emu.%s.xclbin", dir, kernel_name,
            target_device_name);
  else
    sprintf(xclbin, "%s/%s.hw.%s.xclbin", dir, kernel_name, target_device_name);
  // xclbin
  //------------------------------------------------------------------------------
  OCL_MSG("INFO: loading xclbin %s\n", xclbin);
  cl_int n_i0 = load_file_to_memory(xclbin, (char **)&kernelbinary);

  if (n_i0 < 0) {
    OCL_ERR("failed to load kernel from xclbin: %s\n", xclbin);

    exit(EXIT_FAILURE);
  }
  size_t n0 = n_i0;

  // Create the compute program
  program = clCreateProgramWithBinary(world.context, 1, &world.device_id, &n0,
                                      (const unsigned char **)&kernelbinary,
                                      &status, &err);
  free(kernelbinary);

  if ((!program) || (err != CL_SUCCESS)) {
    OCL_ERR("Failed to create compute program from binary %d!\n", err);

    exit(EXIT_FAILURE);
  }

  // Build the program execute
  err = clBuildProgram(program, 0, NULL, NULL, NULL, NULL);

  if (err != CL_SUCCESS) {
    size_t len;
    char buffer[2048];
    OCL_ERR("Failed to build program executable!\n");
    clGetProgramBuildInfo(program, world.device_id, CL_PROGRAM_BUILD_LOG,
                          sizeof(buffer), buffer, &len);
    OCL_ERR("%s\n", buffer);
    OCL_ERR("Test failed\n");
    exit(EXIT_FAILURE);
  }

  kernel = clCreateKernel(program, kernel_name, &err);

  if (!kernel || err != CL_SUCCESS) {
    OCL_ERR("Failed to create compute kernel!\n");
    exit(EXIT_SUCCESS);
  }

  global = 1, local = 1;
  pending_status = false;
  OCL_MSG("Allocating buffers\n");

  initEvents();
}
cl_int DeviceHandle::load_file_to_memory(const char *filename, char **result) {
  cl_int size = 0;
  FILE *f = fopen(filename, "rb");
  if (f == NULL) {
    *result = NULL;
    return -1; // -1 means file opening fail
  }
  fseek(f, 0, SEEK_END);
  size = ftell(f);
  fseek(f, 0, SEEK_SET);
  *result = (char *)malloc(size + 1);
  if (size != fread(*result, sizeof(char), size, f)) {
    free(*result);
    return -2; // -2 means file reading fail
  }
  fclose(f);
  (*result)[size] = 0;
  return size;
}

void DeviceHandle::enqueueExecution() {
  OCL_MSG("Enqueueing NDRange kernel.\n");

  OCL_CHECK(clEnqueueNDRangeKernel(world.command_queue, kernel, 1, nullptr,
                                   &global, &local, num_inputs, write_events,
                                   &kernel_event));
  on_completion(kernel_event, &kernel_events_info);
}

void DeviceHandle::run() {
  OCL_MSG("Creating CL buffers\n");
  // fillBuffers();

  OCL_MSG("Migrating to Device\n");
  enqueueWriteBuffer();

  OCL_MSG("Setting kernel arguments\n");
  setArgs();

  OCL_MSG("Starting execution\n");
  enqueueExecution();

  OCL_MSG("Migrating to host\n");
  enqueueReadBuffer();
  pending_status = true;
}

void DeviceHandle::terminate() {
  clFlush(world.command_queue);
  clFinish(world.command_queue);
  OCL_CHECK(clReleaseKernel(kernel));
  OCL_CHECK(clReleaseProgram(program));
  releaseMemObjects();
  OCL_CHECK(clReleaseContext(world.context));
  
}

void DeviceHandle::waitForDevice() {
  clWaitForEvents(num_inputs + 2 * num_outputs, read_events);
  releaseWriteEvents();
  releaseReadEvents();
  releaseKernelEvent();
  pending_status = false;
}

void DeviceHandle::initEvents() {

  write_events_info = (eventInfo *)malloc(num_inputs * sizeof(eventInfo));
  read_events_info =
      (eventInfo *)malloc((num_inputs + 2 * num_outputs) * sizeof(eventInfo));

  for (int i = 0; i < num_inputs; i++) {
    write_events_info[i].counter = 0;
    sprintf(write_events_info[i].msg, "write event %d", i);
  }
  for (int i = 0; i < num_inputs + 2 * num_outputs; i++) {
    read_events_info[i].counter = 0;
    if (i < num_inputs) {
      sprintf(read_events_info[i].msg, "write size event %d", i);
    } else if (i < num_inputs + num_outputs) {
      sprintf(read_events_info[i].msg, "read size event %d", i - num_inputs);
    } else {
      sprintf(read_events_info[i].msg, "read event %d",
              i - num_inputs - num_outputs);
    }
  }
  kernel_events_info.counter = 0;
  sprintf(kernel_events_info.msg, "kernel event");
}

void DeviceHandle::setRequestSize(uint32_t *req_sz) {
  for (int i = 0; i < num_inputs; i++) {
    request_size[i] = req_sz[i];
  }
}

void DeviceHandle::releaseReadEvents() {
  OCL_MSG("Releasing read events\n");
  for (int i = 0; i < num_inputs + 2 * num_outputs; i ++) {
    OCL_CHECK(clReleaseEvent(read_events[i]));
  }
  
}

void DeviceHandle::releaseWriteEvents() {
  OCL_MSG("Releasing write events\n");
  for (int i = 0; i < num_inputs; i++) {
    OCL_CHECK(clReleaseEvent(write_events[i]));
  }
}

void DeviceHandle::releaseKernelEvent() {
  OCL_MSG("Releasing kernel event\n");
  OCL_CHECK(clReleaseEvent(kernel_event));
}