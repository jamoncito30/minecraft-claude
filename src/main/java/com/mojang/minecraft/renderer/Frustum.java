package com.mojang.minecraft.renderer;

import com.mojang.minecraft.phys.AABB;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;

/**
 * Frustum Culling de 6 planos (Derecha, Izquierda, Abajo, Arriba, Atrás, Frente).
 * Extrae las matrices GL_PROJECTION y GL_MODELVIEW para descartar Chunks fuera de la cámara.
 */
public class Frustum {
    public final float[][] m_Frustum = new float[6][4];

    public static final int RIGHT = 0;
    public static final int LEFT = 1;
    public static final int BOTTOM = 2;
    public static final int TOP = 3;
    public static final int BACK = 4;
    public static final int FRONT = 5;

    public static final int A = 0;
    public static final int B = 1;
    public static final int C = 2;
    public static final int D = 3;

    private static final Frustum frustum = new Frustum();

    private final FloatBuffer _proj = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer _modl = BufferUtils.createFloatBuffer(16);
    private final float[] proj = new float[16];
    private final float[] modl = new float[16];
    private final float[] clip = new float[16];

    private Frustum() {
    }

    public static Frustum getFrustum() {
        frustum.calculateFrustum();
        return frustum;
    }

    private void normalizePlane(float[][] frustum, int side) {
        float magnitude = (float) Math.sqrt(
                frustum[side][A] * frustum[side][A] +
                frustum[side][B] * frustum[side][B] +
                frustum[side][C] * frustum[side][C]
        );
        frustum[side][A] /= magnitude;
        frustum[side][B] /= magnitude;
        frustum[side][C] /= magnitude;
        frustum[side][D] /= magnitude;
    }

    private void calculateFrustum() {
        this._proj.clear();
        this._modl.clear();

        glGetFloatv(GL_PROJECTION_MATRIX, this._proj);
        glGetFloatv(GL_MODELVIEW_MATRIX, this._modl);

        this._proj.flip().limit(16);
        this._proj.get(this.proj);
        this._modl.flip().limit(16);
        this._modl.get(this.modl);

        this.clip[0] = this.modl[0] * this.proj[0] + this.modl[1] * this.proj[4] + this.modl[2] * this.proj[8] + this.modl[3] * this.proj[12];
        this.clip[1] = this.modl[0] * this.proj[1] + this.modl[1] * this.proj[5] + this.modl[2] * this.proj[9] + this.modl[3] * this.proj[13];
        this.clip[2] = this.modl[0] * this.proj[2] + this.modl[1] * this.proj[6] + this.modl[2] * this.proj[10] + this.modl[3] * this.proj[14];
        this.clip[3] = this.modl[0] * this.proj[3] + this.modl[1] * this.proj[7] + this.modl[2] * this.proj[11] + this.modl[3] * this.proj[15];

        this.clip[4] = this.modl[4] * this.proj[0] + this.modl[5] * this.proj[4] + this.modl[6] * this.proj[8] + this.modl[7] * this.proj[12];
        this.clip[5] = this.modl[4] * this.proj[1] + this.modl[5] * this.proj[5] + this.modl[6] * this.proj[9] + this.modl[7] * this.proj[13];
        this.clip[6] = this.modl[4] * this.proj[2] + this.modl[5] * this.proj[6] + this.modl[6] * this.proj[10] + this.modl[7] * this.proj[14];
        this.clip[7] = this.modl[4] * this.proj[3] + this.modl[5] * this.proj[7] + this.modl[6] * this.proj[11] + this.modl[7] * this.proj[15];

        this.clip[8] = this.modl[8] * this.proj[0] + this.modl[9] * this.proj[4] + this.modl[10] * this.proj[8] + this.modl[11] * this.proj[12];
        this.clip[9] = this.modl[8] * this.proj[1] + this.modl[9] * this.proj[5] + this.modl[10] * this.proj[9] + this.modl[11] * this.proj[13];
        this.clip[10] = this.modl[8] * this.proj[2] + this.modl[9] * this.proj[6] + this.modl[10] * this.proj[10] + this.modl[11] * this.proj[14];
        this.clip[11] = this.modl[8] * this.proj[3] + this.modl[9] * this.proj[7] + this.modl[10] * this.proj[11] + this.modl[11] * this.proj[15];

        this.clip[12] = this.modl[12] * this.proj[0] + this.modl[13] * this.proj[4] + this.modl[14] * this.proj[8] + this.modl[15] * this.proj[12];
        this.clip[13] = this.modl[12] * this.proj[1] + this.modl[13] * this.proj[5] + this.modl[14] * this.proj[9] + this.modl[15] * this.proj[13];
        this.clip[14] = this.modl[12] * this.proj[2] + this.modl[13] * this.proj[6] + this.modl[14] * this.proj[10] + this.modl[15] * this.proj[14];
        this.clip[15] = this.modl[12] * this.proj[3] + this.modl[13] * this.proj[7] + this.modl[14] * this.proj[11] + this.modl[15] * this.proj[15];

        this.m_Frustum[RIGHT][A] = this.clip[3] - this.clip[0];
        this.m_Frustum[RIGHT][B] = this.clip[7] - this.clip[4];
        this.m_Frustum[RIGHT][C] = this.clip[11] - this.clip[8];
        this.m_Frustum[RIGHT][D] = this.clip[15] - this.clip[12];
        this.normalizePlane(this.m_Frustum, RIGHT);

        this.m_Frustum[LEFT][A] = this.clip[3] + this.clip[0];
        this.m_Frustum[LEFT][B] = this.clip[7] + this.clip[4];
        this.m_Frustum[LEFT][C] = this.clip[11] + this.clip[8];
        this.m_Frustum[LEFT][D] = this.clip[15] + this.clip[12];
        this.normalizePlane(this.m_Frustum, LEFT);

        this.m_Frustum[BOTTOM][A] = this.clip[3] + this.clip[1];
        this.m_Frustum[BOTTOM][B] = this.clip[7] + this.clip[5];
        this.m_Frustum[BOTTOM][C] = this.clip[11] + this.clip[9];
        this.m_Frustum[BOTTOM][D] = this.clip[15] + this.clip[13];
        this.normalizePlane(this.m_Frustum, BOTTOM);

        this.m_Frustum[TOP][A] = this.clip[3] - this.clip[1];
        this.m_Frustum[TOP][B] = this.clip[7] - this.clip[5];
        this.m_Frustum[TOP][C] = this.clip[11] - this.clip[9];
        this.m_Frustum[TOP][D] = this.clip[15] - this.clip[13];
        this.normalizePlane(this.m_Frustum, TOP);

        this.m_Frustum[BACK][A] = this.clip[3] - this.clip[2];
        this.m_Frustum[BACK][B] = this.clip[7] - this.clip[6];
        this.m_Frustum[BACK][C] = this.clip[11] - this.clip[10];
        this.m_Frustum[BACK][D] = this.clip[15] - this.clip[14];
        this.normalizePlane(this.m_Frustum, BACK);

        this.m_Frustum[FRONT][A] = this.clip[3] + this.clip[2];
        this.m_Frustum[FRONT][B] = this.clip[7] + this.clip[6];
        this.m_Frustum[FRONT][C] = this.clip[11] + this.clip[10];
        this.m_Frustum[FRONT][D] = this.clip[15] + this.clip[14];
        this.normalizePlane(this.m_Frustum, FRONT);
    }

