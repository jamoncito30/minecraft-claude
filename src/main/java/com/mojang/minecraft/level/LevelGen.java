package com.mojang.minecraft.level;

import com.mojang.minecraft.level.tile.Tile;

import java.util.Random;

/**
 * Generador procedural de mundos de Minecraft Classic 0.0.14a.
 * Implementa las fases originales de Notch:
 *   1. Raising (Elevación con Perlin Noise combinado)
 *   2. Eroding (Erosión de colinas)
 *   3. Soiling (Estratos de piedra, tierra, bedrock y vetas de minerales)
 *   4. Carving (Cuevas subterráneas serpenteantes)
 *   5. Watering (Océanos y lagos al nivel del mar = 32)
 *   6. Melting (Playas de arena, grava y césped superficial)
 *   7. Planting (Árboles con tronco y hojas + retoños)
 */
public class LevelGen {
    private final int width;
    private final int depth;
    private final int height;
    private final Random random = new Random();
    private byte[] blocks;

    public LevelGen(int width, int depth, int height) {
        this.width = width;
        this.depth = depth;
        this.height = height;
    }

    public byte[] generateMap() {
        int w = this.width;
        int d = this.depth;
        int h = this.height;
        this.blocks = new byte[w * d * h];

        // 1. Raising (Mapa de alturas con ruido Perlin combinado)
        PerlinNoise noise1 = new PerlinNoise(this.random, 8);
        PerlinNoise noise2 = new PerlinNoise(this.random, 8);
        PerlinNoise noise3 = new PerlinNoise(this.random, 8);
        PerlinNoise erosion1 = new PerlinNoise(this.random, 8);
        PerlinNoise erosion2 = new PerlinNoise(this.random, 8);

        int[] heightmap = new int[w * h];
        double scale = 1.3;

        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                double hLow = noise1.getValue(x * scale, z * scale) / 6.0 - 4.0;
                double hHigh = noise2.getValue(x * scale, z * scale) / 5.0 + 6.0;
                double selector = noise3.getValue(x * scale * 0.5, z * scale * 0.5) / 8.0;
                double val = selector > 0.0 ? hLow : Math.max(hLow, hHigh);
                val *= 0.5;
                if (val < 0.0) {
                    val *= 0.8;
                }
                heightmap[x + z * w] = (int) val + d / 2;
            }
        }

        // 2. Eroding (Suavizado de terreno)
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                double val1 = erosion1.getValue(x * 2.0, z * 2.0) / 8.0;
                int val2 = erosion2.getValue(x * 2.0, z * 2.0) > 0.0 ? 1 : 0;
                if (val1 > 2.0) {
                    int cur = heightmap[x + z * w];
                    heightmap[x + z * w] = ((cur - val2) / 2) * 2 + val2;
                }
            }
        }

        // 3. Soiling (Capas de Bedrock, Piedra y Tierra)
        PerlinNoise dirtNoise = new PerlinNoise(this.random, 8);
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                int dirtThickness = (int) (dirtNoise.getValue(x, z) / 24.0) - 4;
                int dirtTransition = heightmap[x + z * w];
                int stoneTransition = dirtTransition + dirtThickness;
                heightmap[x + z * w] = Math.max(dirtTransition, stoneTransition);

                for (int y = 0; y < d; y++) {
                    int index = (y * h + z) * w + x;
                    int id = 0;
                    if (y == 0) {
                        id = Tile.unbreakable.id;
                    } else if (y <= stoneTransition) {
                        id = Tile.rock.id;
                    } else if (y <= dirtTransition) {
                        id = Tile.dirt.id;
                    }
                    this.blocks[index] = (byte) id;
                }
            }
        }

        // 4. Carving (Cuevas 3D y Vetas de Minerales)
        this.carveCaves();
        this.populateOre(Tile.coalOre.id, 90, 1, 4);
        this.populateOre(Tile.ironOre.id, 70, 2, 4);
        this.populateOre(Tile.goldOre.id, 50, 3, 4);

        // 5. Watering (Rellenar nivel del mar = 32)
        int waterLevel = 32;
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                for (int y = 0; y < waterLevel; y++) {
                    int index = (y * h + z) * w + x;
                    if (this.blocks[index] == 0) {
                        this.blocks[index] = (byte) Tile.calmWater.id;
                    }
                }
            }
        }

        // 6. Surface (Césped, Arena en playas y Grava bajo el agua)
        PerlinNoise sandNoise = new PerlinNoise(this.random, 8);
        PerlinNoise gravelNoise = new PerlinNoise(this.random, 8);

        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                boolean sandBeach = sandNoise.getValue(x, z) > 8.0;
                boolean gravelBeach = gravelNoise.getValue(x, z) > 12.0;
                int y = heightmap[x + z * w];
                if (y < 1 || y >= d - 1) continue;

                int index = (y * h + z) * w + x;
                int above = ((y + 1) * h + z) * w + x;
                int blockAbove = this.blocks[above] & 0xFF;

                if ((blockAbove == Tile.water.id || blockAbove == Tile.calmWater.id) && y <= waterLevel - 1 && gravelBeach) {
                    this.blocks[index] = (byte) Tile.gravel.id;
                }
                if (blockAbove == 0) {
                    int surfaceId = Tile.grass.id;
                    if (y <= waterLevel && sandBeach) {
                        surfaceId = Tile.sand.id;
                    }
                    this.blocks[index] = (byte) surfaceId;
                }
            }
        }

        // 7. Planting (Árboles y Retoños de 0.0.14a)
        this.plantTrees(heightmap, waterLevel);
        this.plantFlowers(heightmap, waterLevel);

        return this.blocks;
    }

    private void carveCaves() {
        int w = this.width;
        int d = this.depth;
        int h = this.height;
        int caveCount = w * h * d / 256 / 64 * 2;

        for (int i = 0; i < caveCount; i++) {
            float x = this.random.nextFloat() * w;
            float y = this.random.nextFloat() * d;
            float z = this.random.nextFloat() * h;
            int length = (int) ((this.random.nextFloat() + this.random.nextFloat()) * 150.0f);

            float theta = (float) (this.random.nextFloat() * Math.PI * 2.0);
            float deltaTheta = 0.0f;
            float phi = (float) (this.random.nextFloat() * Math.PI * 2.0);
            float deltaPhi = 0.0f;

            for (int l = 0; l < length; l++) {
                x += (float) (Math.sin(theta) * Math.cos(phi));
                z += (float) (Math.cos(theta) * Math.cos(phi));
                y += (float) Math.sin(phi);

                theta += deltaTheta * 0.2f;
                deltaTheta = (deltaTheta * 0.9f) + (this.random.nextFloat() - this.random.nextFloat());
                phi = (phi + deltaPhi * 0.5f) * 0.5f;
                deltaPhi = (deltaPhi * 0.9f) + (this.random.nextFloat() - this.random.nextFloat());

                if (this.random.nextFloat() >= 0.25f) {
                    float cx = x + (this.random.nextFloat() * 4.0f - 2.0f) * 0.2f;
                    float cy = y + (this.random.nextFloat() * 4.0f - 2.0f) * 0.2f;
                    float cz = z + (this.random.nextFloat() * 4.0f - 2.0f) * 0.2f;
                    float size = (float) (Math.sin(l * Math.PI / length) * 2.5 + 1.0);

                    for (int xx = (int) (cx - size); xx <= (int) (cx + size); xx++) {
                        for (int yy = (int) (cy - size); yy <= (int) (cy + size); yy++) {
                            for (int zz = (int) (cz - size); zz <= (int) (cz + size); zz++) {
                                float xd = xx - cx;
                                float yd = yy - cy;
                                float zd = zz - cz;
                                if (xd * xd + yd * yd * 2.0f + zd * zd < size * size &&
                                    xx >= 1 && yy >= 1 && zz >= 1 && xx < w - 1 && yy < d - 1 && zz < h - 1) {
                                    int idx = (yy * h + zz) * w + xx;
                                    if (this.blocks[idx] == Tile.rock.id) {
                                        this.blocks[idx] = 0;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void populateOre(int tileId, int abundance, int minDepthDiv, int maxDepthDiv) {
        int w = this.width;
        int d = this.depth;
        int h = this.height;
        int count = w * h * d / 256 / 64 * abundance / 100;

        for (int i = 0; i < count; i++) {
            float x = this.random.nextFloat() * w;
            float y = this.random.nextFloat() * d * minDepthDiv / maxDepthDiv;
            float z = this.random.nextFloat() * h;
            int length = (int) ((this.random.nextFloat() + this.random.nextFloat()) * 60.0f * abundance / 100.0f);
            float theta = (float) (this.random.nextFloat() * Math.PI * 2.0);
            float phi = (float) (this.random.nextFloat() * Math.PI * 2.0);

            for (int l = 0; l < length; l++) {
                x += (float) (Math.sin(theta) * Math.cos(phi));
                z += (float) (Math.cos(theta) * Math.cos(phi));
                y += (float) Math.sin(phi);
                float size = (float) Math.sin(l * Math.PI / length) * abundance / 100.0f + 1.0f;

                for (int xx = (int) (x - size); xx <= (int) (x + size); xx++) {
                    for (int yy = (int) (y - size); yy <= (int) (y + size); yy++) {
                        for (int zz = (int) (z - size); zz <= (int) (z + size); zz++) {
                            float xd = xx - x;
                            float yd = yy - y;
                            float zd = zz - z;
                            if (xd * xd + yd * yd + zd * zd < size * size &&
                                xx >= 1 && yy >= 1 && zz >= 1 && xx < w - 1 && yy < d - 1 && zz < h - 1) {
                                int idx = (yy * h + zz) * w + xx;
                                if (this.blocks[idx] == Tile.rock.id) {
                                    this.blocks[idx] = (byte) tileId;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void plantTrees(int[] heightmap, int waterLevel) {
        int w = this.width;
        int d = this.depth;
        int h = this.height;
        int treePatches = w * h / 4000;

        for (int i = 0; i < treePatches; i++) {
            int patchX = this.random.nextInt(w);
            int patchZ = this.random.nextInt(h);
            for (int j = 0; j < 20; j++) {
                int tx = patchX;
                int tz = patchZ;
                for (int k = 0; k < 12; k++) {
                    tx += this.random.nextInt(6) - this.random.nextInt(6);
                    tz += this.random.nextInt(6) - this.random.nextInt(6);
                    if (tx >= 4 && tz >= 4 && tx < w - 4 && tz < h - 4 && this.random.nextInt(4) == 0) {
                        int ty = heightmap[tx + tz * w] + 1;
                        if (ty > waterLevel + 1 && ty < d - 8) {
                            this.placeTree(tx, ty, tz);
                        }
                    }
                }
            }
        }
    }

    /** Pequeños prados de flores amarillas o rojas sobre el césped. */
    private void plantFlowers(int[] heightmap, int waterLevel) {
        int w = this.width;
        int d = this.depth;
        int h = this.height;
        int patches = w * h / 2500;
        for (int i = 0; i < patches; i++) {
            int flower = this.random.nextBoolean() ? Tile.yellowFlower.id : Tile.redFlower.id;
            int px = this.random.nextInt(w);
            int pz = this.random.nextInt(h);
            for (int j = 0; j < 24; j++) {
                int fx = px + this.random.nextInt(9) - this.random.nextInt(9);
                int fz = pz + this.random.nextInt(9) - this.random.nextInt(9);
                if (fx < 0 || fz < 0 || fx >= w || fz >= h) continue;
                int fy = heightmap[fx + fz * w] + 1;
                if (fy <= waterLevel || fy >= d - 1) continue;
                int below = this.blocks[((fy - 1) * h + fz) * w + fx] & 0xFF;
                int idx = (fy * h + fz) * w + fx;
                if (below == Tile.grass.id && this.blocks[idx] == 0) {
                    this.blocks[idx] = (byte) flower;
                }
            }
        }
    }

    private void placeTree(int x, int y, int z) {
        int w = this.width;
        int h = this.height;
        int treeHeight = this.random.nextInt(3) + 4;

        int belowIdx = ((y - 1) * h + z) * w + x;
        int belowId = this.blocks[belowIdx] & 0xFF;
        if (belowId != Tile.grass.id && belowId != Tile.dirt.id) {
            return;
        }

        // Verificar que haya espacio libre
        for (int yy = y; yy <= y + 1 + treeHeight; yy++) {
            int radius = 1;
            if (yy == y) radius = 0;
            if (yy >= y + 1 + treeHeight - 2) radius = 2;
            for (int xx = x - radius; xx <= x + radius; xx++) {
                for (int zz = z - radius; zz <= z + radius; zz++) {
                    if (this.blocks[(yy * h + zz) * w + xx] != 0) {
                        return;
                    }
                }
            }
        }

        this.blocks[belowIdx] = (byte) Tile.dirt.id;

        // Copa de hojas
        for (int yy = y - 3 + treeHeight; yy <= y + treeHeight; yy++) {
            int yo = yy - (y + treeHeight);
            int radius = 1 - yo / 2;
            for (int xx = x - radius; xx <= x + radius; xx++) {
                int xo = xx - x;
                for (int zz = z - radius; zz <= z + radius; zz++) {
                    int zo = zz - z;
                    if (Math.abs(xo) != radius || Math.abs(zo) != radius || (this.random.nextInt(2) != 0 && yo != 0)) {
                        int idx = (yy * h + zz) * w + xx;
                        if (this.blocks[idx] == 0) {
                            this.blocks[idx] = (byte) Tile.leaves.id;
                        }
                    }
                }
            }
        }

        // Tronco vertical
        for (int hh = 0; hh < treeHeight; hh++) {
            this.blocks[((y + hh) * h + z) * w + x] = (byte) Tile.log.id;
        }
    }
}
