package com.mojang.minecraft.level.tile;

import com.mojang.minecraft.level.Level;

/**
 * Vidrio: bloque con colisión completa pero transparente a la luz.
 * Se dibuja en la capa recortada (alpha test) y oculta las caras que tocan a otro vidrio,
 * así una pared de vidrio se ve limpia en lugar de una rejilla de marcos.
 */
public class GlassTile extends Tile {
    protected GlassTile(int id, int tex) {
        super(id, tex, "Vidrio");
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
    protected boolean shouldRenderFace(Level level, int x, int y, int z, int layer, int face) {
        if (layer != 0) {
            return false;
        }
        return !level.isSolidTile(x, y, z) && level.getTile(x, y, z) != this.id;
    }
}
