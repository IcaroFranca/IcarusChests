package dev.icaro.icaruschests.util;

import org.bukkit.block.BlockFace;

/** Shared face sets, used wherever chest adjacency (double-chest linking) is checked. */
public final class BlockFaces {

    private BlockFaces() {
    }

    /** The 4 faces vanilla double chests can merge across — chests never merge vertically. */
    public static final BlockFace[] HORIZONTAL = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
}
