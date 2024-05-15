package se.lth.cs.mlir;

import com.google.gson.Gson;
import se.lth.cs.tycho.ir.QID;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * An object that stores import information about a test.
 *
 * Fields are loaded from a json formatted [file name].test file
 * Other fields are then generated based on the content of the test file
 */
public class TestDescription {

    Path directory; // The directory the cal file is located in
    Path calFile; // The actual cal file
    Path correctMlirFile; // The MLIR file that the generate MLIR file will be compared to
    Path generatedMlirFile; // The location to store the generated MLIR file after compilation
    QID entityName; // The name of the top level entity in the cal file to build
    String description; // A description of the project

    public TestDescription(Path directory, Path calFile, Path correctMlirFile, Path generatedMlirFile, QID entityName, String description){
        this.calFile = calFile;
        this.correctMlirFile = correctMlirFile;
        this.generatedMlirFile = generatedMlirFile;
        this.entityName = entityName;
        this.description = description;
        this.directory = directory;
    }

    public Path getCalFile() {
        return calFile;
    }

    public Path getCorrectMlirFile() {
        return correctMlirFile;
    }

    public QID getEntityName() {
        return entityName;
    }

    public String getDescription() {
        return description;
    }

    public Path getDirectory() {
        return directory;
    }

    public Path getGeneratedMlirFile() {
        return generatedMlirFile;
    }

    static TestDescription fromFile(Path testDescriptionFile) throws FileNotFoundException {
        BufferedReader bufferedReader = new BufferedReader(new FileReader(testDescriptionFile.toString()));

        Gson gson = new Gson();
        Map json = gson.fromJson(bufferedReader, Map.class);

        Path directory = testDescriptionFile.getParent();
        String calPathString = testDescriptionFile.getParent() + "/" + json.get("cal-file").toString();
        Path calPath = Paths.get(calPathString);
        String mlirPathString = testDescriptionFile.getParent() + "/" + json.get("mlir-file").toString();
        Path mlirPath = Paths.get(mlirPathString);
        String generatedMlirPathString = mlirPathString + ".generated";
        Path generatedMlirPath = Paths.get(generatedMlirPathString);
        QID entity = QID.parse(json.get("entity").toString());
        String description = json.get("description").toString();

        return new TestDescription(directory, calPath, mlirPath, generatedMlirPath, entity, description);
    }

    public String toString(){
        return calFile.getFileName().toString();
    }

}
