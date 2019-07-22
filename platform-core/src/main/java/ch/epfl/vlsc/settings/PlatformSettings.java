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

}
