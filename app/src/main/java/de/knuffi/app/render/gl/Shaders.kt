package de.knuffi.app.render.gl

/**
 * Shaders of the 3D pet. Everything is in the pet's body space: x right, y DOWN (like the canvas),
 * z towards the viewer. The pet turns around the vertical axis by uYaw.
 */
internal object Shaders {
    const val VERTEX = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
uniform mat4 uModel;
uniform mat3 uNrm;
uniform vec2 uYaw;
uniform vec4 uRegion;
uniform float uZRange;
out vec3 vRest;
out vec3 vRestN;
out vec3 vN;
void main() {
    vec4 p = uModel * vec4(aPos, 1.0);
    vec3 n = normalize(uNrm * aNrm);
    vRest = p.xyz;
    vRestN = n;
    float c = uYaw.x;
    float s = uYaw.y;
    vec3 w = vec3(p.x * c + p.z * s, p.y, -p.x * s + p.z * c);
    vN = vec3(n.x * c + n.z * s, n.y, -n.x * s + n.z * c);
    float x = (w.x - uRegion.x) / (uRegion.z - uRegion.x) * 2.0 - 1.0;
    float y = (w.y - uRegion.y) / (uRegion.w - uRegion.y) * 2.0 - 1.0;
    gl_Position = vec4(x, y, -w.z / uZRange, 1.0);
}
"""

    const val FRAGMENT = """#version 300 es
precision highp float;
in vec3 vRest;
in vec3 vRestN;
in vec3 vN;
uniform int uMode;          // 0 plain colour, 1 body with painted decal, 2 textured relief
uniform sampler2D uTex;
uniform vec4 uRegion;
uniform vec3 uBase;
uniform vec3 uRim;
uniform float uGloss;
uniform float uLit;         // how much of the 3D light is applied (reliefs already carry painted shading)
uniform vec2 uFloor;        // bottom of the body and body half height, for ambient occlusion
uniform vec4 uTint;         // sick / golden tint (rgb, amount)
out vec4 outColor;

void main() {
    vec3 n = normalize(vN);
    vec2 uv = vec2((vRest.x - uRegion.x) / (uRegion.z - uRegion.x), (vRest.y - uRegion.y) / (uRegion.w - uRegion.y));
    vec3 albedo = uBase;
    float alpha = 1.0;
    if (uMode == 1) {
        vec4 d = texture(uTex, uv);
        // painted on from the front, fading out towards the sides
        float f = smoothstep(0.0, 0.32, normalize(vRestN).z);
        d *= f;
        albedo = albedo * (1.0 - d.a) + d.rgb;
    } else if (uMode == 2) {
        vec4 d = texture(uTex, uv);
        if (d.a < 0.04) discard;
        albedo = d.rgb / max(d.a, 0.001);
        alpha = smoothstep(0.04, 0.55, d.a);
    }

    // key light from the top left front (y is down)
    vec3 L = normalize(vec3(-0.5, -0.75, 0.6));
    float ndl = dot(n, L);
    float wrap = clamp((ndl + 0.45) / 1.45, 0.0, 1.0);
    float sky = 0.5 - 0.5 * n.y;
    vec3 V = vec3(0.0, 0.0, 1.0);
    vec3 H = normalize(L + V);
    float nh = max(dot(n, H), 0.0);
    float spec = (pow(nh, 70.0) * 0.9 + pow(nh, 14.0) * 0.12) * uGloss;
    float fres = pow(1.0 - clamp(n.z, 0.0, 1.0), 3.0);

    // soft occlusion towards the floor
    float ao = mix(0.84, 1.0, smoothstep(uFloor.x, uFloor.x - uFloor.y * 0.8, vRest.y));
    float light = 0.58 + 0.12 * sky + 0.46 * wrap;
    light = mix(1.0, light, uLit) * mix(1.0, ao, uLit);
    vec3 col = albedo * light;
    // subsurface-like warm glow in the shadow
    col += albedo * vec3(0.10, 0.06, 0.04) * clamp(-ndl, 0.0, 1.0) * uLit;
    col += uRim * fres * 0.32 * ao * uLit;
    col += vec3(spec) * mix(0.35, 1.0, uLit);
    col = mix(col, uTint.rgb, uTint.a);
    outColor = vec4(clamp(col, 0.0, 1.0) * alpha, alpha);
}
"""
}
