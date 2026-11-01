package com.mojang.minecraft.level.tile;

import com.mojang.minecraft.level.Level;

/**
 * Bloques con gravedad (Arena y Grava) introducidos en Minecraft Classic 0.0.14a:
 * Al colocarse o actualizarse un bloque vecino, caen instantáneamente hasta encontrar suelo sólido.
 */
public class FallingTile extends Tile {
    public FallingTile(int id, int tex, String name) {
        super(id, tex, name);
    }

    @Override
    public void onPlace(Level level, int x, int y, int z) {
        this.tryToFall(level, x, y, z);
    }

    @Override
    public void neighborChanged(Level level, int x, int y, int z, int type) {
        this.tryToFall(level, x, y, z);
    }

    private void tryToFall(Level level, int x, int y, int z) {
        int y0 = y;
        while (true) {
            int below = level.getTile(x, y0 - 1, z);
            boolean canFallThrough = (below == 0) || (Tile.tiles[below] != null && Tile.tiles[below].getLiquidType() > 0);
            if (!canFallThrough || y0 <= 0) {
                break;
            }
            y0--;
        }
        if (y0 != y) {
            level.swap(x, y, z, x, y0, z);
        }
    }
}
