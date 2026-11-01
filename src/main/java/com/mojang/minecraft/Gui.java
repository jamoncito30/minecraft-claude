package com.mojang.minecraft;

import com.mojang.minecraft.level.tile.Tile;
import com.mojang.minecraft.renderer.Font;
import com.mojang.minecraft.renderer.SkyRenderer;
import com.mojang.minecraft.renderer.Tesselator;
import com.mojang.minecraft.renderer.Textures;

import static org.lwjgl.opengl.GL11.*;

/**
 * Interfaz gráfica (HUD):
 * - Versión, FPS, reloj del día y (con F3) información de depuración
 * - Bloque activo renderizado en 3D en la esquina superior derecha
 * - Barra de selección rápida (1-9) personalizable
 * - Inventario de bloques (E), pantalla de pausa y mensajes emergentes
 * - Mira central (+)
 */
public class Gui {
    /** Barra rápida inicial (se puede cambiar desde el inventario y se guarda con el mundo). */
    public static final int[] DEFAULT_HOTBAR = {
            Tile.rock.id,
            Tile.stoneBrick.id,
            Tile.dirt.id,
            Tile.grass.id,
            Tile.wood.id,
            Tile.log.id,
            Tile.glass.id,
            Tile.brick.id,
            Tile.lamp.id
    };

    /** Todos los bloques que se pueden elegir en el inventario. */
    public static final int[] ALL_TILES = {
            Tile.rock.id, Tile.stoneBrick.id, Tile.polishedStone.id, Tile.brick.id, Tile.dirt.id,
            Tile.grass.id, Tile.sand.id, Tile.gravel.id, Tile.wood.id, Tile.log.id,
            Tile.leaves.id, Tile.glass.id, Tile.lamp.id, Tile.bush.id, Tile.yellowFlower.id,
            Tile.redFlower.id, Tile.coalOre.id, Tile.ironOre.id, Tile.goldOre.id, Tile.water.id,
            Tile.lava.id
    };

    private static final int INV_COLS = 7;
    private static final int INV_CELL = 22;

    private final Font font;
    private final Textures textures;

    private String message = null;
    private long messageTime = 0L;

    public Gui(Font font, Textures textures) {
        this.font = font;
        this.textures = textures;
    }

    public void showMessage(String text) {
        this.message = text;
        this.messageTime = System.currentTimeMillis();
    }

    public static int guiWidth(int width, int height) {
        return width * 240 / height;
    }

    public static boolean isPlaceable(int id) {
        for (int t : ALL_TILES) {
            if (t == id) return true;
        }
        return false;
    }

    private int invRows() {
        return (ALL_TILES.length + INV_COLS - 1) / INV_COLS;
    }

    private int invX(int screenWidth) {
        return (screenWidth - INV_COLS * INV_CELL) / 2;
    }

    private int invY(int screenHeight) {
        return (screenHeight - this.invRows() * INV_CELL) / 2;
    }

    /** Índice en ALL_TILES bajo el puntero (coordenadas de GUI) o -1. */
    public int inventorySlotAt(int screenWidth, int screenHeight, float mx, float my) {
        int x0 = this.invX(screenWidth);
        int y0 = this.invY(screenHeight);
        if (mx < x0 || my < y0) return -1;
        int col = (int) ((mx - x0) / INV_CELL);
        int row = (int) ((my - y0) / INV_CELL);
        if (col >= INV_COLS || row >= this.invRows()) return -1;
        int idx = row * INV_COLS + col;
        return idx < ALL_TILES.length ? idx : -1;
    }

    public void render(Minecraft mc, int width, int height) {
        int screenWidth = guiWidth(width, height);
        int screenHeight = 240;
        Player player = mc.player;

        glClear(GL_DEPTH_BUFFER_BIT);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0.0, screenWidth, screenHeight, 0.0, 100.0, 300.0);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glTranslatef(0.0f, 0.0f, -200.0f);
        glDisable(GL_FOG);

        // 1. Tinte si la cámara está bajo el agua o la lava
        if (mc.cameraInLava) {
            this.fill(0, 0, screenWidth, screenHeight, 0x90E05010);
        } else if (player.isUnderWater()) {
            this.fill(0, 0, screenWidth, screenHeight, 0x401A40D8);
        }

        int selectedTileId = mc.hotbar[mc.selectedSlot];

        // 2. Bloque activo en 3D en la esquina superior derecha
        Tile activeTile = Tile.tiles[selectedTileId];
        if (activeTile != null) {
            this.renderTileIcon(activeTile, screenWidth - 20.0f, 20.0f, 16.0f);
            String tileName = activeTile.name;
            this.font.drawShadow(tileName, screenWidth - this.font.width(tileName) - 34, 14, 0xFFFFFF);
        }

