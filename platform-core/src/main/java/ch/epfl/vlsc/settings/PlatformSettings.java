package ch.epfl.vlsc.settings;

import ch.epfl.vlsc.compiler.PartitionedCompilationTask.PartitionKind;
import se.lth.cs.tycho.settings.*;


public class PlatformSettings {

   static public OnOffSetting scopeLivenessAnalysis = new OnOffSetting() {
        @Override
        public String getKey() {
            return "scope-liveness-analysis";
        }

        @Override
        public String getDescription() {
            return "Analyzes actor machine scope liveness for initialization.";
        }

        @Override
        public Boolean defaultValue(Configuration configuration) {
            return true;
        }
    };

    // -- Node Setting
    static public OnOffSetting runOnNode = new OnOffSetting() {
        @Override
        public String getKey() {
            return "run-on-node";
        }

        @Override
        public String getDescription() {
            return "Activate code generation for Node runtime.";
        }

        @Override
        public Boolean defaultValue(Configuration configuration) {
            return false;
        }
    };
    // -- OCL Host
    static public OnOffSetting C99Host = new OnOffSetting(){
    
        @Override
        public String getKey() {
            
            return "C99";
        }
    
        @Override
        public String getDescription() {
            
            return "Generate C99 host code for OCL";
        }
    
        @Override
        public Boolean defaultValue(Configuration configuration) {
           
            return false;
        }
    };
    // -- Partition phase setting
    static public OnOffSetting PartitionNetwork =  new OnOffSetting() {
        @Override
        public String getKey() {
            return "partitioning";
        }

        @Override
        public String getDescription() {
            return "Partitions the network into SW and HW, if an instance has no tag it will be placed in SW.";
        }

        @Override
        public Boolean defaultValue(Configuration configuration) {
            return false;
        }
    };

    // -- Default partition setting
    static public EnumSetting<PartitionKind> defaultPartition = new EnumSetting<PartitionKind>(PartitionKind.class) {
        @Override
        public String getKey() {
            return "default-partition";
        }

        @Override
        public String getDescription() {
            return "default partition of instances";
        }

        @Override
        public PartitionKind defaultValue(Configuration configuration) {
            return PartitionKind.SW;
        }
    };


    public enum ControllerKind {
        BC, QJ;

        @Override
        public String toString() {
            String ret;
            switch (this) {
                case BC:
                    return "branching-controller";
                case QJ:
                    return "quick-jump-controller";
                default:
                    return "ERROR";
            }
        }
    };

    static public EnumSetting<ControllerKind> defaultController = new EnumSetting<ControllerKind>(ControllerKind.class) {
        @Override
        public String getKey() {
            return "default-controller";
        }

        @Override
        public String getDescription() {
            return "default controller of instances";
        }

        @Override
        public ControllerKind defaultValue(Configuration configuration) {
            return ControllerKind.QJ;
        }
    };


    static public OnOffSetting arbitraryPrecisionIntegers = new OnOffSetting() {
        @Override
        public String getKey() {
            return "arbitrary-precision-integers";
        }

        @Override
        public String getDescription() {
            return "Enable arbitrary precision integer data types.";
        }

        @Override
        public Boolean defaultValue(Configuration configuration) {
            return false;
        }
    };

    static public OnOffSetting enableTraces = new OnOffSetting() {
        @Override
        public String getKey() {
            return "enable-traces";
        }

        @Override
        public String getDescription() {
            return "Enable fifo traces";
        }

        @Override
        public Boolean defaultValue(Configuration configuration) {
            return false;
        }
    };

    static public OnOffSetting enableActionProfile = new OnOffSetting() {
        @Override
        public String getKey() {
            return "enable-action-profile";
        }

        @Override
        public String getDescription() {
            return "Enable action profile for systemC";
        }

        @Override
        public Boolean defaultValue(Configuration configuration) {
            return false;
        }
    };

    static public IntegerSetting defaultBufferDepth = new IntegerSetting() {
        @Override
        public String getKey() {
            return "buffer-depth";
        }

        @Override
        public String getDescription() {
            return "The depth of the PLink fifo queues.";
        }

        @Override
        public Integer defaultValue(Configuration configuration) {
            return 4096;
        }
    };

    static public IntegerSetting defaultQueueDepth = new IntegerSetting() {
        @Override
        public String getKey() {
            return "queue-depth";
        }

        @Override
        public String getDescription() {
            return "The depth of the fifo queues.";
        }

        @Override
        public Integer defaultValue(Configuration configuration) {
            return 4096;
        }
    };

    static public OnOffSetting enableSystemC =  new OnOffSetting() {
        @Override
        public String getKey() {
            return "enable-systemc";
        }

        @Override
        public String getDescription() {
            return "Enables the SystemC network generator";
        }

        @Override
        public Boolean defaultValue(Configuration configuration) {
            return false;
        }
    };

    static public StringSetting maxBRAMSize = new StringSetting() {
        @Override
        public String getKey() {
            return "max-bram";
        }

        @Override
        public String getDescription() {
            return "maximum on chip memory size for an actor, example value 1MiB, 1MB, 2.2KiB, 1024B, 1024, 1.2GB";
        }

        @Override
        public String defaultValue(Configuration configuration) {
            return"1MiB"; // 1MiB
        }

    };


}
