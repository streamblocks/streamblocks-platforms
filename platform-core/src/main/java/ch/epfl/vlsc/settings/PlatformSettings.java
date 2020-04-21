package ch.epfl.vlsc.settings;

import ch.epfl.vlsc.compiler.PartitionedCompilationTask.PartitionKind;
import se.lth.cs.tycho.settings.Configuration;
import se.lth.cs.tycho.settings.EnumSetting;
import se.lth.cs.tycho.settings.OnOffSetting;


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
    static public EnumSetting<PartitionKind> DefaultPartition = new EnumSetting<PartitionKind>(PartitionKind.class) {
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

}