        // 3. Barra inferior de bloques (1 a 9)
        int[] hotbar = mc.hotbar;
        int barWidth = hotbar.length * 20 + 2;
        int barX = (screenWidth - barWidth) / 2;
        int barY = screenHeight - 22;

        glDisable(GL_DEPTH_TEST);
        this.fill(barX, barY, barX + barWidth, barY + 20, 0x80000000);
        int selX = barX + mc.selectedSlot * 20;
        this.fill(selX, barY - 1, selX + 22, barY + 21, 0xC0FFFFFF);
        this.fill(selX + 1, barY, selX + 21, barY + 20, 0x80303030);

        for (int i = 0; i < hotbar.length; i++) {
            Tile tile = Tile.tiles[hotbar[i]];
            if (tile == null) continue;
            this.renderTileIcon(tile, barX + i * 20 + 11.0f, barY + 10.0f, 10.0f);
            this.font.drawShadow(String.valueOf(i + 1), barX + i * 20 + 2, barY + 1, 0xE0E0E0);
        }

        // 4. Texto superior izquierdo: versión, FPS y reloj
        this.font.drawShadow(Minecraft.VERSION_STRING, 2, 2, 0xFFFFFF);
        this.font.drawShadow(mc.fpsString, 2, 12, 0xFFFFFF);
        float hours = SkyRenderer.getClockHours(mc.level.time);
        int hh = (int) hours;
        int mm = (int) ((hours - hh) * 60.0f);
        String clock = String.format("Día %d  %02d:%02d", SkyRenderer.getDay(mc.level.time), hh, mm);
        if (player.flying) clock += "  [vuelo]";
        this.font.drawShadow(clock, 2, 22, hours >= 6 && hours < 19 ? 0xFFF4C0 : 0xA0B8FF);

        int textY = 34;
        boolean overlay = mc.inventoryOpen || mc.isPaused();
        if (mc.showDebug && !overlay) {
            int bx = (int) Math.floor(player.x);
            int by = (int) Math.floor(player.y - player.heightOffset);
            int bz = (int) Math.floor(player.z);
            String[] lines = {
                    String.format("XYZ: %.2f / %.2f / %.2f", player.x, player.y - player.heightOffset, player.z),
                    "Bloque: " + bx + " " + by + " " + bz + "   Mirando: " + facing(player.yRot),
                    "Luz cielo: " + mc.level.getSkyLight(bx, by, bz) + "  Luz bloque: " + mc.level.getBlockLight(bx, by, bz)
                            + String.format("  Luz del día: %.0f%%", mc.sky.daylight * 100.0f),
                    "Niebla: " + (512 >> mc.renderDistance) + " bloques" + (player.sprinting ? "   Corriendo" : "")
            };
            for (String line : lines) {
                this.font.drawShadow(line, 2, textY, 0xE0E0E0);
                textY += 10;
            }
            textY += 2;
        }

        if (mc.showHelp && !overlay) {
            String[] help = {
                    "WASD mover, Espacio saltar, Ctrl correr",
                    "V volar (Espacio sube, Shift baja)",
                    "Clic izq/der: romper/colocar (mantener)",
                    "Rueda/1-9 bloque, E inventario",
                    "T adelantar el tiempo, F niebla",
                    "R spawn, Enter fijar spawn, G mundo nuevo",
                    "F3 depuración, F11 pantalla completa",
                    "H ocultar esta ayuda"
            };
            for (String line : help) {
                this.font.drawShadow(line, 2, textY, 0xCCCCCC);
                textY += 10;
            }
        }

        // 5. Mensaje emergente sobre la barra rápida
        if (this.message != null) {
            long age = System.currentTimeMillis() - this.messageTime;
            if (age > 3000L) {
                this.message = null;
            } else {
                int alpha = age < 2200L ? 255 : (int) (255 * (3000L - age) / 800L);
                if (alpha > 8) {
                    int mw = this.font.width(this.message);
                    int mx = (screenWidth - mw) / 2;
                    int my = barY - 22;
                    this.fill(mx - 4, my - 3, mx + mw + 4, my + 10, (alpha / 2) << 24);
                    this.font.drawShadow(this.message, mx, my, 0xFFFFFF);
                }
            }
        }

        if (mc.inventoryOpen) {
            this.renderInventory(mc, screenWidth, screenHeight);
        } else if (mc.isPaused()) {
            this.renderPause(screenWidth, screenHeight);
        } else {
            this.renderCrosshair(screenWidth, screenHeight);
        }

