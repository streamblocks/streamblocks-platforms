package platformutils;

import java.io.File;
import java.nio.file.Path;

/**
 * Utils class for platforms
 *
 * @author Endri Bezati
 */
public class Utils {
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

}
