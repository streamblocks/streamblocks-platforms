package ch.epfl.vlsc.sw.launcher;

import ch.epfl.vlsc.launcher.SBLauncher;
import ch.epfl.vlsc.sw.platform.Multicore;

public class MulticoreLauncher {

    private static final String toolName = "sbc";
    private static final String toolFullName = "The StreamBlocks Tycho Compiler";
    private static final String toolVersion = "0.0.1-SNAPSHOT";

    public static void main(String[] args) {
        SBLauncher main = new SBLauncher(new Multicore(), "art-sbc");
        main.run(args);
    }

}
