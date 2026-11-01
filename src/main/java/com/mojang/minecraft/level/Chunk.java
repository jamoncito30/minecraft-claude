package com.mojang.minecraft.level;

import com.mojang.minecraft.Player;
import com.mojang.minecraft.level.tile.Tile;
import com.mojang.minecraft.phys.AABB;
import com.mojang.minecraft.renderer.Tesselator;

import static org.lwjgl.opengl.GL11.*;

/**
 * Sección del mundo de 16x16x16 bloques (Chunk).
 * Compila sus bloques visibles en Display Lists de OpenGL (capa 0 = sólidos, capa 1 = agua).
 */
public class Chunk {
    public AABB aabb;
    public final Level level;
    public final int x0, y0, z0;
    public final int x1, y1, z1;
    public final float x, y, z;

    private boolean dirty = true;
    private final int lists;
    private final boolean[] empty = new boolean[2];
    public boolean visible = false;

    private static final Tesselator t = Tesselator.instance;
    public static int updates = 0;

    public Chunk(Level level, int x0, int y0, int z0, int x1, int y1, int z1) {
        this.level = level;
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;

        this.x = (x0 + x1) / 2.0f;
        this.y = (y0 + y1) / 2.0f;
        this.z = (z0 + z1) / 2.0f;

        this.aabb = new AABB(x0, y0, z0, x1, y1, z1);
        this.lists = glGenLists(2);
    }

    private void rebuild(int layer) {
        this.empty[layer] = true;
        updates++;

        glNewList(this.lists + layer, GL_COMPILE);
        t.begin();
        boolean renderedAny = false;
        for (int x = this.x0; x < this.x1; x++) {
            for (int y = this.y0; y < this.y1; y++) {
                for (int z = this.z0; z < this.z1; z++) {
                    int tileId = this.level.getTile(x, y, z);
                    if (tileId > 0 && Tile.tiles[tileId] != null) {
                        renderedAny |= Tile.tiles[tileId].render(t, this.level, layer, x, y, z);
                    }
                }
            }
        }
        t.end();
        glEndList();

        if (renderedAny) {
            this.empty[layer] = false;
        }
    }

    public void rebuild() {
        this.dirty = false;
        this.rebuild(0);
        this.rebuild(1);
    }

    public int getList(int layer) {
        if (!this.visible || this.empty[layer]) {
            return -1;
        }
        return this.lists + layer;
    }

    public void setDirty() {
        this.dirty = true;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public float distanceToSqr(Player player) {
        float xd = player.x - this.x;
        float yd = player.y - this.y;
        float zd = player.z - this.z;
        return xd * xd + yd * yd + zd * zd;
    }
}
