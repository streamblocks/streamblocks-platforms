#include "device-handle.h"

void CL_CALLBACK completion_handler(cl_event event, cl_int cmd_status,
                                    void *info) {

  cl_command_type command;
  OCL_CHECK(clGetEventInfo(event, CL_EVENT_COMMAND_TYPE,
                           sizeof(cl_command_type), &command, NULL));
  const char *command_str;
  EventInfo *event_info = (EventInfo *)info;

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

void DeviceHandleConstructor(DeviceHandle_t *dev, char *kernel_name,
                  char *target_device_name, char *dir, bool hw_emu) {
  // cl_int err;
  dev->buffer_size = BUFFER_SIZE;
  dev->num_inputs = NUM_INPUTS;
  dev->num_outputs = NUM_OUTPUTS;
  dev->mem_alignment = MEM_ALIGNMENT;

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
      dev->world.platform_id = platforms[iplat];
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
  err = clGetDeviceIDs(dev->world.platform_id, CL_DEVICE_TYPE_ACCELERATOR, 16,
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
      dev->world.device_id = devices[i];
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
  dev->world.context =
      clCreateContext(0, 1, &dev->world.device_id, NULL, NULL, &err);
  if (!dev->world.context) {
    OCL_ERR("Error: Failed to create a compute context!\n");
    exit(EXIT_FAILURE);
  }

  // Create a command commands
  dev->world.command_queue =
      clCreateCommandQueue(dev->world.context, dev->world.device_id,
                           CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE, &err);
  if (!dev->world.command_queue) {
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
  dev->program = clCreateProgramWithBinary(
      dev->world.context, 1, &dev->world.device_id, &n0,
      (const unsigned char **)&kernelbinary, &status, &err);
  free(kernelbinary);

  if ((!dev->program) || (err != CL_SUCCESS)) {
    OCL_ERR("Failed to create compute program from binary %d!\n", err);

    exit(EXIT_FAILURE);
  }

  // Build the program execute
  err = clBuildProgram(dev->program, 0, NULL, NULL, NULL, NULL);

  if (err != CL_SUCCESS) {
    size_t len;
    char buffer[2048];
    OCL_ERR("Failed to build program executable!\n");
    clGetProgramBuildInfo(dev->program, dev->world.device_id,
                          CL_PROGRAM_BUILD_LOG, sizeof(buffer), buffer, &len);
    OCL_ERR("%s\n", buffer);
    OCL_ERR("Test failed\n");
    exit(EXIT_FAILURE);
  }

  dev->kernel = clCreateKernel(dev->program, kernel_name, &err);

  if (!dev->kernel || err != CL_SUCCESS) {
    OCL_ERR("Failed to create compute kernel!\n");
    exit(EXIT_SUCCESS);
  }

  dev->global = 1;
  dev->local = 1;
  dev->pending_status = false;
  OCL_MSG("Allocating buffers\n");

  DeviceHandleInitEvents(dev);
}
cl_int load_file_to_memory(const char *filename, char **result) {
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

void DeviceHandleEnqueueExecution(DeviceHandle_t* dev) {
  OCL_MSG("Enqueueing NDRange kernel.\n");

  OCL_CHECK(clEnqueueNDRangeKernel(
      dev->world.command_queue, dev->kernel, 1, NULL, &dev->global, &dev->local,
      dev->num_inputs, dev->write_buffer_event, &dev->kernel_event));
  on_completion(dev->kernel_event, &dev->kernel_event_info);
}

void DeviceHandleRun(DeviceHandle_t *dev) {


  OCL_MSG("Migrating to Device\n");
  DeviceHandleEnqueueWriteBuffers(dev);

  OCL_MSG("Setting kernel arguments\n");
  DeviceHandleSetArgs(dev);

  OCL_MSG("Starting execution\n");
  DeviceHandleEnqueueExecution(dev);

  OCL_MSG("Migrating to host\n");
  DeviceHandleEnqueueReadBuffers(dev);

}

void DeviceHandleTerminate(DeviceHandle_t *dev) {
  clFlush(dev->world.command_queue);
  clFinish(dev->world.command_queue);
  OCL_CHECK(clReleaseKernel(dev->kernel));
  OCL_CHECK(clReleaseProgram(dev->program));
  DeviceHandleReleaseMemObjects(dev);
  OCL_CHECK(clReleaseContext(dev->world.context));
  DeviceHandleFreeEvents(dev);
}

void DeviceHandleWaitForDevice(DeviceHandle_t *dev) {
  clWaitForEvents(dev->num_outputs, dev->read_buffer_event);
  DeviceHandleReleaseWriteEvents(dev);
  DeviceHandleReleaseReadEvents(dev);
  DeviceHandleReleaseKernelEvent(dev);

}

void DeviceHandleInitEvents(DeviceHandle_t *dev) {

  dev->write_buffer_event_info =
      (EventInfo *)malloc(dev->num_inputs * sizeof(EventInfo));
  dev->read_size_event_info =
      (EventInfo *)malloc((dev->num_inputs + dev->num_outputs) * sizeof(EventInfo));
  dev->read_buffer_event_info =
      (EventInfo *)malloc(dev->num_outputs * sizeof(EventInfo));

  for (int i = 0; i < dev->num_inputs; i++) {
    dev->write_buffer_event_info[i].counter = 0;
    sprintf(dev->write_buffer_event_info[i].msg, "write buffer event %d", i);
  }
  for (int i = 0; i < dev->num_inputs + dev->num_outputs; i++) {
    dev->read_size_event_info[i].counter = 0;
    sprintf(dev->read_size_event_info[i].msg, "read size event %d", i);
  }
  for (int i = 0 ; i < dev->num_outputs; i++) {
    dev->read_buffer_event_info[i].counter = 0;
    sprintf(dev->read_buffer_event_info[i].msg, "read buffer event %d", i);
  }
  dev->kernel_event_info.counter = 0;
  sprintf(dev->kernel_event_info.msg, "kernel event");
}


void DeviceHandleReleaseReadEvents(DeviceHandle_t *dev) {
  OCL_MSG("Releasing read size events...\n");
  for (int i = 0; i < dev->num_inputs + dev->num_outputs; i++) {
    OCL_CHECK(clReleaseEvent(dev->read_size_event[i]));
  }

  OCL_MSG("Releasing read buffer events...\n");
  for (int i = 0; i < dev->num_outputs; i++) {
    OCL_CHECK(clReleaseEvent(dev->read_buffer_event[i]));
  }

  OCL_MSG("All read events released.\n");
}

void DeviceHandleReleaseWriteEvents(DeviceHandle_t *dev) {
  OCL_MSG("Releasing write buffer events...\n");
  for (int i = 0; i < dev->num_inputs; i++) {
    OCL_CHECK(clReleaseEvent(dev->write_buffer_event[i]));
  }
  OCL_MSG("All write buffer events released.\n");
}

void DeviceHandleReleaseKernelEvent(DeviceHandle_t *dev) {
  OCL_MSG("Releasing kernel event\n");
  OCL_CHECK(clReleaseEvent(dev->kernel_event));
}

void DeviceHandleFreeEvents(DeviceHandle_t *dev) {
   OCL_MSG("Freeing write buffers event info...\n");
   free(dev->write_buffer_event_info);
   OCL_MSG("Freeing read size event info...\n");
   free(dev->read_size_event_info);
   OCL_MSG("Freeing read buffer event info...\n");
   free(dev->read_buffer_event_info);
   OCL_MSG("All event info deallocated.\n");
}

void DeviceHandleSetKernelCommand(DeviceHandle_t *dev, uint64_t cmd) {
  dev->kernel_command = cmd;
  dev->command_is_set = 1;
}