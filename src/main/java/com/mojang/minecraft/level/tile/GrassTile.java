package com.mojang.minecraft.level.tile;

import com.mojang.minecraft.level.Level;

import java.util.Random;

/**
 * Bloque de césped (Grass):
 * - Cara superior (1): verde (tex 0)
 * - Cara inferior (0): tierra (tex 2)
 * - Caras laterales (2..5): tierra con borde de césped (tex 3)
 * - Se convierte en tierra si no recibe luz solar y se propaga a bloques de tierra cercanos iluminados.
 */
public class GrassTile extends Tile {
    protected GrassTile(int id) {
        super(id, 3, "Césped");
    }

    @Override
    protected int getTexture(int face) {
        if (face == 1) return 0;
        if (face == 0) return 2;
        return 3;
    }

    @Override
    public void tick(Level level, int x, int y, int z, Random random) {
        if (!level.isLit(x, y, z)) {
            level.setTile(x, y, z, Tile.dirt.id);
        } else {
            for (int i = 0; i < 4; i++) {
                int xt = x + random.nextInt(3) - 1;
                int yt = y + random.nextInt(5) - 3;
                int zt = z + random.nextInt(3) - 1;
                if (level.getTile(xt, yt, zt) == Tile.dirt.id && level.isLit(xt, yt, zt)) {
                    level.setTile(xt, yt, zt, Tile.grass.id);
                }
            }
        }
    }
}
