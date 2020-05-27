package ch.epfl.vlsc.settings;

import ch.epfl.vlsc.compiler.PartitionedCompilationTask.PartitionKind;
import se.lth.cs.tycho.settings.Configuration;
import se.lth.cs.tycho.settings.IntegerSetting;
import se.lth.cs.tycho.settings.OnOffSetting;
import se.lth.cs.tycho.settings.EnumSetting;


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

    static public IntegerSetting defaultBufferDepth = new IntegerSetting() {
        @Override
        public String getKey() {
            return "buffer-depth";
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


}
