package ch.epfl.vlsc.platformutils;

import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.compiler.Context;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;

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
     * Get the cmake target output directory
     *
     * @param context
     * @return
     */
    public static Path getTargetCmake(Context context) {
        File directory = new File(getTarget(context).toFile(), "cmake");
        return directory.toPath();
    }

    /**
     * Get the cmake target output directory
     *
     * @param context
     * @return
     */
    public static Path getTargetScripts(Context context) {
        File directory = new File(getTarget(context).toFile(), "scripts");
        return directory.toPath();
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
     * Get the code-gen RTL target output directory
     *
     * @param context
     * @return
     */
    public static Path getTargetCodeGenRtl(Context context) {
        File directory = new File(getTargetCodeGen(context).toFile(), "rtl");
        return directory.toPath();
    }


    /**
     * Get the code-gen RTL target output directory
     *
     * @param context
     * @return
     */
    public static Path getTargetCodeGenRtlTb(Context context) {
        File directory = new File(getTargetCodeGen(context).toFile(), "rtl-tb");
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

    public static Path getAuxiliary(Context context) {
        File directory = new File(getTargetCodeGen(context).toFile(), "auxiliary");
        return directory.toPath();
    }

    public static void copyDirTree(Path fromDir, Path toDir, CopyOption... opts) throws IOException {
        boolean fromDirExistsAndIsDir = fromDir.toFile().isDirectory();
        boolean toIsBelowFrom = toDir.toAbsolutePath().startsWith(fromDir.toAbsolutePath());
        boolean result = fromDirExistsAndIsDir && !toIsBelowFrom;

        if (result) {
            Files.walkFileTree(fromDir, new CopyVisitor(fromDir, toDir, opts));
        } else {
            log("Won't copy from " + fromDir + " to " + toDir);
            log(fromDirExistsAndIsDir, toIsBelowFrom);
        }
    }

    private static class CopyVisitor extends SimpleFileVisitor<Path> {
        CopyVisitor(Path fromDir, Path toDir, CopyOption... opts) {
            this.fromDir = fromDir;
            this.toDir = toDir;
            this.opts = opts;
        }

        /**
         * First create the dir.
         * Create any non-existent parent dir's, if needed.
         * If {@code dir} already exists, then do nothing; no exception is thrown if
         * the dir already exists.
         */
        @Override
        public FileVisitResult preVisitDirectory(
                Path dir, BasicFileAttributes attrs
        ) throws IOException {
            //log("Creating dir: " + dir);
            Files.createDirectories(toDir.resolve(fromDir.relativize(dir)));
            return FileVisitResult.CONTINUE;
        }

        /**
         * Then copy the files into the dir.
         * Note that the {@code opts} only apply to this method.
         */
        @Override
        public FileVisitResult visitFile(
                Path file, BasicFileAttributes attrs
        ) throws IOException {
            //log("Copying file: " + file);
            Files.copy(file, toDir.resolve(fromDir.relativize(file)), opts);
            return FileVisitResult.CONTINUE;
        }

        private Path fromDir;
        private Path toDir;
        private CopyOption[] opts;
    }

    private static void log(Object... things) {
        for (Object thing : things) {
            System.out.println(thing);
        }
    }


}
