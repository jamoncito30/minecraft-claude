package com.mojang.minecraft.level;

import com.mojang.minecraft.HitResult;
import com.mojang.minecraft.Player;
import com.mojang.minecraft.level.tile.Tile;
import com.mojang.minecraft.phys.AABB;
import com.mojang.minecraft.renderer.Frustum;
import com.mojang.minecraft.renderer.Tesselator;
import com.mojang.minecraft.renderer.Textures;
import org.lwjgl.BufferUtils;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * Administra y dibuja todos los Chunks del mundo, el océano/roca exterior infinito,
 * el recorte Frustum Culling y el resaltado del bloque seleccionado.
 */
public class LevelRenderer implements LevelListener {
    public static final int MAX_REBUILDS_PER_FRAME = 10;
    public static final int CHUNK_SIZE = 16;

    private final Level level;
    private final Chunk[] chunks;
    private final int xChunks;
    private final int yChunks;
    private final int zChunks;
    private final Textures textures;
    private final int surroundLists;
    private final IntBuffer callBuffer = BufferUtils.createIntBuffer(65536);

    public LevelRenderer(Level level, Textures textures) {
        this.level = level;
        this.textures = textures;
        level.addListener(this);

        this.xChunks = level.width / CHUNK_SIZE;
        this.yChunks = level.depth / CHUNK_SIZE;
        this.zChunks = level.height / CHUNK_SIZE;
        this.chunks = new Chunk[this.xChunks * this.yChunks * this.zChunks];

        for (int x = 0; x < this.xChunks; x++) {
            for (int y = 0; y < this.yChunks; y++) {
                for (int z = 0; z < this.zChunks; z++) {
                    int x0 = x * CHUNK_SIZE;
                    int y0 = y * CHUNK_SIZE;
                    int z0 = z * CHUNK_SIZE;
                    int x1 = (x + 1) * CHUNK_SIZE;
                    int y1 = (y + 1) * CHUNK_SIZE;
                    int z1 = (z + 1) * CHUNK_SIZE;

                    if (x1 > level.width) x1 = level.width;
                    if (y1 > level.depth) y1 = level.depth;
                    if (z1 > level.height) z1 = level.height;

                    this.chunks[(x + y * this.xChunks) * this.zChunks + z] =
                            new Chunk(level, x0, y0, z0, x1, y1, z1);
                }
            }
        }

        this.surroundLists = glGenLists(2);
        this.buildSurroundLists();
    }

    private void buildSurroundLists() {
        Tesselator t = Tesselator.instance;

        // Lista 0: Suelo exterior de roca alrededor de los límites del mundo (fiel a Classic 0.0.14a)
        glNewList(this.surroundLists, GL_COMPILE);
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        float groundY = 30.0f;
        int rockTex = Tile.rock.tex;
        float ru0 = ((rockTex % 16) * 16) / 256.0f;
        float ru1 = ((rockTex % 16) * 16 + 15.99f) / 256.0f;
        float rv0 = ((rockTex / 16) * 16) / 256.0f;
        float rv1 = ((rockTex / 16) * 16 + 15.99f) / 256.0f;
        int gStep = 16;
        int gBorder = 128;
        t.begin();
        t.color(0.5f, 0.5f, 0.5f);
        for (int xx = -gBorder; xx < this.level.width + gBorder; xx += gStep) {
            for (int zz = -gBorder; zz < this.level.height + gBorder; zz += gStep) {
                if (xx < 0 || zz < 0 || xx >= this.level.width || zz >= this.level.height) {
                    t.vertexUV(xx, groundY, zz + gStep, ru0, rv1);
                    t.vertexUV(xx + gStep, groundY, zz + gStep, ru1, rv1);
                    t.vertexUV(xx + gStep, groundY, zz, ru1, rv0);
                    t.vertexUV(xx, groundY, zz, ru0, rv0);
                }
            }
        }
        t.end();
        glEndList();

        // Lista 1: Océano exterior infinito al nivel del mar (y = 32 - 0.1)
        glNewList(this.surroundLists + 1, GL_COMPILE);
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        float waterY = 32.0f - 0.1f;
        int tex = Tile.water.tex;
        float u0 = ((tex % 16) * 16) / 256.0f;
        float u1 = ((tex % 16) * 16 + 15.99f) / 256.0f;
        float v0 = ((tex / 16) * 16) / 256.0f;
        float v1 = ((tex / 16) * 16 + 15.99f) / 256.0f;

        t.begin();
        t.color(1.0f, 1.0f, 1.0f);
        int step = 16;
        int border = 128;
        for (int xx = -border; xx < this.level.width + border; xx += step) {
            for (int zz = -border; zz < this.level.height + border; zz += step) {
                if (xx < 0 || zz < 0 || xx >= this.level.width || zz >= this.level.height) {
                    t.vertexUV(xx, waterY, zz + step, u0, v1);
                    t.vertexUV(xx + step, waterY, zz + step, u1, v1);
                    t.vertexUV(xx + step, waterY, zz, u1, v0);
                    t.vertexUV(xx, waterY, zz, u0, v0);
                }
            }
        }
        t.end();
        glEndList();
    }

    public void cull(Frustum frustum) {
        for (Chunk chunk : this.chunks) {
            chunk.visible = frustum.isVisible(chunk.aabb);
        }
    }

