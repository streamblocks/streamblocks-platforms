package ch.epfl.vlsc.platformutils;

import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.compiler.Context;

import java.io.File;
import java.nio.file.Path;

/**
 * PathUtils class for platforms
 *
 * @author Endri Bezati
 */
public class PathUtils {
    /**
     * Create a directory (recursively)
     *
     * @param parent
     * @param name
     * @return
     */
    public static Path createDirectory(Path parent, String name) {
        Path path = null;
        try {
            File directory = new File(parent.toFile(), name);
            if (!directory.exists()) {
                if (directory.mkdirs()) {
                    path = directory.toPath();
                }
            } else if (directory.isDirectory()) {
                path = directory.toPath();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return path;
    }

    /**
     * Get the target output directory
     *
     * @param context
     * @return
     */
    public static Path getTarget(Context context) {
        return context.getConfiguration().get(Compiler.targetPath);
    }

    /**
     * Get the code-gen target output directory
     *
     * @param context
     * @return
     */
    public static Path getTargetCodeGen(Context context) {
        File directory = new File(getTarget(context).toFile(), "code-gen");
        return directory.toPath();
    }

    /**
     * Get the code-gen source target output directory
     *
     * @param context
     * @return
     */
    public static Path getTargetCodeGenSource(Context context) {
        File directory = new File(getTargetCodeGen(context).toFile(), "src");
        return directory.toPath();
    }

    /**
     * Get the code-gen headers target output directory
     *
     * @param context
     * @return
     */
    public static Path getTargetCodeGenInclude(Context context) {
        File directory = new File(getTargetCodeGen(context).toFile(), "include");
        return directory.toPath();
    }

    /**
     * Get the library target output directory
     *
     * @param context
     * @return
     */
    public static Path getTargetLib(Context context) {
        File directory = new File(getTarget(context).toFile(), "lib");
        return directory.toPath();
    }

    /**
     * Get the library source target output directory
     *
     * @param context
     * @return
     */
    public static Path getTargetLibSource(Context context) {
        File directory = new File(getTargetLib(context).toFile(), "src");
        return directory.toPath();
    }

    /**
     * Get the library headers target output directory
     *
     * @param context
     * @return
     */
    public static Path getTargetLibInclude(Context context) {
        File directory = new File(getTargetLib(context).toFile(), "include");
        return directory.toPath();
    }

    public static Path getAuxiliary(Context context){
        File directory = new File(getTargetCodeGen(context).toFile(), "auxiliary");
        return directory.toPath();
    }

}
