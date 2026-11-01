package com.mojang.minecraft.renderer;

import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;

/**
 * Gestor de texturas OpenGL con filtrado GL_NEAREST (pixel-art puro).
 * Si no encuentra terrain.png en resources, genera automáticamente el atlas clásico 256x256
 * y lo guarda en src/main/resources/terrain.png.
 */
public class Textures {
    private final Map<String, Integer> idMap = new HashMap<>();

    public int loadTexture(String resourceName, int mode) {
        if (this.idMap.containsKey(resourceName)) {
            return this.idMap.get(resourceName);
        }

        BufferedImage img = null;
        if (resourceName.equals("/terrain.png")) {
            // El atlas se genera siempre por código (determinista), así los bloques nuevos
            // aparecen aunque exista un terrain.png antiguo en resources.
            img = generateClassicTerrainAtlas();
            saveGeneratedAtlas(img, resourceName);
        }
        if (img == null) try {
            InputStream in = Textures.class.getResourceAsStream(resourceName);
            if (in != null) {
                img = ImageIO.read(in);
                in.close();
            } else {
                File resFile = new File("src/main/resources" + resourceName);
                if (resFile.exists()) {
                    img = ImageIO.read(resFile);
                }
            }
        } catch (IOException ignored) {
        }

        if (img == null) {
            img = generateClassicTerrainAtlas();
            saveGeneratedAtlas(img, resourceName);
        }

        IntBuffer ib = BufferUtils.createIntBuffer(1);
        glGenTextures(ib);
        int id = ib.get(0);
        upload(id, img, mode, false);
        this.idMap.put(resourceName, id);
        return id;
    }

