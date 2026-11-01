package com.mojang.minecraft.level.tile;

import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.particle.Particle;
import com.mojang.minecraft.particle.ParticleEngine;
import com.mojang.minecraft.phys.AABB;
import com.mojang.minecraft.renderer.Tesselator;

import java.util.Random;

/**
 * Clase base de todos los bloques (Tiles).
 * Define los IDs (1..18 originales + bloques nuevos), geometría de las 6 caras,
 * iluminación suave por vértice con oclusión ambiental, Face Culling, propiedades de luz
 * (opacidad y emisión) y generación de partículas al destruir el bloque.
 */
public class Tile {
    public static final Tile[] tiles = new Tile[256];

    public static final Tile empty = null;
    public static final Tile rock = new Tile(1, 1, "Piedra");
    public static final Tile grass = new GrassTile(2);
    public static final Tile dirt = new Tile(3, 2, "Tierra");
    public static final Tile stoneBrick = new Tile(4, 16, "Adoquín");
    public static final Tile wood = new Tile(5, 4, "Tablones");
    public static final Tile bush = new BushTile(6);
    public static final Tile unbreakable = new Tile(7, 17, "Roca madre");
    public static final Tile water = new LiquidTile(8, 1);
    public static final Tile calmWater = new LiquidTile(9, 1);
    public static final Tile lava = new LiquidTile(10, 2);
    public static final Tile calmLava = new LiquidTile(11, 2);
    public static final Tile sand = new FallingTile(12, 18, "Arena");
    public static final Tile gravel = new FallingTile(13, 19, "Grava");
    public static final Tile goldOre = new Tile(14, 32, "Mena de oro");
    public static final Tile ironOre = new Tile(15, 33, "Mena de hierro");
    public static final Tile coalOre = new Tile(16, 34, "Mena de carbón");
    public static final Tile log = new LogTile(17);
    public static final Tile leaves = new LeafTile(18, 22);
    // Bloques nuevos
    public static final Tile glass = new GlassTile(19, 5);
    public static final Tile brick = new Tile(20, 6, "Ladrillos");
    public static final Tile polishedStone = new Tile(21, 7, "Piedra pulida");
    public static final Tile lamp = new Tile(22, 8, "Farol").setLightEmission(15);
    public static final Tile yellowFlower = new FlowerTile(23, 9, "Flor amarilla");
    public static final Tile redFlower = new FlowerTile(24, 10, "Flor roja");

