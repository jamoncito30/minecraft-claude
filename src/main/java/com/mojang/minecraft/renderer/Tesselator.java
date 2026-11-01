package com.mojang.minecraft.renderer;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;

/**
 * Empaquetador de vértices para OpenGL (fiel al Tesselator original de Notch).
 * Acumula coordenadas de vértices, UV de texturas, colores RGB y un canal opcional de luz
 * (cielo, bloque) que viaja en el atributo de normal y que lee el shader del mundo.
 * Lo envía de una sola vez a la tarjeta gráfica con glDrawArrays(GL_QUADS).
 */
public class Tesselator {
    private static final int MAX_MEMORY_USE = 4194304;
    private static final int MAX_FLOATS = MAX_MEMORY_USE / 4;

    private final FloatBuffer buffer = BufferUtils.createFloatBuffer(MAX_FLOATS);
    private final float[] array = new float[MAX_FLOATS];

    private int vertices = 0;
    private float u;
    private float v;
    private float r;
    private float g;
    private float b;
    private float skyLight;
    private float blockLight;

    private boolean hasColor = false;
    private boolean hasTexture = false;
    private boolean hasLight = false;
    private int len = 3;
    private int p = 0;
    private boolean noColor = false;

    public static final Tesselator instance = new Tesselator();

    private Tesselator() {
    }

    public void begin() {
        this.clear();
        this.hasTexture = false;
        this.hasColor = false;
        this.hasLight = false;
        this.noColor = false;
        // Antes 'len' nunca se reiniciaba: crecía en cada begin() y el umbral de vaciado acababa
        // siendo negativo, forzando un glDrawArrays por cada quad (el juego se ralentizaba con el tiempo).
        this.len = 3;
    }

    public void end() {
        if (this.vertices > 0) {
            this.buffer.clear();
            this.buffer.put(this.array, 0, this.p);

            int stride = this.len * 4;
            int offset = 0;
            if (this.hasTexture) {
                this.buffer.position(offset);
                glTexCoordPointer(2, GL_FLOAT, stride, this.buffer);
                glEnableClientState(GL_TEXTURE_COORD_ARRAY);
                offset += 2;
            }
            if (this.hasColor) {
                this.buffer.position(offset);
                glColorPointer(3, GL_FLOAT, stride, this.buffer);
                glEnableClientState(GL_COLOR_ARRAY);
                offset += 3;
            }
            if (this.hasLight) {
                this.buffer.position(offset);
                glNormalPointer(GL_FLOAT, stride, this.buffer);
                glEnableClientState(GL_NORMAL_ARRAY);
                offset += 3;
            }
            this.buffer.position(offset);
            glVertexPointer(3, GL_FLOAT, stride, this.buffer);
            glEnableClientState(GL_VERTEX_ARRAY);

            glDrawArrays(GL_QUADS, 0, this.vertices);

            glDisableClientState(GL_VERTEX_ARRAY);
            if (this.hasTexture) {
                glDisableClientState(GL_TEXTURE_COORD_ARRAY);
            }
            if (this.hasColor) {
                glDisableClientState(GL_COLOR_ARRAY);
            }
            if (this.hasLight) {
                glDisableClientState(GL_NORMAL_ARRAY);
            }
        }
        this.clear();
    }

    private void clear() {
        this.vertices = 0;
        this.buffer.clear();
        this.p = 0;
    }

    public void color(float r, float g, float b) {
        if (this.noColor) {
            return;
        }
        if (!this.hasColor) {
            this.len += 3;
        }
        this.hasColor = true;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public void color(int r, int g, int b) {
        this.color(r / 255.0f, g / 255.0f, b / 255.0f);
    }

    /**
     * Luz por vértice normalizada (0..1): luz del cielo y luz de bloque (faroles, lava).
     */
    public void light(float sky, float block) {
        if (!this.hasLight) {
            this.len += 3;
        }
        this.hasLight = true;
        this.skyLight = sky;
        this.blockLight = block;
    }

    public void tex(float u, float v) {
        if (!this.hasTexture) {
            this.len += 2;
        }
        this.hasTexture = true;
        this.u = u;
        this.v = v;
    }

    public void vertexUV(float x, float y, float z, float u, float v) {
        this.tex(u, v);
        this.vertex(x, y, z);
    }

    public void vertex(float x, float y, float z) {
        if (this.hasTexture) {
            this.array[this.p++] = this.u;
            this.array[this.p++] = this.v;
        }
        if (this.hasColor) {
            this.array[this.p++] = this.r;
            this.array[this.p++] = this.g;
            this.array[this.p++] = this.b;
        }
        if (this.hasLight) {
            this.array[this.p++] = this.skyLight;
            this.array[this.p++] = this.blockLight;
            this.array[this.p++] = 0.0f;
        }
        this.array[this.p++] = x;
        this.array[this.p++] = y;
        this.array[this.p++] = z;
        this.vertices++;

        if (this.vertices % 4 == 0 && this.p >= MAX_FLOATS - this.len * 4) {
            this.end();
        }
    }

    public void noColor() {
        this.noColor = true;
    }
}
