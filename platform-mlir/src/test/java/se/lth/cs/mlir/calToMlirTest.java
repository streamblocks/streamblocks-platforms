package se.lth.cs.mlir;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class calToMlirTest {

    @Test
    public void calToMlirCompilation() throws IOException {
        // Step 1, files: Get the directory
            // - get 2 input files
            // - generate output file
            // - compare

        // 1. Get files
        List<Path> tests = getTestPaths();

        // 2. Test file
        int numTests = tests.size();
        System.out.println("Running Tests: ");
        for (int i = 0; i < numTests; i++) {
            Path testFile = tests.get(i);
            System.out.print("\t" + (i+1) + " of " + numTests + ": " + testFile.getFileName() + " - ");
            boolean testResult = singleTest(testFile);
            if(!testResult){
                System.out.println("Failed!");
                fail("Test '" + testFile.getFileName() + "' failed.");
            }
            System.out.println("Passed!");
        }
    }

    private boolean singleTest(Path testFile) {
        return true;
    }

    private List<Path> getTestPaths() throws IOException {
        List<Path> calFiles = new ArrayList<>();

        Path currentPath = Paths.get("./testdata");
        // Get all files in testdata
        List<Path> subfolders = Files.walk(currentPath, 3)
                .collect(Collectors.toList());

        // If the file is a CAL file, add it to the list of files to return
        for (Path path: subfolders){
            if(path.toString().endsWith(".cal")){
                calFiles.add(path);
            }
        }

        return calFiles;
    }
}