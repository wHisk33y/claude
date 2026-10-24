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
uniform vec2 uFloor;        // bottom of the body and body half height
uniform vec3 uWobble;       // amplitude, wave number, time
uniform vec2 uPitch;        // cos, sin of leaning forward (a bow)
out vec3 vRest;
out vec3 vRestN;
out vec3 vN;
void main() {
    vec4 p = uModel * vec4(aPos, 1.0);
    vec3 n = normalize(uNrm * aNrm);
    vRest = p.xyz;
    vRestN = n;
    // soft jiggle: the head sways more than the feet
    float up = clamp((uFloor.x - p.y) / max(uFloor.y * 2.0, 1.0), 0.0, 1.2);
    p.x += sin(p.y * uWobble.y + uWobble.z * 3.1) * uWobble.x * up;
    p.z += cos(p.y * uWobble.y * 0.7 + uWobble.z * 2.3) * uWobble.x * 0.5 * up;
    // leaning forward around the feet: the head comes towards the viewer and down
    float dy = p.y - uFloor.x;
    float pz = p.z * uPitch.x - dy * uPitch.y;
    p.y = uFloor.x + dy * uPitch.x + p.z * uPitch.y;
    p.z = pz;
    float nny = n.y * uPitch.x + n.z * uPitch.y;
    n.z = n.z * uPitch.x - n.y * uPitch.y;
    n.y = nny;
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
uniform vec4 uSurface;      // fur, (unused), jelly, 1 / fur fibre size
uniform vec3 uKey;          // colour of the main light (time of day)
uniform vec3 uAmb;          // colour of the soft light all around
uniform float uTime;
out vec4 outColor;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.71, 0.113, 0.419));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x), mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
        mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x), mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y),
        f.z);
}

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

    float fur = uSurface.x;
    float jelly = uSurface.z;
    vec3 q = vRest * uSurface.w;
    if (fur > 0.0) {
        // fine fibres: stretched noise along the body, a little lighter and darker
        float fib = noise(vec3(q.x * 2.2, q.y * 0.7, q.z * 2.2)) * 0.6 + noise(q * 5.0) * 0.4;
        albedo *= mix(1.0, 0.88 + 0.24 * fib, fur * uLit);
        // the fur bends the light a little
        n = normalize(n + (vec3(noise(q * 3.1), noise(q * 3.7 + 9.0), noise(q * 4.3 + 3.0)) - 0.5) * 0.25 * fur);
    }

    // key light from the top left front (y is down)
    vec3 L = normalize(vec3(-0.5, -0.75, 0.6));
    float ndl = dot(n, L);
    float wrap = clamp((ndl + 0.45) / 1.45, 0.0, 1.0);
    float sky = 0.5 - 0.5 * n.y;
    vec3 V = vec3(0.0, 0.0, 1.0);
    vec3 H = normalize(L + V);
    float nh = max(dot(n, H), 0.0);
    float gloss = uGloss * mix(1.0, 0.3, fur) * mix(1.0, 1.4, jelly);
    float spec = (pow(nh, mix(70.0, 120.0, jelly)) * 0.9 + pow(nh, 14.0) * 0.12) * gloss;
    float fres = pow(1.0 - clamp(n.z, 0.0, 1.0), 3.0);

    // soft occlusion towards the floor
    float ao = mix(0.84, 1.0, smoothstep(uFloor.x, uFloor.x - uFloor.y * 0.8, vRest.y));
    vec3 light = uAmb * (0.58 + 0.12 * sky) + uKey * (0.46 * wrap);
    light = mix(vec3(1.0), light, uLit) * mix(1.0, ao, uLit);
    vec3 col = albedo * light;
    // subsurface-like warm glow in the shadow (stronger for jelly)
    col += albedo * vec3(0.10, 0.06, 0.04) * clamp(-ndl, 0.0, 1.0) * uLit * (1.0 + 2.0 * jelly);
    col += uRim * fres * (0.32 + 0.25 * fur + 0.3 * jelly) * ao * uLit;
    col += uKey * vec3(spec) * mix(0.35, 1.0, uLit);
    col = mix(col, uTint.rgb, uTint.a);
    alpha *= mix(1.0, 0.9, jelly * step(0.5, float(uMode == 1)));
    outColor = vec4(clamp(col, 0.0, 1.0) * alpha, alpha);
}
"""
}
