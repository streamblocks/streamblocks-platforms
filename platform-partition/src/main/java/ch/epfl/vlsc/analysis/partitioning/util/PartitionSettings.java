package ch.epfl.vlsc.analysis.partitioning.util;
import se.lth.cs.tycho.settings.Configuration;
import se.lth.cs.tycho.settings.IntegerSetting;
import se.lth.cs.tycho.settings.OnOffSetting;
import se.lth.cs.tycho.settings.PathSetting;

import java.nio.file.Path;

public class PartitionSettings {

    public static PathSetting profilePath = new PathSetting() {
        @Override
        public String getKey() {
            return "profile-path";
        }

        @Override
        public String getDescription() {
            return "path to the profile xml file";
        }

        @Override
        public Path defaultValue(Configuration configuration) {
            return null;
        }
    };

    public static PathSetting configPath = new PathSetting() {
        @Override
        public String getKey() {
            return "config-path";
        }

        @Override
        public String getDescription() {
            return "path to generate a config xml file";
        }

        @Override
        public Path defaultValue(Configuration configuration) {
            return null;
        }
    };

    public static IntegerSetting cpuCoreCount = new IntegerSetting() {
        @Override
        public String getKey() {
            return "num-cores";
        }

        @Override
        public String getDescription() {
            return "Number of available CPU cores";
        }

        @Override
        public Integer defaultValue(Configuration configuration) {
            return 1;
        }
    };

}