    public void render(Player player, int layer) {
        glEnable(GL_TEXTURE_2D);
        int id = this.textures.loadTexture("/terrain.png", GL_NEAREST);
        glBindTexture(GL_TEXTURE_2D, id);

        if (layer == 1) {
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        }

        this.callBuffer.clear();
        for (Chunk chunk : this.chunks) {
            int list = chunk.getList(layer);
            if (list >= 0) {
                this.callBuffer.put(list);
            }
        }
        this.callBuffer.flip();
        if (this.callBuffer.remaining() > 0) {
            glCallLists(this.callBuffer);
        }

        // Las listas exteriores no llevan canal de luz: se usa la normal actual (= cielo abierto)
        glNormal3f(1.0f, 0.0f, 0.0f);
        if (layer == 0) {
            glCallList(this.surroundLists);
        } else if (layer == 1) {
            glCallList(this.surroundLists + 1);
            glDisable(GL_BLEND);
        }

        glDisable(GL_TEXTURE_2D);
    }

    public void updateDirtyChunks(Player player) {
        List<Chunk> dirty = new ArrayList<>();
        for (Chunk chunk : this.chunks) {
            if (chunk.isDirty()) {
                dirty.add(chunk);
            }
        }
        if (dirty.isEmpty()) {
            return;
        }

        dirty.sort(new DirtyChunkSorter(player));
        for (int i = 0; i < dirty.size() && i < MAX_REBUILDS_PER_FRAME; i++) {
            dirty.get(i).rebuild();
        }
    }

    public void renderHit(HitResult h) {
        Tesselator t = Tesselator.instance;

        // 1. Borde negro (wireframe clásico de 0.0.14a alrededor del bloque apuntado)
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(0.0f, 0.0f, 0.0f, 0.4f);
        glLineWidth(2.0f);
        glDisable(GL_TEXTURE_2D);
        glDepthMask(false);

        int tileId = this.level.getTile(h.x, h.y, h.z);
        if (tileId > 0 && Tile.tiles[tileId] != null) {
            AABB box = Tile.tiles[tileId].getTileAABB(h.x, h.y, h.z).grow(0.002f, 0.002f, 0.002f);

            glBegin(GL_LINE_STRIP);
            glVertex3f(box.x0, box.y0, box.z0);
            glVertex3f(box.x1, box.y0, box.z0);
            glVertex3f(box.x1, box.y0, box.z1);
            glVertex3f(box.x0, box.y0, box.z1);
            glVertex3f(box.x0, box.y0, box.z0);
            glEnd();

            glBegin(GL_LINE_STRIP);
            glVertex3f(box.x0, box.y1, box.z0);
            glVertex3f(box.x1, box.y1, box.z0);
            glVertex3f(box.x1, box.y1, box.z1);
            glVertex3f(box.x0, box.y1, box.z1);
            glVertex3f(box.x0, box.y1, box.z0);
            glEnd();

            glBegin(GL_LINES);
            glVertex3f(box.x0, box.y0, box.z0);
            glVertex3f(box.x0, box.y1, box.z0);
            glVertex3f(box.x1, box.y0, box.z0);
            glVertex3f(box.x1, box.y1, box.z0);
            glVertex3f(box.x1, box.y0, box.z1);
            glVertex3f(box.x1, box.y1, box.z1);
            glVertex3f(box.x0, box.y0, box.z1);
            glVertex3f(box.x0, box.y1, box.z1);
            glEnd();
        }

        // 2. Pulso luminoso suave sobre la cara apuntada (estilo rd-132211 / 0.0.11a)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);
        glColor4f(1.0f, 1.0f, 1.0f, ((float) Math.sin(System.currentTimeMillis() / 100.0) * 0.2f + 0.4f) * 0.35f);
        t.begin();
        t.noColor();
        Tile.rock.renderFaceNoTexture(t, h.x, h.y, h.z, h.face);
        t.end();

        glDepthMask(true);
        glDisable(GL_BLEND);
    }

    public void setDirty(int x0, int y0, int z0, int x1, int y1, int z1) {
        x0 /= CHUNK_SIZE;
        x1 /= CHUNK_SIZE;
        y0 /= CHUNK_SIZE;
        y1 /= CHUNK_SIZE;
        z0 /= CHUNK_SIZE;
        z1 /= CHUNK_SIZE;

        if (x0 < 0) x0 = 0;
        if (y0 < 0) y0 = 0;
        if (z0 < 0) z0 = 0;
        if (x1 >= this.xChunks) x1 = this.xChunks - 1;
        if (y1 >= this.yChunks) y1 = this.yChunks - 1;
        if (z1 >= this.zChunks) z1 = this.zChunks - 1;

        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    this.chunks[(x + y * this.xChunks) * this.zChunks + z].setDirty();
                }
            }
        }
    }

    @Override
    public void tileChanged(int x, int y, int z) {
        this.setDirty(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
    }

    @Override
    public void lightColumnChanged(int x, int z, int y0, int y1) {
        // La columna de sol solo se usa para la lógica (césped, retoños); la luz visible viene de LightEngine
    }

    @Override
    public void lightChanged(int x0, int y0, int z0, int x1, int y1, int z1) {
        this.setDirty(x0 - 1, y0 - 1, z0 - 1, x1 + 1, y1 + 1, z1 + 1);
    }

    @Override
    public void allChanged() {
        this.setDirty(0, 0, 0, this.level.width, this.level.depth, this.level.height);
    }
}
