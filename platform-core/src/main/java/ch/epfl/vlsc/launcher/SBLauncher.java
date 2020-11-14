package ch.epfl.vlsc.launcher;

import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.compiler.Loader;
import se.lth.cs.tycho.ir.QID;
import se.lth.cs.tycho.platform.Platform;
import se.lth.cs.tycho.settings.Configuration;
import se.lth.cs.tycho.settings.Setting;
import se.lth.cs.tycho.settings.SettingsManager;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SBLauncher {
    private String toolName = "sbc";
    private static final String toolFullName = "The StreamBlocks Tycho Compiler";
    private static final String toolVersion = "0.0.1-SNAPSHOT";

    private final Platform platform;

    public SBLauncher(Platform platform, String toolName){
        this.platform = platform;
        this.toolName = toolName;
    }

    private Optional<String> platformName(String... args) {
        if (args.length >= 2 && !args[0].startsWith("-")) {
            return Optional.of(args[0]);
        } else {
            return Optional.empty();
        }
    }

    public void run(String... args) {
        SettingsManager initialSettings = SettingsManager.initialSettingManager();
        SettingsManager settingsManager = new SettingsManager.Builder()
                .addAll(initialSettings.getAllSettings())
                .addAll(platform.settingsManager()).build();
        Configuration.Builder builder = Configuration.builder(settingsManager);
        List<String> promotedSettings = promotedSettings();
        QID qid = null;
        int i = 0;
        try {
            while (i < args.length) {
                switch (args[i]) {
                    case "--help": {
                        printHelp(promotedSettings, settingsManager);
                        System.exit(0);
                    }
                    case "--version": {
                        printVersion();
                        System.exit(0);
                    }
                    case "--print-phases": { // hidden option
                        printPhases(platform);
                        System.exit(0);
                    }
                    case "--platforms": {
                        printPlatforms();
                        System.exit(0);
                    }
                    case "--settings": {
                        printSettings(settingsManager);
                        System.exit(0);
                    }
                    case "--set": {
                        if (i + 2 >= args.length) {
                            printMissingArguments("--set");
                            System.exit(1);
                        }
                        String keyVal = args[i + 1];
                        int eqIndex = keyVal.indexOf('=');
                        if (eqIndex < 0) {
                            printWrongSettingsFormat(keyVal);
                            System.exit(1);
                        }
                        String key = keyVal.substring(0, eqIndex);
                        String val = keyVal.substring(eqIndex + 1);
                        builder.set(key, val);
                        i += 2;
                        break;
                    }
                    default: {
                        if (args[i].startsWith("--")) {
                            if (promotedSettings.contains(args[i].substring(2))) {
                                if (i + 2 >= args.length) {
                                    printMissingArguments(args[i]);
                                    System.exit(1);
                                }
                                builder.set(args[i].substring(2), args[i + 1]);
                                i += 2;
                            } else {
                                printUnknownArgument(args[i]);
                                System.exit(1);
                            }
                        } else if (i == args.length - 1) {
                            qid = QID.parse(args[i]);
                            i += 1;
                        } else {
                            printUnknownArgument(args[i]);
                            System.exit(1);
                        }
                    }
                }
            }
        } catch (Configuration.Builder.UnknownKeyException e) {
            System.out.println("Unknown setting \"" + e.getKey() + "\"");
            System.exit(1);
        } catch (Configuration.Builder.ReadException e) {
            System.out.println("Could not parse value \"" + e.getValue() + "\" for setting \"" + e.getKey() + "\"");
            System.exit(1);
        }

        if (qid == null) {
            printMissingEntity();
            System.exit(1);
        }


        Configuration config = builder.build();
        Compiler compiler = new Compiler(platform, config);
        if (!compiler.compile(qid)) {
            System.exit(1);
        }
    }

    private void printPhases(Platform platform) {
        platform.phases().forEach(phase -> {
            System.out.println(phase.getName());
            System.out.println(phase.getDescription());
            System.out.println();
        });
    }

    private void printPlatforms() {
        System.out.println("Available platforms:");
        System.out.println();
        for (Platform platform : Compiler.getPlatforms()) {
            System.out.println(platform.name());
            System.out.println("\t" + platform.description());
            System.out.println();
        }
    }

    private void printSettings(SettingsManager settingsManager) {
        System.out.println("These are the settings available through --set <key> <value>");
        System.out.println();
        for (Setting<?> setting : settingsManager.getAllSettings()) {
            System.out.println(setting.getKey() + " <" + setting.getType() + ">");
            System.out.println("\t" + setting.getDescription());
        }
    }

    private void printWrongSettingsFormat(String keyVal) {
        System.out.println("Illegal setting \"" + keyVal + "\". Expected key=value.");
        printUsage();
    }

    private void printMissingArguments(String option) {
        System.out.println("Missing argument to " + option);
        printUsage();
    }

    private void printUnknownArgument(String arg) {
        System.out.println("Unknown argument \"" + arg + "\"");
        printUsage();
    }

    private void printHelp(List<String> promotedSettings, SettingsManager settingsManager) {
        printVersion();
        printUsage();
        System.out.println();
        System.out.println("Available options:");
        System.out.println("--help");
        System.out.println("\tPrints this help message and exits.");
        System.out.println("--version");
        System.out.println("\tPrints the version and exits.");
        System.out.println("--platforms");
        System.out.println("\tPrints the available target platforms and exits.");
        System.out.println("--set <key>=<value>");
        System.out.println("\tSets the compiler setting <key> to <value>.");
        System.out.println("--settings");
        System.out.println("\tPrints all available settings and exits.");
        for (String key : promotedSettings) {
            Setting<?> setting = settingsManager.get(key);
            System.out.println("--" + key + " <" + setting.getType() + ">");
            System.out.println("\t" + setting.getDescription());
        }
        System.out.println();
        System.out.println("Examples:");
        System.out.println("tychoc --source-path src" + File.pathSeparator + "lib --target-path target com.example.Example");
        System.out.println("tychoc --set some-option=a-value com.example.Example");
    }

    private List<String> promotedSettings() {
        return Stream.of(
                Compiler.sourcePaths,
                Compiler.orccSourcePaths,
                Compiler.xdfSourcePaths,
                Compiler.xcfPath,
                Compiler.targetPath,
                Loader.followLinks
        ).map(Setting::getKey).collect(Collectors.toList());
    }

    private void printVersion() {
        System.out.println(toolFullName + ", version " + toolVersion);
    }

    private void printUsage() {
        System.out.println("Usage: " + toolName + " [platform] [options] [entity]");
        System.out.println("For more information: " + toolName + " --help");
    }

    private void printMissingEntity() {
        System.out.println("No entity was specified.");
        printUsage();
        System.out.println("For a description of available options: " + toolName + " --help");
    }
}
