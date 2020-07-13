package ch.epfl.vlsc.hls.backend.kernel;

import java.util.Optional;

public class AxiConstants {

    public static final int C_S_AXI_CONTROL_ADDR_WIDTH = 32;

    public static final int C_S_AXI_CONTROL_DATA_WIDTH = 32;

    public static final int C_M_AXI_ADDR_WIDTH = 64;

//    public static final int C_M_AXI_DATA_WIDTH = 512;

    public static final int IO_STAGE_BUS_WIDTH = 512;

    public static Optional<Integer> getAxiDataWidth(int bitWidth) {

        switch (bitWidth) {
            case 8:
            case 16:
            case 32:
                return Optional.of(32);
            case 64:
                return Optional.of(64);
            case 128:
                return Optional.of(128);
            case 256:
                return Optional.of(256);
            case 512:
                return Optional.of(512);
            default:
                return Optional.empty();
        }

    }
}
