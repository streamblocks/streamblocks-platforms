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
     * Get the code-gen target output directory
     *
     * @param context
     * @return
     */
    public static Path getTargetCodeGenCC(Context context) {
        File directory = new File(getTargetCodeGen(context).toFile(), "cc");
        return directory.toPath();
    }

    /**
     * Get the code-gen target output directory
     *
     * @param context
     * @return
     */
    public static Path getTargetCodeGenHLS(Context context) {
        File directory = new File(getTargetCodeGen(context).toFile(), "hls");
        return directory.toPath();
    }


    /**
     * Get the code-gen target output directory
     *
     * @param context
     * @return
     */
    public static Path getTargetBin(Context context) {
        File directory = new File(getTarget(context).toFile(), "bin");
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
     * Get the code-gen source target output directory
     *
     * @param context
     * @return
     */
    public static Path getTargetCodeGenHost(Context context) {
        File directory = new File(getTargetCodeGen(context).toFile(), "host");
        return directory.toPath();
    }

    /**
     * Get the code-gen source target output directory
     *
     * @param context
     * @return
     */
    public static Path getTargetCodeGenSourceCC(Context context) {
        File directory = new File(getTargetCodeGenCC(context).toFile(), "src");
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
    public static Path getTargetCodeGenTla(Context context) {
        File directory = new File(getTargetCodeGen(context).toFile(), "tla");
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
     * Get the code-gen RTL target output directory
     *
     * @param context
     * @return
     */
    public static Path getTargetCodeGenSrcTb(Context context) {
        File directory = new File(getTargetCodeGen(context).toFile(), "src-tb");
        return directory.toPath();
    }

    /**
     * Get the code-gen RTL target output directory
     *
     * @param context
     * @return
     */
    public static Path getTargetCodeGenWcfg(Context context) {
        File directory = new File(getTargetCodeGen(context).toFile(), "wcfg");
        return directory.toPath();
    }

    /**
     * Get the CMake script target output directory
     *
     * @param context
     * @return
     */
    public static Path getTargetScript(Context context) {
        File directory = new File(getTarget(context).toFile(), "scripts");
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
     * Get the code-gen headers target output directory
     *
     * @param context
     * @return
     */
    public static Path getTargetCodeGenIncludeCC(Context context) {
        File directory = new File(getTargetCodeGenCC(context).toFile(), "include");
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

    /**
     * Copy a path from jar
     *
     * @param source
     * @param target
     * @throws URISyntaxException
     * @throws IOException
     */
    public static void copyFromJar(URI resource, String source, final Path target) throws IOException {

        FileSystem fileSystem;
        try {
            fileSystem = FileSystems.newFileSystem(resource, Collections.<String, String>emptyMap());
        } catch (FileSystemAlreadyExistsException e) {
            fileSystem = FileSystems.getFileSystem(resource);
        }

        final Path jarPath = fileSystem.getPath(source);

        Files.walkFileTree(jarPath, new SimpleFileVisitor<Path>() {

            private Path currentTarget;

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                currentTarget = target.resolve(jarPath.relativize(dir).toString());
                Files.createDirectories(currentTarget);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(jarPath.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }

        });
    }


}