    /** Sombreado fijo por cara: abajo, arriba, norte, sur, oeste, este. */
    private static final float[] FACE_SHADE = {0.5f, 1.0f, 0.8f, 0.8f, 0.6f, 0.6f};
    /** Brillo según el número de vecinos que ocluyen la esquina (0 = muy ocluida). */
    private static final float[] AO_CURVE = {0.55f, 0.7f, 0.85f, 1.0f};
    private static final int[][] FACE_NORMALS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };
    /**
     * Vértices de cada cara en el mismo orden que el renderizado original: por vértice
     * {x, y, z, u, v} donde 0 = mínimo y 1 = máximo de la forma del bloque / de la celda UV.
     */
    private static final int[][][] FACE_VERTS = {
            {{0, 0, 1, 0, 1}, {0, 0, 0, 0, 0}, {1, 0, 0, 1, 0}, {1, 0, 1, 1, 1}},
            {{1, 1, 1, 1, 1}, {1, 1, 0, 1, 0}, {0, 1, 0, 0, 0}, {0, 1, 1, 0, 1}},
            {{0, 1, 0, 1, 0}, {1, 1, 0, 0, 0}, {1, 0, 0, 0, 1}, {0, 0, 0, 1, 1}},
            {{0, 1, 1, 0, 0}, {0, 0, 1, 0, 1}, {1, 0, 1, 1, 1}, {1, 1, 1, 1, 0}},
            {{0, 1, 1, 1, 0}, {0, 1, 0, 0, 0}, {0, 0, 0, 0, 1}, {0, 0, 1, 1, 1}},
            {{1, 0, 1, 0, 1}, {1, 0, 0, 1, 1}, {1, 1, 0, 1, 0}, {1, 1, 1, 0, 0}},
    };

    public int tex;
    public final int id;
    public final String name;
    private int lightEmission = 0;

    protected float xx0 = 0.0f;
    protected float yy0 = 0.0f;
    protected float zz0 = 0.0f;
    protected float xx1 = 1.0f;
    protected float yy1 = 1.0f;
    protected float zz1 = 1.0f;

    // Buffers reutilizados durante la construcción de chunks (solo se usa desde el hilo de render)
    private static final float[] vShade = new float[4];
    private static final float[] vSky = new float[4];
    private static final float[] vBlock = new float[4];

    protected Tile(int id, String name) {
        tiles[id] = this;
        this.id = id;
        this.name = name;
    }

    protected Tile(int id, int tex, String name) {
        this(id, name);
        this.tex = tex;
    }

    protected Tile setLightEmission(int emission) {
        this.lightEmission = emission;
        return this;
    }

    protected void setShape(float x0, float y0, float z0, float x1, float y1, float z1) {
        this.xx0 = x0;
        this.yy0 = y0;
        this.zz0 = z0;
        this.xx1 = x1;
        this.yy1 = y1;
        this.zz1 = z1;
    }

    protected int getTexture(int face) {
        return this.tex;
    }

    /**
     * Renderiza el bloque dentro de un Chunk (layer 0 = opacos/recortados, layer 1 = translúcidos)
     * con iluminación suave: cada vértice promedia la luz de las 4 celdas que tocan esa esquina
     * y se oscurece según cuántas de ellas son bloques sólidos (oclusión ambiental).
     */
    public boolean render(Tesselator t, Level level, int layer, int x, int y, int z) {
        boolean rendered = false;
        for (int face = 0; face < 6; face++) {
            int[] n = FACE_NORMALS[face];
            if (this.shouldRenderFace(level, x + n[0], y + n[1], z + n[2], layer, face)) {
                this.renderFaceLit(t, level, x, y, z, face);
                rendered = true;
            }
        }
        return rendered;
    }

    protected void renderFaceLit(Tesselator t, Level level, int x, int y, int z, int face) {
        int[] n = FACE_NORMALS[face];
        int qx = x + n[0];
        int qy = y + n[1];
        int qz = z + n[2];
        float shade = FACE_SHADE[face];
        boolean fullBright = this.lightEmission > 0;

        for (int v = 0; v < 4; v++) {
            if (fullBright) {
                vShade[v] = shade;
                vSky[v] = level.getSkyLight(qx, qy, qz);
                vBlock[v] = this.lightEmission;
                continue;
            }
            int[] c = FACE_VERTS[face][v];
            int ox = c[0] == 1 ? 1 : -1;
            int oy = c[1] == 1 ? 1 : -1;
            int oz = c[2] == 1 ? 1 : -1;
            int s1x, s1y, s1z, s2x, s2y, s2z;
            if (n[1] != 0) {
                s1x = ox; s1y = 0; s1z = 0;
                s2x = 0; s2y = 0; s2z = oz;
            } else if (n[2] != 0) {
                s1x = ox; s1y = 0; s1z = 0;
                s2x = 0; s2y = oy; s2z = 0;
            } else {
                s1x = 0; s1y = oy; s1z = 0;
                s2x = 0; s2y = 0; s2z = oz;
            }
            boolean b1 = level.isSolidTile(qx + s1x, qy + s1y, qz + s1z);
            boolean b2 = level.isSolidTile(qx + s2x, qy + s2y, qz + s2z);
            boolean bc = level.isSolidTile(qx + s1x + s2x, qy + s1y + s2y, qz + s1z + s2z);
            int ao = (b1 && b2) ? 0 : 3 - ((b1 ? 1 : 0) + (b2 ? 1 : 0) + (bc ? 1 : 0));

            float sky = level.getSkyLight(qx, qy, qz);
            float blk = level.getBlockLight(qx, qy, qz);
            int count = 1;
            if (!b1) {
                sky += level.getSkyLight(qx + s1x, qy + s1y, qz + s1z);
                blk += level.getBlockLight(qx + s1x, qy + s1y, qz + s1z);
                count++;
            }
            if (!b2) {
                sky += level.getSkyLight(qx + s2x, qy + s2y, qz + s2z);
                blk += level.getBlockLight(qx + s2x, qy + s2y, qz + s2z);
                count++;
            }
            if (!bc && !(b1 && b2)) {
                sky += level.getSkyLight(qx + s1x + s2x, qy + s1y + s2y, qz + s1z + s2z);
                blk += level.getBlockLight(qx + s1x + s2x, qy + s1y + s2y, qz + s1z + s2z);
                count++;
            }
            vShade[v] = shade * AO_CURVE[ao];
            vSky[v] = sky / count;
            vBlock[v] = blk / count;
        }

        // Girar la diagonal del quad cuando la oclusión es asimétrica (evita "costuras" de AO)
        float d02 = vShade[0] + vShade[2] + (vSky[0] + vSky[2]) * 0.02f;
        float d13 = vShade[1] + vShade[3] + (vSky[1] + vSky[3]) * 0.02f;
        int start = d02 < d13 ? 1 : 0;

        int tex = this.getTexture(face);
        float u0 = ((tex % 16) * 16) / 256.0f;
        float u1 = ((tex % 16) * 16 + 15.99f) / 256.0f;
        float v0 = ((tex / 16) * 16) / 256.0f;
        float v1 = ((tex / 16) * 16 + 15.99f) / 256.0f;

        for (int k = 0; k < 4; k++) {
            int v = (start + k) & 3;
            int[] c = FACE_VERTS[face][v];
            t.color(vShade[v], vShade[v], vShade[v]);
            t.light(vSky[v] / 15.0f, vBlock[v] / 15.0f);
            t.vertexUV(
                    x + (c[0] == 1 ? this.xx1 : this.xx0),
                    y + (c[1] == 1 ? this.yy1 : this.yy0),
                    z + (c[2] == 1 ? this.zz1 : this.zz0),
                    c[3] == 1 ? u1 : u0,
                    c[4] == 1 ? v1 : v0);
        }
    }

    protected boolean shouldRenderFace(Level level, int x, int y, int z, int layer, int face) {
        if (layer != 0) {
            return false;
        }
        return !level.isSolidTile(x, y, z);
    }

    /** Cara sin iluminación por vértice (HUD, mano y bloques en miniatura). */
    public void renderFace(Tesselator t, int x, int y, int z, int face) {
        int tex = this.getTexture(face);
        float u0 = ((tex % 16) * 16) / 256.0f;
        float u1 = ((tex % 16) * 16 + 15.99f) / 256.0f;
        float v0 = ((tex / 16) * 16) / 256.0f;
        float v1 = ((tex / 16) * 16 + 15.99f) / 256.0f;
        for (int[] c : FACE_VERTS[face]) {
            t.vertexUV(
                    x + (c[0] == 1 ? this.xx1 : this.xx0),
                    y + (c[1] == 1 ? this.yy1 : this.yy0),
                    z + (c[2] == 1 ? this.zz1 : this.zz0),
                    c[3] == 1 ? u1 : u0,
                    c[4] == 1 ? v1 : v0);
        }
    }

    public void renderFaceNoTexture(Tesselator t, int x, int y, int z, int face) {
        for (int[] c : FACE_VERTS[face]) {
            t.vertex(x + c[0], y + c[1], z + c[2]);
        }
    }

    /**
     * Renderiza el bloque en miniatura 3D para el HUD (esquina superior derecha / barra) y la mano.
     */
    public void renderGui(Tesselator t) {
        t.begin();
        t.color(0.5f, 0.5f, 0.5f);
        this.renderFace(t, 0, 0, 0, 0);
        t.color(1.0f, 1.0f, 1.0f);
        this.renderFace(t, 0, 0, 0, 1);
        t.color(0.8f, 0.8f, 0.8f);
        this.renderFace(t, 0, 0, 0, 2);
        this.renderFace(t, 0, 0, 0, 3);
        t.color(0.6f, 0.6f, 0.6f);
        this.renderFace(t, 0, 0, 0, 4);
        this.renderFace(t, 0, 0, 0, 5);
        t.end();
    }

    public AABB getTileAABB(int x, int y, int z) {
        return new AABB(x, y, z, x + 1, y + 1, z + 1);
    }

    public AABB getAABB(int x, int y, int z) {
        return new AABB(x, y, z, x + 1, y + 1, z + 1);
    }

    public boolean blocksLight() {
        return true;
    }

    /** Cuánto atenúa la luz al atravesar el bloque (0 = aire, 15 = opaco). */
    public int getLightOpacity() {
        return this.blocksLight() ? 15 : 0;
    }

    /** Intensidad de luz que emite el bloque (0..15). */
    public int getLightEmission() {
        return this.lightEmission;
    }

    public boolean isSolid() {
        return true;
    }

    /** Plantas pequeñas que un árbol puede pisar al crecer. */
    public boolean isReplaceable() {
        return false;
    }

    public boolean mayPick() {
        return true;
    }

    public int getLiquidType() {
        return 0;
    }

    public void tick(Level level, int x, int y, int z, Random random) {
    }

    public void neighborChanged(Level level, int x, int y, int z, int type) {
    }

    public void onPlace(Level level, int x, int y, int z) {
    }

    public void destroy(Level level, int x, int y, int z, ParticleEngine particleEngine) {
        int SD = 4;
        for (int xx = 0; xx < SD; xx++) {
            for (int yy = 0; yy < SD; yy++) {
                for (int zz = 0; zz < SD; zz++) {
                    float xp = x + (xx + 0.5f) / SD;
                    float yp = y + (yy + 0.5f) / SD;
                    float zp = z + (zz + 0.5f) / SD;
                    particleEngine.add(new Particle(level, xp, yp, zp, xp - x - 0.5f, yp - y - 0.5f, zp - z - 0.5f, this.tex));
                }
            }
        }
    }
}
