package com.mojang.minecraft.level.tile;

import com.mojang.minecraft.level.Level;

/**
 * Hojas de árbol (Leaves) introducidas en Minecraft Classic 0.0.14a:
 * Permiten el paso de la luz y no ocultan las caras de los bloques adyacentes.
 */
public class LeafTile extends Tile {
    protected LeafTile(int id, int tex) {
        super(id, tex, "Hojas");
    }

    @Override
    public boolean isSolid() {
        return false;
    }

    @Override
    public boolean blocksLight() {
        return false;
    }

    @Override
    public int getLightOpacity() {
        return 1;
    }

    @Override
    protected boolean shouldRenderFace(Level level, int x, int y, int z, int layer, int face) {
        if (layer != 0) {
            return false;
        }
        return !level.isSolidTile(x, y, z);
    }
}