    public boolean cubeInFrustum(float x1, float y1, float z1, float x2, float y2, float z2) {
        for (int i = 0; i < 6; i++) {
            if (this.m_Frustum[i][A] * x1 + this.m_Frustum[i][B] * y1 + this.m_Frustum[i][C] * z1 + this.m_Frustum[i][D] > 0.0f) continue;
            if (this.m_Frustum[i][A] * x2 + this.m_Frustum[i][B] * y1 + this.m_Frustum[i][C] * z1 + this.m_Frustum[i][D] > 0.0f) continue;
            if (this.m_Frustum[i][A] * x1 + this.m_Frustum[i][B] * y2 + this.m_Frustum[i][C] * z1 + this.m_Frustum[i][D] > 0.0f) continue;
            if (this.m_Frustum[i][A] * x2 + this.m_Frustum[i][B] * y2 + this.m_Frustum[i][C] * z1 + this.m_Frustum[i][D] > 0.0f) continue;
            if (this.m_Frustum[i][A] * x1 + this.m_Frustum[i][B] * y1 + this.m_Frustum[i][C] * z2 + this.m_Frustum[i][D] > 0.0f) continue;
            if (this.m_Frustum[i][A] * x2 + this.m_Frustum[i][B] * y1 + this.m_Frustum[i][C] * z2 + this.m_Frustum[i][D] > 0.0f) continue;
            if (this.m_Frustum[i][A] * x1 + this.m_Frustum[i][B] * y2 + this.m_Frustum[i][C] * z2 + this.m_Frustum[i][D] > 0.0f) continue;
            if (this.m_Frustum[i][A] * x2 + this.m_Frustum[i][B] * y2 + this.m_Frustum[i][C] * z2 + this.m_Frustum[i][D] > 0.0f) continue;
            return false;
        }
        return true;
    }

    public boolean isVisible(AABB aabb) {
        return this.cubeInFrustum(aabb.x0, aabb.y0, aabb.z0, aabb.x1, aabb.y1, aabb.z1);
    }
}
