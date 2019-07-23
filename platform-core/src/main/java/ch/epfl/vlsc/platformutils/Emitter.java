package ch.epfl.vlsc.platformutils;

import se.lth.cs.tycho.reporting.CompilationException;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class Emitter {
    private int indentation;
    private PrintWriter writer;

    public Emitter() { }

    public void open(Path file) {
        if (writer != null) throw new IllegalStateException("Must close previous file before opening a new.");
        try {
            writer = new PrintWriter(Files.newBufferedWriter(file));
        } catch (IOException e) {
            throw CompilationException.from(e);
        }
        indentation = 0;
    }

    public void close() {
        writer.flush();
        writer.close();
        writer = null;
    }

    public void increaseIndentation() {
        indentation++;
    }

    public void decreaseIndentation() {
        indentation--;
    }

    public void emit(String format, Object... values) {
        if (writer == null) {
            throw new IllegalStateException("No output file is currently open.");
        }
        if (!format.isEmpty()) {
            int indentation = this.indentation;
            while (indentation > 0) {
                writer.print('\t');
                indentation--;
            }
            writer.printf(format, values);
        }
        writer.println();
    }

    public void emitRawLine(CharSequence text) {
        if (writer == null) {
            throw new IllegalStateException("No output file is currently open.");
        }
        writer.println(text);
    }

    public void emitSharpBlockComment(String text){
       emit("# -- --------------------------------------------------------------------------");
       emit("# -- %s", text);
       emit("# -- --------------------------------------------------------------------------");
    }

    public void emitClikeBlockComment(String text){
        emit("// -- --------------------------------------------------------------------------");
        emit("// -- %s", text);
        emit("// -- --------------------------------------------------------------------------");
    }

    public void emitNewLine(){
        emit("");
    }
}
