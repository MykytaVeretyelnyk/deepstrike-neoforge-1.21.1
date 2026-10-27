#version 150
// Zero-K PD SmokeSmall / SmokeTrail: source RGBA is rendered directly.
// Spring/Recoil geometry and opacity are supplied by SpringSmokeTrail.java.
uniform sampler2D Sampler0;
uniform float CloudMode;
uniform mat4 LightPosMatA;
uniform mat4 LightJetMatA;
uniform mat4 LightColMatA;
uniform mat4 LightPosMatB;
uniform mat4 LightJetMatB;
uniform mat4 LightColMatB;
in vec4 tint;
in vec2 coord;
in float exhaustHeat;
in vec3 worldPos;
out vec4 fragColor;

vec3 evalWakeLight3D(vec4 lPosRad, vec4 lJetInt, vec4 lColMode, vec3 fragPos, float radialTube, float opticalDensity, float texAlpha) {
    float radius = lPosRad.w;
    float intensity = lJetInt.w;
    if (radius <= 0.01 || intensity <= 0.002) return vec3(0.0);
    vec3 lPos = lPosRad.xyz;
    vec3 lJet = lJetInt.xyz;
    float jetLen2 = dot(lJet, lJet);
    float segU = jetLen2 > 1e-4 ? clamp(dot(fragPos - lPos, lJet) / jetLen2, 0.0, 1.0) : 0.0;
    vec3 closestPt = lPos + lJet * segU;
    vec3 toLight = closestPt - fragPos;
    float dist2 = dot(toLight, toLight);
    float r2 = dist2 / max(0.04, radius * radius);
    if (r2 > 2.25) return vec3(0.0);

    float boundary = 1.0 - smoothstep(1.0, 2.25, r2);
    float invQ = 1.0 / (1.0 + r2 * 2.05);
    float atten = invQ * invQ * boundary;

    float coreRadius = max(0.35, radius * 0.26 * (1.0 + 0.35 * segU));
    float coreGlow = exp(-dist2 / (coreRadius * coreRadius) * 1.65) * (1.0 - 0.35 * segU) * radialTube;
    float subsurface = (0.45 + 0.55 * radialTube) * (1.0 - texAlpha * 0.42) * exp(-opticalDensity * 1.25);
    float weight = clamp((atten * (0.48 + 0.65 * subsurface) + coreGlow * 0.90) * intensity, 0.0, 2.3);
    vec3 lightRgb = mix(lColMode.rgb, vec3(1.0, 0.96, 0.85), clamp(coreGlow * 0.78 + atten * 0.22, 0.0, 1.0));
    return lightRgb * weight;
}

void main() {
    vec2 uv;
    if (CloudMode > .5)
        uv = clamp(coord*.5+.5, vec2(.5/32.0), vec2(31.5/32.0));
    else
        uv = vec2(clamp(fract(coord.x), .5/16.0, 15.5/16.0),
                  clamp(coord.y*.5+.5, .5/64.0, 63.5/64.0));
    vec4 texel = texture(Sampler0, uv);
    float radial = exp(-coord.y * coord.y * 2.2);
    float opticalDensity = dot(texel.rgb, vec3(0.3333)) * texel.a;

    vec3 dynamicLight3D = vec3(0.0);
    for (int i = 0; i < 4; i++) {
        dynamicLight3D += evalWakeLight3D(LightPosMatA[i], LightJetMatA[i], LightColMatA[i], worldPos, radial, opticalDensity, texel.a);
        dynamicLight3D += evalWakeLight3D(LightPosMatB[i], LightJetMatB[i], LightColMatB[i], worldPos, radial, opticalDensity, texel.a);
    }

    float scatter = clamp(exhaustHeat, 0.0, 1.0);
    if (scatter > 0.001) {
        vec3 warmCol = mix(vec3(1.42, 0.56, 0.16), vec3(1.62, 1.38, 0.88), smoothstep(0.45, 0.96, scatter * radial));
        dynamicLight3D = max(dynamicLight3D, warmCol * (scatter * (0.45 + 0.55 * radial)));
    }

    vec3 hdrSmoke = tint.rgb + dynamicLight3D * 0.82;
    vec3 lit = hdrSmoke / (vec3(1.0) + max(vec3(0.0), hdrSmoke - vec3(0.85)) * 0.36);
    fragColor = vec4(texel.rgb * lit, texel.a * tint.a);
}
