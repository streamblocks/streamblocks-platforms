package se.lth.cs.mlir;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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

    private Path calFilePath;
    private Path correctMlirFilePath;
    private Path generatedMlirFilePath;

    // The constructor per test which is automatically called by the test framework and passed a single element from
    // the getTestPaths() parameters function.
    public CalToMlirTest(Path calFilePath) {
        int pointLocation = calFilePath.toString().lastIndexOf(".");
        String pathWithoutExtension = calFilePath.toString().substring(0, pointLocation);

        this.calFilePath = calFilePath;
        this.correctMlirFilePath = Paths.get(pathWithoutExtension + ".mlir");
        this.generatedMlirFilePath = Paths.get(pathWithoutExtension + ".generated");
        this.testOutput = new TestOutput(calFilePath);
    }

    // The parameters to be passed to the tests, each .cal file in testdata becomes a separate test on one of the
    // values in the returned list.
    @Parameterized.Parameters(name = "Test index: {index}: Test file: {0}")
    public static List<Path> getTestPaths() throws IOException {
        List<Path> calFiles = new ArrayList<>();

        Path currentPath = Paths.get("./testdata");
        // Get all files in testdata
        List<Path> subfolders = Files.walk(currentPath, 3)
                .collect(Collectors.toList());

        // If the file is a CAL file, add it to the list of files to return
        for (Path path : subfolders) {
            if (path.toString().endsWith(".cal")) {
                calFiles.add(path);
            }
        }
        return calFiles;
    }

    @Test
    public void singleTest() {
        // 1. Check that the input files exist
        assertTrue("File '" + calFilePath.toString() + "' does not exist.", Files.exists(calFilePath));
        assertTrue("File '" + correctMlirFilePath.toString() + "' does not exist.", Files.exists(correctMlirFilePath));

        // 2. Initialise the compiler


        // 3. Run the compiler and set the backend

        // 4. Compare the generated file with the actual file
    }
}

