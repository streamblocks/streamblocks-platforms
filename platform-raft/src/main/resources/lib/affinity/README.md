# Affinity

## Build status
![CI](https://github.com/RaftLib/affinity/workflows/CI/badge.svg)

## Notes
* Pulled out raftlib core affinity functions to 
try to make more modular. This piece looks like 
it should be tested in isolation. 
* To build
```bash
cmake <path to affinity> -Wno-dev -CMAKE_BUILD_TYPE=[Release/Debug] -GNinja
ninja
ninja test
```
* You can inject your own namespace if you'd like by
setting the following options, also include the moduleflags.cmake
file at the global level so that CFLAGS prop through all files:
```bash
-DAFFINITY_CUSTOM_NAMESPACE=YES
-DAFFINITY_NAMESPACE=<Name>
```
* test cases only test functionality at the moment, 
need to add test cases for perf as well and more 
corner cases. 

## Usage
Include the header file
```cpp
#include <affinity>
```
then link 
```bash
-laffinity
```
and use this library inside your c++ code base
```cpp
affinity::set( core );
```
and that's pretty much it. If you've chosen to 
inject your own namespace when building the
library, you'll need to modify the invocation
above like this:
```cpp
your_namespace::affinity::set( core );
```

## Platforms
* Currently  only sets affinity for Linux, 
will assign others to do Win/Mac at some point.
* CI currently only checks Linux but will add
others as we enable setting affinity on those
platforms.
