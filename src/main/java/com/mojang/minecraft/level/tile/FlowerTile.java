package com.mojang.minecraft.level.tile;

import com.mojang.minecraft.level.Level;

import java.util.Random;

/**
 * Flores decorativas: se dibujan en cruz como los retoños, pero no crecen.
 * Se marchitan si quedan sin sol o sin tierra debajo.
 */
public class FlowerTile extends BushTile {
    protected FlowerTile(int id, int tex, String name) {
        super(id, tex, name);
    }

    @Override
    public void tick(Level level, int x, int y, int z, Random random) {
        int below = level.getTile(x, y - 1, z);
        if (!level.isLit(x, y, z) || (below != Tile.dirt.id && below != Tile.grass.id)) {
            level.setTile(x, y, z, 0);
        }
    }
}
