How to generate a TURNUS trace from StreamBlocks
======================

- Use the ART multicore platform to generate the source code of a CAL design
- Generate etracez file (preliminary trace that represent what an action does)

```
cd <design>
cd build
cmake -DTURNUS_TRACE=ON -DCMAKE_BUILD_TYPE=RELEASE ..
make
cd ../bin

./<deisgn name> --turnus-trace
```

- Copy the path of generate trace_0.etracez
- Launch the platform-turnus for generating the final TURNUS trace

``
---set etracez-path=<Path>/trace_0.etracez --source-path <Same source path as the design>  --target-path <a target path> <name of the Top>
``

- The turnus trace is now stored on the given target path

ART Software Weights for TURNUS
========

- Generate Software weights for TURNUS

```
cd <design>
cd build
cmake -DTARCE=ON -DTURNUS_TRACE=OFF -DCMAKE_BUILD_TYPE=RELEASE ..
make
cd ../bin

./<deisgn name> --trace
```

- Two files will appear after the execution of the design on the bin folder "net_trace.xml" and "trace_0.xml"

- Use the ActionWeightReader Java application on analysis-core module for generating the TURNUS weight ".exdf" file

``
 <generated path>/bin/net_trace.xml <generate path>/bin/trace_0.xml
``