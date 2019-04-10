StreamBlocks Platforms Repository
=================================

Welcome to the StreamBlocks Platforms repository. This repository contains the code generators for the StreamBlocks dataflow compiler.

This README file is organized as follows:
1. Getting started
2. How to download this repository
3. Available platforms
4. Dependencies
5. Installation
6. Support

### 1. Getting started

The StreamBlocks dataflow compiler offers code-generation for multicore generic platforms and FPGAs through High-level synthesis. This repository contains the backends of the StreamBlocks-tycho dataflow compiler. Currently this repository is under heavy development.

To use the StreamBlocks Platforms first you need to compile and install StreamBlocks-Tycho compiler [streamblocks-tycho](https://github.com/streamblocks/streamblocks-tycho/blob/master/README.md).

### 2. How to download this repository

To get a local copy of the StreamBlocks examples repository, clone this repository to the local system with the following commmand:
```
git clone https://github.com/streamblocks/streamblocks-platforms streamblocks-platforms
```

### 3. Available platforms

Platform                   | Description           | 
---------------------------|-----------------------|
[platform-multicore/][]    | Code generation for multicore architectures supporting Posix threads  <br> |
[platform-vivadohls/][]    | Code generation for Xilinx FPGAs by using Vivado HLS <br>   |  



### 4. Dependencies

* StreamBlocks platforms are written with Java 8, you will need a compatible Java SE Development Kit 8 (or later), Apache Maven and Git.


* The generated C multithreaded source code of StreamBlocks has the following dependencies: CMake, libxml2 and (optionaly) libsdl2.


* The generated C++ for Vivado HLS source code of StreamBlocks, needs the [Xilinx Vivado Design Suite](https://www.xilinx.com/products/design-tools/vivado.html).


### 5. Installation

```
git clone https://github.com/streamblocks/streamblocks-platforms streamblocks-platforms
cd streamblocks-platforms
mvn install
```

### 6. Support

If you have an issue with one of the StreamBlocks platforms please create a new issue in this repository.


[.]:.
[platform-multicore/]:platform-multicore/
[platform-vivadohls/]:platform-vivadohls/