    /**
     * Textura de nubes 64x64 con alfa (cada texel cubre varios bloques en el cielo), repetible en mosaico.
     */
    public int getCloudTexture() {
        Integer cached = this.idMap.get("#clouds");
        if (cached != null) {
            return cached;
        }
        int size = 64;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Random rand = new Random(2024L);
        float[][] grid = new float[size][size];
        // Ruido por suma de "manchas" periódicas: siempre encaja en mosaico
        for (int octave = 0; octave < 3; octave++) {
            int cells = 4 << octave;
            float[][] lattice = new float[cells][cells];
            for (int i = 0; i < cells; i++) {
                for (int j = 0; j < cells; j++) {
                    lattice[i][j] = rand.nextFloat();
                }
            }
            float amp = 1.0f / (1 << octave);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    float fx = (float) x * cells / size;
                    float fy = (float) y * cells / size;
                    int x0 = (int) fx;
                    int y0 = (int) fy;
                    float tx = fx - x0;
                    float ty = fy - y0;
                    tx = tx * tx * (3 - 2 * tx);
                    ty = ty * ty * (3 - 2 * ty);
                    float a = lattice[x0 % cells][y0 % cells];
                    float b = lattice[(x0 + 1) % cells][y0 % cells];
                    float c = lattice[x0 % cells][(y0 + 1) % cells];
                    float d = lattice[(x0 + 1) % cells][(y0 + 1) % cells];
                    grid[x][y] += (a + (b - a) * tx + (c - a) * ty + (a - b - c + d) * tx * ty) * amp;
                }
            }
        }
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                img.setRGB(x, y, grid[x][y] > 1.0f ? 0xE8FFFFFF : 0x00FFFFFF);
            }
        }
        IntBuffer ib = BufferUtils.createIntBuffer(1);
        glGenTextures(ib);
        int id = ib.get(0);
        upload(id, img, GL_NEAREST, true);
        this.idMap.put("#clouds", id);
        return id;
    }

    private static void upload(int id, BufferedImage img, int mode, boolean repeat) {
        glBindTexture(GL_TEXTURE_2D, id);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, mode);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, mode);
        if (repeat) {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        }

        int w = img.getWidth();
        int h = img.getHeight();
        int[] rawPixels = new int[w * h];
        img.getRGB(0, 0, w, h, rawPixels, 0, w);

        ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
        for (int i = 0; i < rawPixels.length; i++) {
            int a = (rawPixels[i] >> 24) & 0xFF;
            int r = (rawPixels[i] >> 16) & 0xFF;
            int g = (rawPixels[i] >> 8) & 0xFF;
            int b = rawPixels[i] & 0xFF;

            pixels.put((byte) r);
            pixels.put((byte) g);
            pixels.put((byte) b);
            pixels.put((byte) a);
        }
        pixels.flip();

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    }

    private void saveGeneratedAtlas(BufferedImage img, String resourceName) {
        try {
            File outFile = new File("src/main/resources" + resourceName);
            outFile.getParentFile().mkdirs();
            ImageIO.write(img, "PNG", outFile);
        } catch (IOException ignored) {
        }
    }

    /**
     * Genera el atlas terrain.png (256x256, 16x16 celdas de 16x16 píxeles)
     * con la paleta y patrones pixel-art fieles a Minecraft Classic 0.0.11a / 0.0.14a.
     */
    public static BufferedImage generateClassicTerrainAtlas() {
        BufferedImage atlas = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Random rand = new Random(132211L);

        // 0: Grass Top (Césped superior verde clásico)
        drawTile(atlas, 0, (x, y) -> {
            int n = rand.nextInt(18) - 9;
            return rgb(92 + n, 158 + n, 52 + n);
        });

        // 1: Stone / Rock (Piedra lisa con estrías horizontales sutiles)
        drawTile(atlas, 1, (x, y) -> {
            int base = 122 + ((x * 7 + y * 13) % 9 - 4);
            if ((y % 4 == 0 && (x + y) % 5 < 3)) base -= 14;
            if ((y % 5 == 2 && (x * 3) % 7 < 2)) base += 10;
            int n = rand.nextInt(10) - 5;
            int v = clamp(base + n);
            return rgb(v, v, v);
        });

        // 2: Dirt (Tierra marrón moteada)
        int[][] dirtTile = new int[16][16];
        drawTile(atlas, 2, (x, y) -> {
            int n = rand.nextInt(22) - 11;
            int r = 134 + n;
            int g = 96 + (int) (n * 0.75f);
            int b = 67 + (int) (n * 0.55f);
            if (rand.nextInt(9) == 0) {
                r -= 18;
                g -= 15;
                b -= 12;
            }
            int col = rgb(r, g, b);
            dirtTile[x][y] = col;
            return col;
        });

        // 3: Grass Side (Tierra con borde superior de césped irregular)
        int[] grassHang = {3, 4, 3, 2, 4, 5, 3, 2, 3, 4, 6, 4, 3, 2, 4, 3};
        drawTile(atlas, 3, (x, y) -> {
            if (y < grassHang[x]) {
                int n = rand.nextInt(16) - 8;
                int shade = (y == grassHang[x] - 1) ? -12 : 0;
                return rgb(90 + n + shade, 155 + n + shade, 50 + n + shade);
            }
            return dirtTile[x][y];
        });

        // 4: Wood Planks (Tablones de madera clásicos)
        drawTile(atlas, 4, (x, y) -> {
            int plankRow = y / 4;
            boolean seamH = (y % 4 == 0);
            boolean seamV = ((x + plankRow * 7) % 16 == 0);
            int grain = (int) (Math.sin((x + plankRow * 3) * 0.6) * 6) + rand.nextInt(8) - 4;
            int r = 162 + grain;
            int g = 131 + grain;
            int b = 79 + grain;
            if (seamH || seamV) {
                r -= 32;
                g -= 28;
                b -= 20;
            }
            return rgb(r, g, b);
        });

        // 14: Water (Agua azul translúcida)
        drawTile(atlas, 14, (x, y) -> {
            int wave = ((x + y * 2) % 7 == 0) ? 20 : 0;
            int n = rand.nextInt(10) - 5 + wave;
            return rgba(38 + n, 92 + n, 255, 195);
        });

        // 15: Sapling / Bush (Retoño clásico con transparencia)
        drawTile(atlas, 15, (x, y) -> {
            // Tronco pequeño abajo
            if (y >= 10 && y <= 15 && (x == 7 || x == 8)) {
                return rgb(105, 78, 42);
            }
            // Copa de hojas pixelada
            int dx = x - 7;
            int dy = y - 6;
            int dist2 = dx * dx + dy * dy;
            if (dist2 <= 22 && (x + y * 3) % 4 != 0) {
                int n = rand.nextInt(26) - 13;
                return rgb(78 + n, 168 + n, 44 + n);
            }
            return 0x00000000;
        });

        // 16: Cobblestone (Adoquín clásico de alto contraste estilo rd-132211)
        int[][] cobblePattern = {
                {1,1,1,0,2,2,2,2,0,1,1,1,1,0,2,1},
                {1,2,1,0,2,3,3,2,0,1,2,2,1,0,2,1},
                {1,1,1,0,2,2,2,2,0,1,1,1,1,0,1,1},
                {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
                {2,2,0,1,1,1,1,1,0,2,2,2,0,1,1,2},
                {2,3,0,1,2,3,2,1,0,2,3,2,0,1,2,2},
                {2,2,0,1,1,2,1,1,0,2,2,2,0,1,1,2},
                {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
                {1,1,1,1,0,2,2,2,2,0,1,1,1,0,2,2},
                {1,2,3,1,0,2,3,3,2,0,1,2,1,0,2,3},
                {1,2,2,1,0,2,2,2,2,0,1,1,1,0,2,2},
                {1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0},
                {0,0,0,0,2,2,2,0,1,1,1,1,0,2,2,2},
                {2,2,2,0,2,3,2,0,1,2,3,1,0,2,3,2},
                {2,3,2,0,2,2,2,0,1,1,1,1,0,2,2,2},
                {0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0}
        };
        drawTile(atlas, 16, (x, y) -> {
            int p = cobblePattern[y][x];
            int base = switch (p) {
                case 0 -> 68;
                case 1 -> 112;
                case 2 -> 136;
                default -> 158;
            };
            int v = clamp(base + rand.nextInt(14) - 7);
            return rgb(v, v, v);
        });

        // 17: Bedrock (Roca madre indestructible de alto contraste)
        drawTile(atlas, 17, (x, y) -> {
            int p = cobblePattern[(y + 3) % 16][(x + 5) % 16];
            int base = switch (p) {
                case 0 -> 28;
                case 1 -> 55;
                case 2 -> 85;
                default -> 115;
            };
            int v = clamp(base + rand.nextInt(20) - 10);
            return rgb(v, v, v);
        });

        // 18: Sand (Arena clásica)
        drawTile(atlas, 18, (x, y) -> {
            int n = rand.nextInt(18) - 9;
            return rgb(218 + n, 210 + n, 158 + n);
        });

        // 19: Gravel (Grava clásica)
        drawTile(atlas, 19, (x, y) -> {
            int n = rand.nextInt(34) - 17;
            return rgb(136 + n, 130 + n, 128 + n);
        });

        // 20: Log Side (Corteza vertical de tronco)
        drawTile(atlas, 20, (x, y) -> {
            int stripe = ((x % 4 == 0) ? -14 : (x % 4 == 2 ? 8 : 0));
            int n = rand.nextInt(14) - 7 + stripe;
            return rgb(104 + n, 82 + n, 50 + n);
        });

        // 21: Log Top (Anillos de tronco)
        drawTile(atlas, 21, (x, y) -> {
            if (x == 0 || x == 15 || y == 0 || y == 15) {
                int n = rand.nextInt(12) - 6;
                return rgb(102 + n, 80 + n, 48 + n);
            }
            int dist = Math.max(Math.abs(x - 7), Math.abs(y - 7));
            int ring = (dist % 2 == 0) ? 10 : -8;
            int n = rand.nextInt(10) - 5 + ring;
            return rgb(166 + n, 134 + n, 82 + n);
        });

        // 22: Leaves (Hojas de árbol con huecos transparentes)
        drawTile(atlas, 22, (x, y) -> {
            if (((x * 5 + y * 3) % 7 == 0) && rand.nextInt(3) > 0) {
                return 0x00000000;
            }
            int n = rand.nextInt(28) - 14;
            return rgb(62 + n, 148 + n, 38 + n);
        });

        // 30: Lava
        drawTile(atlas, 30, (x, y) -> {
            int wave = (int) (Math.sin(x * 0.5 + y * 0.4) * 25);
            int n = rand.nextInt(16) - 8 + wave;
            return rgb(235 + n, 105 + n, 20);
        });

        // 32: Gold Ore, 33: Iron Ore, 34: Coal Ore
        drawOreTile(atlas, 32, rand, 245, 220, 60);
        drawOreTile(atlas, 33, rand, 215, 180, 155);
        drawOreTile(atlas, 34, rand, 38, 38, 38);

        // ===== Bloques nuevos =====

        // 5: Vidrio (marco claro con reflejos diagonales y centro transparente)
        drawTile(atlas, 5, (x, y) -> {
            boolean edge = x == 0 || y == 0 || x == 15 || y == 15;
            if (edge) {
                return rgba(214, 236, 242, 255);
            }
            boolean shine = (x - y == 3 || x - y == 4 || x - y == -6) && x > 1 && x < 14;
            if (shine) {
                return rgba(240, 252, 255, 200);
            }
            return 0x00000000;
        });

        // 6: Ladrillos (hileras alternas con argamasa)
        drawTile(atlas, 6, (x, y) -> {
            int row = y / 4;
            boolean mortarH = y % 4 == 3;
            boolean mortarV = ((x + (row % 2) * 4) % 8) == 7;
            if (mortarH || mortarV) {
                int n = rand.nextInt(10) - 5;
                return rgb(178 + n, 170 + n, 160 + n);
            }
            int n = rand.nextInt(20) - 10;
            int shade = (y % 4 == 0) ? 12 : 0;
            return rgb(150 + n + shade, 62 + n / 2 + shade / 2, 48 + n / 2);
        });

        // 7: Piedra pulida (losa lisa con bisel)
        drawTile(atlas, 7, (x, y) -> {
            int n = rand.nextInt(6) - 3;
            if (x == 0 || y == 0) return rgb(178 + n, 178 + n, 182 + n);
            if (x == 15 || y == 15) return rgb(96 + n, 96 + n, 100 + n);
            if (x == 1 || y == 1 || x == 14 || y == 14) return rgb(150 + n, 150 + n, 154 + n);
            return rgb(140 + n, 140 + n, 144 + n);
        });

        // 8: Farol (cristal ámbar brillante dentro de un marco de hierro)
        drawTile(atlas, 8, (x, y) -> {
            boolean frame = x <= 1 || y <= 1 || x >= 14 || y >= 14 || x == 7 || x == 8 || y == 7 || y == 8;
            if (frame) {
                int n = rand.nextInt(10) - 5;
                boolean bolt = (x <= 1 || x >= 14) && (y <= 1 || y >= 14);
                return bolt ? rgb(110 + n, 106 + n, 98 + n) : rgb(62 + n, 58 + n, 54 + n);
            }
            int cx = x < 8 ? 4 : 11;
            int cy = y < 8 ? 4 : 11;
            int dist = Math.abs(x - cx) + Math.abs(y - cy);
            int n = rand.nextInt(14) - 7;
            return rgb(255, 214 - dist * 12 + n, 120 - dist * 18 + n);
        });

        // 9 y 10: Flores amarilla y roja (tallo verde + pétalos, fondo transparente)
        drawFlowerTile(atlas, 9, rand, 250, 214, 40);
        drawFlowerTile(atlas, 10, rand, 222, 38, 44);

        return atlas;
    }

    private static void drawOreTile(BufferedImage atlas, int tileIndex, Random rand, int oreR, int oreG, int oreB) {
        boolean[][] spots = new boolean[16][16];
        int[][] coords = {{3, 4}, {4, 4}, {4, 5}, {10, 3}, {11, 3}, {10, 4}, {6, 10}, {7, 10}, {7, 11}, {12, 11}, {11, 12}};
        for (int[] c : coords) {
            spots[c[0]][c[1]] = true;
        }
        drawTile(atlas, tileIndex, (x, y) -> {
            if (spots[x][y]) {
                int n = rand.nextInt(16) - 8;
                return rgb(oreR + n, oreG + n, oreB + n);
            }
            int base = 122 + ((x * 7 + y * 13) % 9 - 4) + rand.nextInt(10) - 5;
            return rgb(base, base, base);
        });
    }

    private static void drawFlowerTile(BufferedImage atlas, int tileIndex, Random rand, int pr, int pg, int pb) {
        drawTile(atlas, tileIndex, (x, y) -> {
            if (x == 7 && y >= 7) {
                return rgb(52, 128 + rand.nextInt(20), 36);
            }
            if ((y == 11 && x == 6) || (y == 12 && x == 5) || (y == 10 && x == 8) || (y == 9 && x == 9)) {
                return rgb(66, 150, 44);
            }
            int dx = x - 7;
            int dy = y - 5;
            if (dx * dx + dy * dy <= 2) {
                return rgb(255, 238, 120);
            }
            if ((Math.abs(dx) <= 1 && Math.abs(dy) <= 3) || (Math.abs(dy) <= 1 && Math.abs(dx) <= 3)) {
                int n = rand.nextInt(20) - 10;
                return rgb(pr + n, pg + n, pb + n);
            }
            return 0x00000000;
        });
    }

    @FunctionalInterface
    private interface PixelShader {
        int getColor(int x, int y);
    }

    private static void drawTile(BufferedImage atlas, int index, PixelShader shader) {
        int tx = (index % 16) * 16;
        int ty = (index / 16) * 16;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                atlas.setRGB(tx + x, ty + y, shader.getColor(x, y));
            }
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static int rgb(int r, int g, int b) {
        return rgba(r, g, b, 255);
    }

    private static int rgba(int r, int g, int b, int a) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }
}
