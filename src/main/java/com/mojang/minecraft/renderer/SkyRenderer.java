package com.mojang.minecraft.renderer;

import java.util.Random;

import static org.lwjgl.opengl.GL11.*;

/**
 * Ciclo día/noche: calcula la luz del día y los colores del cielo a partir de la hora,
 * y dibuja la bóveda (cenit + horizonte con niebla), el resplandor del amanecer/atardecer,
 * el sol, la luna, las estrellas y una capa de nubes que se desplaza con el viento.
 */
public class SkyRenderer {
    /** Duración de un día completo en ticks (20 ticks = 1 s → 10 minutos). */
    public static final int DAY_LENGTH = 12000;
    private static final float CELESTIAL_RADIUS = 45.0f;
    public static final float CLOUD_HEIGHT = 72.0f;
    private static final float CLOUD_TEXEL_BLOCKS = 12.0f;

    private final Textures textures;
    private final int starList;

    /** Multiplicador de la luz del cielo (0.16 de noche, 1.0 a mediodía). */
    public float daylight = 1.0f;
    /** Ángulo del sol en radianes (0 = sale por +X, PI/2 = cenit). */
    public float sunAngle = 0.0f;
    public float sunHeight = 0.0f;
    /** Intensidad del tono anaranjado cerca del amanecer o atardecer (0..1). */
    public float sunset = 0.0f;
    private float dayFactor = 1.0f;

    public final float[] zenith = new float[3];
    public final float[] horizon = new float[3];
    public final float[] cloud = new float[3];

    public SkyRenderer(Textures textures) {
        this.textures = textures;
        this.starList = glGenLists(1);
        this.buildStars();
    }

