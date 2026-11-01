package com.mojang.minecraft.renderer;

import org.lwjgl.BufferUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;

/**
 * Renderizador de fuente bitmap pixel-art para el HUD de Minecraft Classic 0.0.14a.
 * Soporta texto con sombra (drawShadow) igual que el original.
 */
public class Font {
    private final int[] charWidths = new int[256];
    private final int fontTexture;

    public Font() {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.BOLD, 10));
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setColor(Color.WHITE);

        FontMetrics fm = g.getFontMetrics();
        // ASCII + Latin-1 (á, é, ñ, ¿, ¡...) para poder escribir el HUD en español
        for (int i = 32; i < 256; i++) {
            if (i >= 127 && i < 160) continue;
            char c = (char) i;
            int col = i % 16;
            int row = i / 16;
            // Recortar cada glifo a su celda: si no, tildes y descendentes invaden las letras vecinas
            g.setClip(col * 8, row * 8, 8, 8);
            g.drawString(String.valueOf(c), col * 8, row * 8 + 7);
            this.charWidths[i] = Math.max(4, Math.min(8, fm.charWidth(c)));
        }
        g.dispose();

        int w = img.getWidth();
        int h = img.getHeight();
        int[] rawPixels = new int[w * h];
        img.getRGB(0, 0, w, h, rawPixels, 0, w);

        ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
        for (int p : rawPixels) {
            int a = (p >> 24) & 0xFF;
            int r = (p >> 16) & 0xFF;
            int gr = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            pixels.put((byte) r).put((byte) gr).put((byte) b).put((byte) a);
        }
        pixels.flip();

        IntBuffer ib = BufferUtils.createIntBuffer(1);
        glGenTextures(ib);
        this.fontTexture = ib.get(0);

        glBindTexture(GL_TEXTURE_2D, this.fontTexture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    }

    public void drawShadow(String str, int x, int y, int color) {
        this.draw(str, x + 1, y + 1, color, true);
        this.draw(str, x, y, color, false);
    }

    public void draw(String str, int x, int y, int color, boolean darken) {
        if (str == null || str.isEmpty()) return;

        if (darken) {
            color = (color & 0xFCFCFC) >> 2;
        }

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, this.fontTexture);
        Tesselator t = Tesselator.instance;
        t.begin();
        t.color(r, g, b);

        int xo = 0;
        for (int i = 0; i < str.length(); i++) {
            int ch = str.charAt(i);
            if (ch < 32 || ch >= 256 || (ch >= 127 && ch < 160)) ch = '?';

            int ix = (ch % 16) * 8;
            int iy = (ch / 16) * 8;
            float u0 = ix / 128.0f;
            float v0 = iy / 128.0f;
            float u1 = (ix + 7.99f) / 128.0f;
            float v1 = (iy + 7.99f) / 128.0f;

            t.vertexUV(x + xo, y + 8, 0.0f, u0, v1);
            t.vertexUV(x + xo + 8, y + 8, 0.0f, u1, v1);
            t.vertexUV(x + xo + 8, y, 0.0f, u1, v0);
            t.vertexUV(x + xo, y, 0.0f, u0, v0);

            xo += this.charWidths[ch];
        }
        t.end();
    }

    public int width(String str) {
        if (str == null) return 0;
        int len = 0;
        for (int i = 0; i < str.length(); i++) {
            int ch = str.charAt(i);
            if (ch < 32 || ch >= 256 || (ch >= 127 && ch < 160)) ch = '?';
            len += this.charWidths[ch];
        }
        return len;
    }
}
