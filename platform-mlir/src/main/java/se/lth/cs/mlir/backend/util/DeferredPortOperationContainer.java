package se.lth.cs.mlir.backend.util;

import se.lth.cs.tycho.type.ListType;

import java.util.ArrayList;
import java.util.List;

/**
 * Class for storing all information for deferred MLIR instructions.
 * <p>
 * In the DFG dialect, within a dfg.process/dfg.loop region all dfg.pull operations must be before any other operation
 * while all dfg.push operations must be after all other operations. When using the repeat keyword, extra MLIR
 * instructions are required to pull a number of tokens off of a port and assign them to a memref and for dfg.push and
 * vice versa for dfg.push. These instructions have to be deferred until after all dfg.push or performed before all
 * dfg.pushes. This class stores all relevant information per port required for generating the deferred instructions.
 */
public class DeferredPortOperationContainer {

    List<SinglePortBuilder> individualPorts;

    public DeferredPortOperationContainer() {
        individualPorts = new ArrayList<>();
    }

    public void addPort(String listString, List<String> tempSSAs, ListType listType) {
        individualPorts.add(new SinglePortBuilder(listString, tempSSAs, listType));
    }

    public boolean hasRemainingPorts() {
        return individualPorts.size() != 0;
    }

    public List<SinglePortBuilder> getPorts() {
        return individualPorts;
    }

    public class SinglePortBuilder {
        private String listString;
        private List<String> tempSSAs;
        private ListType listType;

        public SinglePortBuilder(String listString, List<String> tempSSAs, ListType listType) {
            this.listString = listString;
            this.tempSSAs = tempSSAs;
            this.listType = listType;
        }

        public String getListString() {
            return listString;
        }

        public List<String> getTempSSAs() {
            return tempSSAs;
        }

        public ListType getListType() {
            return listType;
        }
    }
}
