package se.lth.cs.mlir;

import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import se.lth.cs.mlir.platform.Mlir;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.phase.CalToAmPhase;
import se.lth.cs.tycho.platform.Platform;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.reporting.Reporter;
import se.lth.cs.tycho.settings.Configuration;
import se.lth.cs.tycho.settings.SettingsManager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the CAL to MLIR backend.
 * <p>
 * The tests work as follows:
 * 1. A [file name].cal file and matching [file name].mlir file are located in platform-mlir/testdata
 * 2. The .cal file is run through the platform-mlir backend to produce mlir stored in a [file name].example file
 * 3. [file name].mlir is compared to [file name].example and if there are no differences, the test passes.
 * 4. The above three steps are executed for every .cal file in the platform-mlir/testdata directory.
 * <p>
 * This is a parameterised JUnit test. The singleTest() function is executed once for every element in the paths
 * returned in getTestPaths()
 */
@RunWith(Parameterized.class)
public class CalToMlirTest {

    // This TestOutput class prints out pretty messages on each passing and failing of the unit tests
    @Rule
    public TestOutput testOutput;

    TestDescription testDescription;

    // The constructor per test which is automatically called by the test framework and passed a single element from
    // the getTestPaths() parameters function.
    public CalToMlirTest(TestDescription description) {
        this.testDescription = description;
        this.testOutput = new TestOutput(description.getCalFile());
    }

    // The parameters to be passed to the tests, each .cal file in testdata becomes a separate test on one of the
    // values in the returned list.
    @Parameterized.Parameters(name = "Test index: {index}: Test file: {0}")
    public static List<TestDescription> getTestPaths() throws IOException {

        List<Path> testFiles = Files.walk(Paths.get("testdata"))
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".test"))
                .collect(Collectors.toList());
        List<TestDescription> testParameters = new ArrayList<>();
        for (Path test : testFiles) {
            TestDescription testDescription = TestDescription.fromFile(test);
            testParameters.add(testDescription);
        }
        return testParameters;

    }

    @Test
    public void singleTest() throws Configuration.Builder.UnknownKeyException, IOException {
        // 1. Check that the input files exist
        assertTrue("File '" + testDescription.getCalFile() + "' does not exist.",
                Files.exists(testDescription.getCalFile()));
        assertTrue("File '" + testDescription.getCorrectMlirFile() + "' does not exist.",
                Files.exists(testDescription.getCorrectMlirFile()));

        // 2. Initialise the compiler
        Path tempDirectory = Paths.get(testDescription.getDirectory().toString() + "/temp");
        Platform platform = new Mlir();
        SettingsManager initialSettings = SettingsManager.initialSettingManager();
        SettingsManager settingsManager = new SettingsManager.Builder()
                .addAll(initialSettings.getAllSettings())
                .addAll(platform.settingsManager()).build();
        Configuration config = Configuration.builder(settingsManager)
                .set(Compiler.targetPath, tempDirectory)
                .set(Compiler.sourcePaths, Collections.singletonList(testDescription.getCalFile()))
                .set(Reporter.reportingLevel, Collections.singleton(Diagnostic.Kind.ERROR)) // Prevent info messages
                // from spamming the test output
                .set(CalToAmPhase.bypassAmGeneration, true)
                .build();

        Compiler compiler = new Compiler(platform, config);

        // 3. Run the compiler
        compiler.compile(testDescription.getEntityName());

        // 4. Copy the generate main.mlir file to [name].mlir.expected and then delete the temporary directory
        Path generatedMainTemp = Paths.get(testDescription.getDirectory().toString() + "/temp/code-gen/main.mlir");
        Files.copy(generatedMainTemp, testDescription.getGeneratedMlirFile(), StandardCopyOption.REPLACE_EXISTING);

        Files.walk(tempDirectory)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(x -> x.delete());

        // 5. Compare the generated file with the actual file
        BufferedReader reader1 = new BufferedReader(new FileReader(testDescription.getGeneratedMlirFile().toFile()));
        BufferedReader reader2 = new BufferedReader(new FileReader(testDescription.getCorrectMlirFile().toFile()));

        String failMessage = "Generated MLIR file 'platform-mlir/" + testDescription.getGeneratedMlirFile() + "' does not match " +
                "expected file: 'platform-mlir/" + testDescription.getCorrectMlirFile() + "'";
        assertTrue(failMessage, IOUtils.contentEqualsIgnoreEOL(reader1, reader2));
    }
}

