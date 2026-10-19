package de.knuffi.app.render.house3d

/** Shaders of the 3D house: soft sun and sky light, shadows from a shadow map. */
internal object Shaders3 {
    const val VERTEX = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec2 aUv;
uniform mat4 uVP;
uniform mat4 uModel;
uniform mat3 uNrm;
uniform mat4 uLightVP;
out vec3 vPos;
out vec3 vN;
out vec2 vUv;
out vec4 vLight;
void main() {
    vec4 wp = uModel * vec4(aPos, 1.0);
    vPos = wp.xyz;
    vN = normalize(uNrm * aNrm);
    vUv = aUv;
    // a little along the normal against shadow acne
    vLight = uLightVP * vec4(wp.xyz + vN * 0.012, 1.0);
    gl_Position = uVP * wp;
}
"""

    const val FRAGMENT = """#version 300 es
precision highp float;
in vec3 vPos;
in vec3 vN;
in vec2 vUv;
in vec4 vLight;
uniform vec4 uColor;
uniform int uTexMode;
uniform sampler2D uTex;
uniform highp sampler2DShadow uShadow;
uniform int uUseShadow;
uniform vec2 uShadowTexel;
uniform vec3 uEye;
uniform vec3 uLightDir;
uniform vec3 uLightCol;
uniform vec3 uSky;
uniform vec3 uGround;
uniform float uGloss;
uniform float uEmissive;
uniform vec4 uTint;
out vec4 outColor;

float shadowAt() {
    if (uUseShadow == 0) return 1.0;
    vec3 p = vLight.xyz / vLight.w * 0.5 + 0.5;
    if (p.x <= 0.0 || p.x >= 1.0 || p.y <= 0.0 || p.y >= 1.0 || p.z >= 1.0) return 1.0;
    float s = 0.0;
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            s += texture(uShadow, vec3(p.xy + vec2(float(x), float(y)) * uShadowTexel * 1.5, p.z - 0.0008));
        }
    }
    return s / 9.0;
}

void main() {
    vec3 base = uColor.rgb;
    if (uTexMode > 0) {
        vec4 t = texture(uTex, vUv);
        if (uTexMode == 2 && t.a < 0.5) discard;
        vec3 trgb = t.a > 0.004 ? t.rgb / t.a : vec3(1.0);
        base *= trgb;
    }
    vec3 n = normalize(vN);
    vec3 v = normalize(uEye - vPos);
    if (dot(n, v) < 0.0) n = -n;
    vec3 l = uLightDir;
    float ndl = dot(n, l);
    float wrap = clamp((ndl + 0.3) / 1.3, 0.0, 1.0);
    float sh = shadowAt();
    vec3 hemi = mix(uGround, uSky, n.y * 0.5 + 0.5);
    vec3 light = hemi + uLightCol * wrap * mix(0.3, 1.0, sh);
    vec3 col = base * light;
    vec3 h = normalize(l + v);
    float spec = pow(max(dot(n, h), 0.0), 40.0) * uGloss * sh;
    col += vec3(spec) * 0.55;
    float rim = pow(1.0 - max(dot(n, v), 0.0), 3.0);
    col += base * rim * 0.1;
    col = mix(col, base * 1.05, uEmissive);
    col = mix(col, uTint.rgb, uTint.a);
    col = clamp(col, 0.0, 1.0);
    float a = uColor.a;
    outColor = vec4(col * a, a);
}
"""

    const val SHADOW_VERTEX = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec2 aUv;
uniform mat4 uLightVP;
uniform mat4 uModel;
out vec2 vUv;
void main() {
    vUv = aUv;
    gl_Position = uLightVP * uModel * vec4(aPos, 1.0);
}
"""

    const val SHADOW_FRAGMENT = """#version 300 es
precision mediump float;
in vec2 vUv;
uniform int uCut;
uniform sampler2D uTex;
out vec4 outColor;
void main() {
    if (uCut == 1 && texture(uTex, vUv).a < 0.5) discard;
    outColor = vec4(1.0);
}
"""
}
