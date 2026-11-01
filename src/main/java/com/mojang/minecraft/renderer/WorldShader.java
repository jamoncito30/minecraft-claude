package com.mojang.minecraft.renderer;

import static org.lwjgl.opengl.GL20.*;

/**
 * Shader GLSL 1.20 del mundo. Combina la textura del bloque, el sombreado por cara + oclusión ambiental
 * (color del vértice) y los dos canales de luz (cielo y bloque, enviados en la normal del vértice).
 * La luz del cielo se multiplica por la luz del día, así el ciclo día/noche no necesita reconstruir chunks.
 * También calcula la niebla (lineal o exponencial bajo el agua).
 */
public class WorldShader {
    private static final String VERTEX = """
            #version 120
            varying vec2 vUv;
            varying vec3 vColor;
            varying vec2 vLight;
            varying float vDist;
            void main() {
                vUv = gl_MultiTexCoord0.xy;
                vColor = gl_Color.rgb;
                vLight = gl_Normal.xy;
                vec4 eye = gl_ModelViewMatrix * gl_Vertex;
                vDist = length(eye.xyz);
                gl_Position = gl_ProjectionMatrix * eye;
            }
            """;

    private static final String FRAGMENT = """
            #version 120
            uniform sampler2D uTex;
            uniform float uDaylight;
            uniform vec3 uFogColor;
            uniform float uFogStart;
            uniform float uFogEnd;
            uniform float uFogDensity;
            uniform int uFogMode;
            varying vec2 vUv;
            varying vec3 vColor;
            varying vec2 vLight;
            varying float vDist;

            float curve(float l) {
                return pow(0.8, 15.0 * (1.0 - clamp(l, 0.0, 1.0)));
            }

            void main() {
                vec4 tex = texture2D(uTex, vUv);
                if (tex.a < 0.1) discard;

                float sky = curve(vLight.x) * uDaylight;
                float blk = curve(vLight.y);
                vec3 skyCol = sky * mix(vec3(0.55, 0.62, 0.95), vec3(1.0), clamp(uDaylight * 1.4 - 0.2, 0.0, 1.0));
                vec3 blkCol = blk * vec3(1.0, 0.86, 0.66);
                vec3 light = max(max(skyCol, blkCol), vec3(0.03));

                vec3 col = tex.rgb * vColor * light;

                float fog = 0.0;
                if (uFogMode == 0) {
                    fog = clamp((vDist - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
                } else if (uFogMode == 1) {
                    fog = 1.0 - exp(-uFogDensity * vDist);
                }
                gl_FragColor = vec4(mix(col, uFogColor, fog), tex.a);
            }
            """;

    public static final int FOG_LINEAR = 0;
    public static final int FOG_EXP = 1;
    public static final int FOG_NONE = 2;

    private final int program;
    private final int uDaylight;
    private final int uFogColor;
    private final int uFogStart;
    private final int uFogEnd;
    private final int uFogDensity;
    private final int uFogMode;

    public WorldShader() {
        int vs = compile(GL_VERTEX_SHADER, VERTEX);
        int fs = compile(GL_FRAGMENT_SHADER, FRAGMENT);
        this.program = glCreateProgram();
        glAttachShader(this.program, vs);
        glAttachShader(this.program, fs);
        glLinkProgram(this.program);
        if (glGetProgrami(this.program, GL_LINK_STATUS) == 0) {
            throw new IllegalStateException("Error al enlazar el shader del mundo: " + glGetProgramInfoLog(this.program));
        }
        glDeleteShader(vs);
        glDeleteShader(fs);

        this.uDaylight = glGetUniformLocation(this.program, "uDaylight");
        this.uFogColor = glGetUniformLocation(this.program, "uFogColor");
        this.uFogStart = glGetUniformLocation(this.program, "uFogStart");
        this.uFogEnd = glGetUniformLocation(this.program, "uFogEnd");
        this.uFogDensity = glGetUniformLocation(this.program, "uFogDensity");
        this.uFogMode = glGetUniformLocation(this.program, "uFogMode");

        glUseProgram(this.program);
        glUniform1i(glGetUniformLocation(this.program, "uTex"), 0);
        glUseProgram(0);
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            throw new IllegalStateException("Error al compilar el shader: " + glGetShaderInfoLog(shader));
        }
        return shader;
    }

    public void bind() {
        glUseProgram(this.program);
    }

    public void unbind() {
        glUseProgram(0);
    }

    /** Debe llamarse con el shader activo. */
    public void setDaylight(float daylight) {
        glUniform1f(this.uDaylight, daylight);
    }

    /** Debe llamarse con el shader activo. */
    public void setFog(int mode, float r, float g, float b, float start, float end, float density) {
        glUniform1i(this.uFogMode, mode);
        glUniform3f(this.uFogColor, r, g, b);
        glUniform1f(this.uFogStart, start);
        glUniform1f(this.uFogEnd, end);
        glUniform1f(this.uFogDensity, density);
    }
}