    private void buildStars() {
        Random rand = new Random(10842L);
        glNewList(this.starList, GL_COMPILE);
        glBegin(GL_QUADS);
        for (int i = 0; i < 1500; i++) {
            float x = rand.nextFloat() * 2.0f - 1.0f;
            float y = rand.nextFloat() * 2.0f - 1.0f;
            float z = rand.nextFloat() * 2.0f - 1.0f;
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            if (len < 0.01f || len > 1.0f) continue;
            x /= len;
            y /= len;
            z /= len;
            float size = 0.12f + rand.nextFloat() * 0.18f;
            // Dos vectores perpendiculares a la dirección de la estrella para formar el quad
            float ax, ay, az;
            if (Math.abs(y) < 0.9f) {
                ax = z; ay = 0; az = -x;
            } else {
                ax = 0; ay = -z; az = y;
            }
            float al = (float) Math.sqrt(ax * ax + ay * ay + az * az);
            ax /= al; ay /= al; az /= al;
            float bx = y * az - z * ay;
            float by = z * ax - x * az;
            float bz = x * ay - y * ax;
            float cx = x * CELESTIAL_RADIUS;
            float cy = y * CELESTIAL_RADIUS;
            float cz = z * CELESTIAL_RADIUS;
            glVertex3f(cx - (ax + bx) * size, cy - (ay + by) * size, cz - (az + bz) * size);
            glVertex3f(cx + (ax - bx) * size, cy + (ay - by) * size, cz + (az - bz) * size);
            glVertex3f(cx + (ax + bx) * size, cy + (ay + by) * size, cz + (az + bz) * size);
            glVertex3f(cx - (ax - bx) * size, cy - (ay - by) * size, cz - (az - bz) * size);
        }
        glEnd();
        glEndList();
    }

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }

    private static void mix(float[] out, float r0, float g0, float b0, float r1, float g1, float b1, float t) {
        out[0] = r0 + (r1 - r0) * t;
        out[1] = g0 + (g1 - g0) * t;
        out[2] = b0 + (b1 - b0) * t;
    }

    /** Recalcula luz y colores para la hora actual (time en ticks + fracción interpolada). */
    public void update(long time, float partialTick) {
        float f = ((time % DAY_LENGTH) + partialTick) / DAY_LENGTH;
        this.sunAngle = (float) (f * Math.PI * 2.0);
        this.sunHeight = (float) Math.sin(this.sunAngle);

        float k = clamp01(this.sunHeight * 2.5f + 0.4f);
        k = k * k * (3.0f - 2.0f * k);
        this.dayFactor = k;
        this.daylight = 0.16f + 0.84f * k;

        float s = clamp01(1.0f - Math.abs(this.sunHeight) / 0.35f);
        this.sunset = s * s;

        mix(this.zenith, 0.012f, 0.016f, 0.05f, 0.40f, 0.62f, 1.0f, k);
        mix(this.horizon, 0.035f, 0.045f, 0.10f, 0.72f, 0.84f, 1.0f, k);
        mix(this.horizon, this.horizon[0], this.horizon[1], this.horizon[2], 0.98f, 0.55f, 0.32f, this.sunset * 0.55f);
        mix(this.cloud, 0.10f, 0.11f, 0.15f, 1.0f, 1.0f, 1.0f, k);
        mix(this.cloud, this.cloud[0], this.cloud[1], this.cloud[2], 1.0f, 0.68f, 0.5f, this.sunset * 0.5f);
    }

    /** Hora del reloj del juego (0..24), donde el tick 0 corresponde al amanecer (06:00). */
    public static float getClockHours(long time) {
        float f = (float) (time % DAY_LENGTH) / DAY_LENGTH;
        return (6.0f + f * 24.0f) % 24.0f;
    }

    public static long getDay(long time) {
        return (time + DAY_LENGTH / 4) / DAY_LENGTH + 1;
    }

    /**
     * Dibuja la bóveda celeste alrededor de la cámara. Debe llamarse antes que el mundo,
     * con la matriz de cámara ya aplicada; no escribe en el depth buffer.
     */
    public void renderSky(float cx, float cy, float cz, float far) {
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_ALPHA_TEST);
        glDisable(GL_CULL_FACE);
        glDepthMask(false);

        // 1. Plano del cielo con niebla: el cenit se funde con el color del horizonte a lo lejos
        FogHelper.linear(this.horizon, 0.0f, far * 0.8f);
        glColor3f(this.zenith[0], this.zenith[1], this.zenith[2]);
        float y = cy + 16.0f;
        glBegin(GL_QUADS);
        glVertex3f(cx - far, y, cz - far);
        glVertex3f(cx + far, y, cz - far);
        glVertex3f(cx + far, y, cz + far);
        glVertex3f(cx - far, y, cz + far);
        glEnd();
        glDisable(GL_FOG);

        glEnable(GL_BLEND);
        glPushMatrix();
        glTranslatef(cx, cy, cz);

        // 2. Resplandor del amanecer / atardecer en el horizonte donde está el sol
        if (this.sunset > 0.01f) {
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            float dir = Math.cos(this.sunAngle) >= 0 ? 1.0f : -1.0f;
            float r = CELESTIAL_RADIUS;
            glBegin(GL_TRIANGLE_FAN);
            glColor4f(1.0f, 0.52f, 0.25f, this.sunset * 0.75f);
            glVertex3f(dir * r, 2.0f, 0.0f);
            glColor4f(1.0f, 0.52f, 0.25f, 0.0f);
            for (int i = 0; i <= 16; i++) {
                double ang = i * Math.PI * 2.0 / 16.0;
                glVertex3f(dir * r * 0.7f, (float) Math.sin(ang) * r * 0.3f + 2.0f, (float) Math.cos(ang) * r * 0.95f);
            }
            glEnd();
        }

        glRotatef((float) Math.toDegrees(this.sunAngle), 0.0f, 0.0f, 1.0f);

        // 3. Estrellas (aparecen al caer la noche y giran con el cielo)
        float stars = (1.0f - this.dayFactor);
        stars = stars * stars * 0.9f;
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);
        if (stars > 0.01f) {
            glColor4f(1.0f, 1.0f, 1.0f, stars);
            glCallList(this.starList);
        }

        // 4. Sol con halo
        float r = CELESTIAL_RADIUS;
        glColor4f(1.0f, 0.82f, 0.45f, 0.22f);
        this.quadX(r, 7.0f);
        glColor4f(1.0f, 0.97f, 0.82f, 1.0f);
        this.quadX(r, 3.2f);

        // 5. Luna (con un pequeño halo frío)
        glColor4f(0.6f, 0.7f, 1.0f, 0.12f);
        this.quadX(-r, 5.0f);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(0.88f, 0.9f, 0.98f, 0.95f);
        this.quadX(-r, 2.2f);
        glColor4f(0.68f, 0.7f, 0.8f, 0.9f);
        glBegin(GL_QUADS);
        float mx = -r + 0.05f;
        glVertex3f(mx, 0.3f, -1.2f);
        glVertex3f(mx, 1.1f, -1.2f);
        glVertex3f(mx, 1.1f, -0.4f);
        glVertex3f(mx, 0.3f, -0.4f);
        glVertex3f(mx, -1.3f, 0.5f);
        glVertex3f(mx, -0.7f, 0.5f);
        glVertex3f(mx, -0.7f, 1.1f);
        glVertex3f(mx, -1.3f, 1.1f);
        glEnd();

        glPopMatrix();

        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_BLEND);
        glDepthMask(true);
        glEnable(GL_CULL_FACE);
        glEnable(GL_ALPHA_TEST);
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /** Quad perpendicular al eje X a la distancia x. */
    private void quadX(float x, float s) {
        glBegin(GL_QUADS);
        glVertex3f(x, -s, -s);
        glVertex3f(x, s, -s);
        glVertex3f(x, s, s);
        glVertex3f(x, -s, s);
        glEnd();
    }

    /** Capa de nubes translúcida a CLOUD_HEIGHT que se desplaza lentamente hacia +X. */
    public void renderClouds(float cx, float cz, float far, long time, float partialTick) {
        int tex = this.textures.getCloudTexture();
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, tex);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE);

        FogHelper.linear(this.horizon, far * 0.3f, far);
        glColor4f(this.cloud[0], this.cloud[1], this.cloud[2], 0.85f);

        float scale = 1.0f / (64.0f * CLOUD_TEXEL_BLOCKS);
        float drift = ((time % 1000000L) + partialTick) * 0.03f;
        float x0 = cx - far;
        float x1 = cx + far;
        float z0 = cz - far;
        float z1 = cz + far;
        float y = CLOUD_HEIGHT;
        glBegin(GL_QUADS);
        glTexCoord2f((x0 + drift) * scale, z0 * scale);
        glVertex3f(x0, y, z0);
        glTexCoord2f((x1 + drift) * scale, z0 * scale);
        glVertex3f(x1, y, z0);
        glTexCoord2f((x1 + drift) * scale, z1 * scale);
        glVertex3f(x1, y, z1);
        glTexCoord2f((x0 + drift) * scale, z1 * scale);
        glVertex3f(x0, y, z1);
        glEnd();

        glDisable(GL_FOG);
        glEnable(GL_CULL_FACE);
        glDisable(GL_BLEND);
        glDisable(GL_TEXTURE_2D);
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /** Niebla de función fija (para el cielo y las nubes, que no usan el shader del mundo). */
    static final class FogHelper {
        private static final java.nio.FloatBuffer COLOR = org.lwjgl.BufferUtils.createFloatBuffer(4);

        static void linear(float[] color, float start, float end) {
            COLOR.clear();
            COLOR.put(color[0]).put(color[1]).put(color[2]).put(1.0f).flip();
            glFogi(GL_FOG_MODE, GL_LINEAR);
            glFogf(GL_FOG_START, start);
            glFogf(GL_FOG_END, end);
            glFogfv(GL_FOG_COLOR, COLOR);
            glEnable(GL_FOG);
        }
    }
}
