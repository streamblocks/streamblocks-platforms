package se.lth.cs.mlir;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.nio.file.Path;

/**
 * A TestWatcher that prints nice messages when a test completes or fails.
 */
public class TestOutput extends TestWatcher {

    private Path fileName;

    public TestOutput(Path fileName){
        this.fileName = fileName;
    }

    @Override
    protected void failed(Throwable e, Description description) {
        System.out.println("[FAILED]: Test '"+fileName.getFileName()+"': " + e.toString());
    }

    @Override
    protected void succeeded(Description description){
        System.out.println("[SUCCEEDED]: Test '"+fileName.getFileName()+"'");
    }
}
