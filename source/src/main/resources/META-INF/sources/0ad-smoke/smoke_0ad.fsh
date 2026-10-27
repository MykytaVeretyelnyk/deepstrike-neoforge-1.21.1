#version 150
// Derived from Wildfire Games / 0 A.D. particle.fs, GPL-2.0-or-later.
// Upstream commit 61a3b9507d974084e6badb88a0826bd89a6d5b8b.
// Forge adapter: baseTex -> Sampler0/1; v_tex -> coord*.5+.5;
// v_color -> tint. RTS line-of-sight and engine fog are not ported.
// Original texture, colour/sun averaging and straight-alpha blend are retained.
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec3 SunColor;
uniform float BaseLayer;
in vec4 tint;
in vec2 coord;
out vec4 fragColor;
void main() {
    vec4 texel;
    if (BaseLayer > .5) {
        vec2 uv = clamp(coord*.5+.5, vec2(.5/512.0), vec2(511.5/512.0));
        texel = texture(Sampler0, uv);
    } else {
        vec2 uv = clamp(coord*.5+.5, vec2(.5/128.0), vec2(127.5/128.0));
        texel = texture(Sampler1, uv);
    }
    fragColor = texel * vec4((tint.rgb + SunColor) / 2.0, tint.a);
}
