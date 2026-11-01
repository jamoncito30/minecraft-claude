package com.mojang.minecraft.particle;

import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.phys.AABB;
import com.mojang.minecraft.renderer.Tesselator;

import java.util.List;

/**
 * Partícula física 3D generada al romper un bloque en Minecraft Classic 0.0.11a / 0.0.14a.
 * Muestra un fragmento aleatorio de 4x4 píxeles de la textura del bloque roto.
 */
public class Particle {
    private final Level level;
    public float xo, yo, zo;
    public float x, y, z;
    public float xd, yd, zd;
    public AABB bb;
    public boolean onGround = false;
    public boolean removed = false;

    public int tex;
    private final float uo;
    private final float vo;
    private int age = 0;
    private final int lifetime;
    private final float size;

    public Particle(Level level, float x, float y, float z, float xa, float ya, float za, int tex) {
        this.level = level;
        this.tex = tex;
        this.setSize(0.2f, 0.2f);
        this.setPos(x, y, z);

        this.xd = xa + (float) (Math.random() * 2.0 - 1.0) * 0.4f;
        this.yd = ya + (float) (Math.random() * 2.0 - 1.0) * 0.4f;
        this.zd = za + (float) (Math.random() * 2.0 - 1.0) * 0.4f;

        float speed = (float) (Math.random() + Math.random() + 1.0) * 0.15f;
        float dd = (float) Math.sqrt(this.xd * this.xd + this.yd * this.yd + this.zd * this.zd);
        this.xd = this.xd / dd * speed * 0.4f;
        this.yd = this.yd / dd * speed * 0.4f + 0.1f;
        this.zd = this.zd / dd * speed * 0.4f;

        this.uo = (float) Math.random() * 3.0f;
        this.vo = (float) Math.random() * 3.0f;
        this.size = (float) (Math.random() * 0.5 + 0.5);
        this.lifetime = (int) (4.0 / (Math.random() * 0.9 + 0.1));
    }

    private void setSize(float w, float h) {
        if (this.bb != null) {
            this.setPos(this.x, this.y, this.z);
        }
    }

    private void setPos(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        float w = 0.1f;
        float h = 0.1f;
        this.bb = new AABB(x - w, y - h, z - w, x + w, y + h, z + w);
    }

    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (this.age++ >= this.lifetime) {
            this.removed = true;
            return;
        }

        this.yd -= 0.04f;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.98f;
        this.yd *= 0.98f;
        this.zd *= 0.98f;

        if (this.onGround) {
            this.xd *= 0.7f;
            this.zd *= 0.7f;
        }
    }

    private void move(float xa, float ya, float za) {
        float xaOrg = xa;
        float yaOrg = ya;
        float zaOrg = za;

        List<AABB> aabbs = this.level.getCubes(this.bb.expand(xa, ya, za));
        for (AABB aabb : aabbs) {
            ya = aabb.clipYCollide(this.bb, ya);
        }
        this.bb.move(0.0f, ya, 0.0f);

        for (AABB aabb : aabbs) {
            xa = aabb.clipXCollide(this.bb, xa);
        }
        this.bb.move(xa, 0.0f, 0.0f);

        for (AABB aabb : aabbs) {
            za = aabb.clipZCollide(this.bb, za);
        }
        this.bb.move(0.0f, 0.0f, za);

        this.onGround = (yaOrg != ya && yaOrg < 0.0f);
        if (xaOrg != xa) this.xd = 0.0f;
        if (yaOrg != ya) this.yd = 0.0f;
        if (zaOrg != za) this.zd = 0.0f;

        this.x = (this.bb.x0 + this.bb.x1) / 2.0f;
        this.y = this.bb.y0 + 0.1f;
        this.z = (this.bb.z0 + this.bb.z1) / 2.0f;
    }

    public void render(Tesselator t, float a, float xa, float ya, float za, float xa2, float za2) {
        float u0 = ((this.tex % 16) + this.uo / 4.0f) / 16.0f;
        float u1 = u0 + 0.999f / 64.0f;
        float v0 = ((this.tex / 16) + this.vo / 4.0f) / 16.0f;
        float v1 = v0 + 0.999f / 64.0f;

        float r = 0.1f * this.size;
        float x = this.xo + (this.x - this.xo) * a;
        float y = this.yo + (this.y - this.yo) * a;
        float z = this.zo + (this.z - this.zo) * a;

        int xt = (int) Math.floor(x);
        int yt = (int) Math.floor(y);
        int zt = (int) Math.floor(z);
        t.color(1.0f, 1.0f, 1.0f);
        t.light(this.level.getSkyLight(xt, yt, zt) / 15.0f, this.level.getBlockLight(xt, yt, zt) / 15.0f);

        t.vertexUV(x - xa * r - xa2 * r, y - ya * r, z - za * r - za2 * r, u0, v1);
        t.vertexUV(x - xa * r + xa2 * r, y + ya * r, z - za * r + za2 * r, u0, v0);
        t.vertexUV(x + xa * r + xa2 * r, y + ya * r, z + za * r + za2 * r, u1, v0);
        t.vertexUV(x + xa * r - xa2 * r, y - ya * r, z + za * r - za2 * r, u1, v1);
    }
}