        glEnable(GL_DEPTH_TEST);
    }

    private static String facing(float yRot) {
        float r = ((yRot % 360.0f) + 360.0f) % 360.0f;
        if (r < 45 || r >= 315) return "Norte (-Z)";
        if (r < 135) return "Este (+X)";
        if (r < 225) return "Sur (+Z)";
        return "Oeste (-X)";
    }

    private void renderCrosshair(int screenWidth, int screenHeight) {
        int wc = screenWidth / 2;
        int hc = screenHeight / 2;
        glDisable(GL_TEXTURE_2D);
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        Tesselator t = Tesselator.instance;
        t.begin();
        t.noColor();
        t.vertex(wc + 1, hc - 4, 0.0f);
        t.vertex(wc - 0, hc - 4, 0.0f);
        t.vertex(wc - 0, hc + 5, 0.0f);
        t.vertex(wc + 1, hc + 5, 0.0f);

        t.vertex(wc + 5, hc - 0, 0.0f);
        t.vertex(wc - 4, hc - 0, 0.0f);
        t.vertex(wc - 4, hc + 1, 0.0f);
        t.vertex(wc + 5, hc + 1, 0.0f);
        t.end();
    }

    private void renderPause(int screenWidth, int screenHeight) {
        this.fill(0, 0, screenWidth, screenHeight, 0x90101018);
        String title = "Juego en pausa";
        this.font.drawShadow(title, (screenWidth - this.font.width(title)) / 2, screenHeight / 2 - 24, 0xFFFFFF);
        String l1 = "Haz clic para continuar";
        String l2 = "Pulsa Esc otra vez para guardar y salir";
        this.font.drawShadow(l1, (screenWidth - this.font.width(l1)) / 2, screenHeight / 2 - 4, 0xD0D0D0);
        this.font.drawShadow(l2, (screenWidth - this.font.width(l2)) / 2, screenHeight / 2 + 8, 0xA0A0A0);
    }

    private void renderInventory(Minecraft mc, int screenWidth, int screenHeight) {
        this.fill(0, 0, screenWidth, screenHeight, 0x70000000);
        int x0 = this.invX(screenWidth);
        int y0 = this.invY(screenHeight);
        int rows = this.invRows();
        this.fill(x0 - 6, y0 - 18, x0 + INV_COLS * INV_CELL + 6, y0 + rows * INV_CELL + 18, 0xD0202028);

        String title = "Elige un bloque para la casilla " + (mc.selectedSlot + 1);
        this.font.drawShadow(title, (screenWidth - this.font.width(title)) / 2, y0 - 13, 0xFFFFFF);

        int hovered = this.inventorySlotAt(screenWidth, screenHeight, mc.guiMouseX, mc.guiMouseY);
        for (int i = 0; i < ALL_TILES.length; i++) {
            int cx = x0 + (i % INV_COLS) * INV_CELL;
            int cy = y0 + (i / INV_COLS) * INV_CELL;
            boolean current = ALL_TILES[i] == mc.hotbar[mc.selectedSlot];
            int bg = i == hovered ? 0xA0FFFFFF : (current ? 0x80FFD860 : 0x60000000);
            this.fill(cx + 1, cy + 1, cx + INV_CELL - 1, cy + INV_CELL - 1, bg);
            Tile tile = Tile.tiles[ALL_TILES[i]];
            if (tile != null) {
                this.renderTileIcon(tile, cx + INV_CELL / 2.0f, cy + INV_CELL / 2.0f, 10.0f);
            }
        }
        glDisable(GL_DEPTH_TEST);

        String hint = hovered >= 0 ? Tile.tiles[ALL_TILES[hovered]].name : "Haz clic en un bloque  ·  E / Esc para cerrar";
        this.font.drawShadow(hint, (screenWidth - this.font.width(hint)) / 2, y0 + rows * INV_CELL + 6, hovered >= 0 ? 0xFFF4A0 : 0xB0B0B0);
    }

    private void renderTileIcon(Tile tile, float x, float y, float scale) {
        glPushMatrix();
        glTranslatef(x, y, 50.0f);
        glScalef(scale, -scale, scale);
        glRotatef(-30.0f, 1.0f, 0.0f, 0.0f);
        glRotatef(45.0f, 0.0f, 1.0f, 0.0f);
        glTranslatef(-0.5f, -0.5f, -0.5f);

        int id = this.textures.loadTexture("/terrain.png", GL_NEAREST);
        glBindTexture(GL_TEXTURE_2D, id);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_ALPHA_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        tile.renderGui(Tesselator.instance);
        glDisable(GL_BLEND);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_TEXTURE_2D);
        glPopMatrix();
    }

    private void fill(int x0, int y0, int x1, int y1, int col) {
        float a = ((col >> 24) & 0xFF) / 255.0f;
        float r = ((col >> 16) & 0xFF) / 255.0f;
        float g = ((col >> 8) & 0xFF) / 255.0f;
        float b = (col & 0xFF) / 255.0f;

        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(r, g, b, a);

        Tesselator t = Tesselator.instance;
        t.begin();
        t.noColor();
        t.vertex(x0, y1, 0.0f);
        t.vertex(x1, y1, 0.0f);
        t.vertex(x1, y0, 0.0f);
        t.vertex(x0, y0, 0.0f);
        t.end();

        glDisable(GL_BLEND);
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
