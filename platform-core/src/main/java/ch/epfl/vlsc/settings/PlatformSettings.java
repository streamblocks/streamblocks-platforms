package ch.epfl.vlsc.settings;

import se.lth.cs.tycho.settings.Configuration;
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

}
