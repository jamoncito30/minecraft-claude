package com.mojang.minecraft.level.tile;

import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.phys.AABB;
import com.mojang.minecraft.renderer.Tesselator;

import java.util.Random;

/**
 * Retoño de árbol / Arbusto (Sapling):
 * Se dibuja como dos planos diagonales cruzados en X (rots = 2) y no tiene caja de colisión.
 * Si se coloca mucho tiempo bajo el sol, puede crecer y convertirse en un árbol clásico de 0.0.14a.
 */
public class BushTile extends Tile {
    protected BushTile(int id) {
        super(id, 15, "Retoño");
    }

    protected BushTile(int id, int tex, String name) {
        super(id, tex, name);
    }

    @Override
    public void tick(Level level, int x, int y, int z, Random random) {
        int below = level.getTile(x, y - 1, z);
        if (!level.isLit(x, y, z) || (below != Tile.dirt.id && below != Tile.grass.id)) {
            level.setTile(x, y, z, 0);
        } else if (random.nextInt(15) == 0) {
            level.maybeGrowTree(x, y, z, random);
        }
    }

    @Override
    public boolean render(Tesselator t, Level level, int layer, int x, int y, int z) {
        if (layer != 0) {
            return false;
        }

        t.color(1.0f, 1.0f, 1.0f);
        t.light(level.getSkyLight(x, y, z) / 15.0f, level.getBlockLight(x, y, z) / 15.0f);
        this.renderCross(t, x, y, z);
        return true;
    }

    @Override
    public void renderGui(Tesselator t) {
        t.begin();
        t.color(1.0f, 1.0f, 1.0f);
        this.renderCross(t, 0, 0, 0);
        t.end();
    }

    private void renderCross(Tesselator t, float x, float y, float z) {
        int tex = this.getTexture(0);
        int xt = (tex % 16) * 16;
        int yt = (tex / 16) * 16;
        float u0 = xt / 256.0f;
        float u1 = (xt + 15.99f) / 256.0f;
        float v0 = yt / 256.0f;
        float v1 = (yt + 15.99f) / 256.0f;

        int rots = 2;
        for (int r = 0; r < rots; r++) {
            float xa = (float) (Math.sin(r * Math.PI / rots + Math.PI / 4.0) * 0.5);
            float za = (float) (Math.cos(r * Math.PI / rots + Math.PI / 4.0) * 0.5);
            float x0 = x + 0.5f - xa;
            float x1 = x + 0.5f + xa;
            float y0 = y + 0.0f;
            float y1 = y + 1.0f;
            float z0 = z + 0.5f - za;
            float z1 = z + 0.5f + za;

            t.vertexUV(x0, y1, z0, u1, v0);
            t.vertexUV(x1, y1, z1, u0, v0);
            t.vertexUV(x1, y0, z1, u0, v1);
            t.vertexUV(x0, y0, z0, u1, v1);

            t.vertexUV(x1, y1, z1, u1, v0);
            t.vertexUV(x0, y1, z0, u0, v0);
            t.vertexUV(x0, y0, z0, u0, v1);
            t.vertexUV(x1, y0, z1, u1, v1);
        }
    }

    @Override
    public AABB getAABB(int x, int y, int z) {
        return null;
    }

    @Override
    public boolean blocksLight() {
        return false;
    }

    @Override
    public boolean isSolid() {
        return false;
    }

    @Override
    public boolean isReplaceable() {
        return true;
    }
}
