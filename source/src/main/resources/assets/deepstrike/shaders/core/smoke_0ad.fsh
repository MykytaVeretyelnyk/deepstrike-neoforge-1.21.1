#version 150
// Derived from Wildfire Games / 0 A.D. particle.fs, GPL-2.0-or-later.
// Upstream commit 61a3b9507d974084e6badb88a0826bd89a6d5b8b.
// Forge adapter: baseTex -> Sampler0/1; v_tex -> coord*.5+.5;
// v_color -> tint. RTS line-of-sight and engine fog are not ported.
// Original texture, colour/sun averaging and straight-alpha blend are retained.
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler3;
uniform vec3 SunColor;
uniform float BaseLayer;
uniform float DensePass;
uniform mat4 LightPosMatA;
uniform mat4 LightJetMatA;
uniform mat4 LightColMatA;
uniform mat4 LightPosMatB;
uniform mat4 LightJetMatB;
uniform mat4 LightColMatB;
in vec4 tint;
in vec2 coord;
in float exhaustHeat;
in float compactGlow;
in float blastWave;
in vec3 worldPos;
out vec4 fragColor;

vec3 evalSmokeLight3D(vec4 lPosRad, vec4 lJetInt, vec4 lColMode, vec3 fragPos, vec3 normal3D, vec3 viewDir, float opticalDensity, float texAlpha) {
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

    float coreRadius = max(0.38, radius * 0.28 * (1.0 + 0.35 * segU));
    float coreR2 = dist2 / (coreRadius * coreRadius);
    float coreGlow = exp(-coreR2 * 1.65) * (1.0 - 0.35 * segU);

    vec3 lightDir = dist2 > 1e-6 ? toLight * inversesqrt(dist2) : viewDir;
    // 1. 3D Volumetric Lobe & Billow Relief Diffuse + wrap-around cloud multiple scattering for outer billows
    float diffuse = max(0.0, dot(normal3D, lightDir));
    float wrapDiffuse = 0.56 + 0.44 * dot(normal3D, lightDir);
    // 2. Deep Internal Subsurface Transmission (light shines from inside the smoke cavity through outer billow edges)
    float forwardScatter = 0.52 + 0.48 * max(0.0, dot(viewDir, -lightDir));
    float rimScatter = smoothstep(0.15, 0.92, r2) * (1.0 - texAlpha * 0.52);
    float transmission = exp(-opticalDensity * 1.12) * (1.0 - texAlpha * 0.38) * forwardScatter + rimScatter * 0.44;
    // 3. Isotropic internal multiple scattering inside and across the outer cloud billows
    float internalScatter = 0.48 + 0.56 * max(diffuse, wrapDiffuse * 0.78) + 0.66 * transmission;

    float totalWeight = clamp((atten * internalScatter + coreGlow * 0.90) * intensity, 0.0, 2.45);
    vec3 baseCol = lColMode.rgb;
    vec3 hotCoreCol = vec3(1.0, 0.96, 0.85);
    vec3 lightRgb = mix(baseCol, hotCoreCol, clamp(coreGlow * 0.80 + atten * 0.28, 0.0, 1.0));
    return lightRgb * totalWeight;
}

void main() {
    vec4 texel;
    if (blastWave > .5) {
        texel = texture(Sampler3, coord*.5+.5);
        texel.rgb = vec3(.60) + texel.rgb * .60;
    } else if (BaseLayer > .5) {
        vec2 uv = clamp(coord*.5+.5, vec2(.5/512.0), vec2(511.5/512.0));
        texel = texture(Sampler0, uv);
    } else {
        vec2 uv = clamp(coord*.5+.5, vec2(.5/128.0), vec2(127.5/128.0));
        texel = texture(Sampler1, uv);
    }
    float border = smoothstep(0.0, .045, 1.0 - max(abs(coord.x), abs(coord.y)));
    float alpha = texel.a * tint.a * border;
    if(blastWave>.5) {
        alpha=(1.0-exp(-texel.a*(blastWave<1.5?9.0:3.0)))*tint.a*border;
        if(blastWave<1.5)alpha=1.0-exp(-alpha*8.0);
    }
    if(DensePass>.5) {
        if(blastWave<.5 || blastWave>1.5 || alpha<.985)discard;
        alpha=1.0;
    } else if(blastWave>.5 && blastWave<1.5 && alpha>=.985)discard;
    if (alpha < .0001) discard;

    vec3 daylight = min((tint.rgb + SunColor) / 2.0, vec3(.94));
    float aerosol = smoothstep(.65, .90, max(tint.r, max(tint.g, tint.b)));
    daylight = mix(tint.rgb * (vec3(.6) + SunColor * .4), daylight, aerosol);
    float ambientVisibility = clamp(dot(SunColor, vec3(.2126, .7152, .0722)) / 1.34, 0.0, 1.0);
    daylight *= mix(.055, 1.0, pow(ambientVisibility, 1.35));

    // Reconstruct true 3D volumetric cloud normal from 3D world-space derivatives + billow density relief
    float opticalDensity = dot(texel.rgb, vec3(0.3333)) * texel.a;
    vec3 viewDir = length(worldPos) > 1e-4 ? -normalize(worldPos) : vec3(0.0, 1.0, 0.0);
    vec3 dPosDx = dFdx(worldPos);
    vec3 dPosDy = dFdy(worldPos);
    vec3 tx = length(dPosDx) > 1e-6 ? normalize(dPosDx) : vec3(1.0, 0.0, 0.0);
    vec3 ty = length(dPosDy) > 1e-6 ? normalize(dPosDy) : vec3(0.0, 1.0, 0.0);
    float r2 = dot(coord, coord);
    float sphereZ = sqrt(max(0.06, 1.0 - min(0.94, r2 * 0.82)));
    float dDensX = dFdx(opticalDensity);
    float dDensY = dFdy(opticalDensity);
    vec3 normal3D = normalize(
        viewDir * sphereZ
        + tx * (coord.x * 0.62 - dDensX * 16.0)
        + ty * (coord.y * 0.62 - dDensY * 16.0)
    );

    // Evaluate all 8 active 3D volumetric light capsules / explosion fireballs in world space
    vec3 dynamicLight3D = vec3(0.0);
    for (int i = 0; i < 4; i++) {
        dynamicLight3D += evalSmokeLight3D(LightPosMatA[i], LightJetMatA[i], LightColMatA[i], worldPos, normal3D, viewDir, opticalDensity, texel.a);
        dynamicLight3D += evalSmokeLight3D(LightPosMatB[i], LightJetMatB[i], LightColMatB[i], worldPos, normal3D, viewDir, opticalDensity, texel.a);
    }

    // Also include vertex-local 3D relief scattering so distant or >8 simultaneous wakes remain internally and externally lit
    float vScatter = clamp(exhaustHeat, 0.0, 1.0) * mix(1.0, exp(-r2 * 4.5), compactGlow);
    if (vScatter > 0.001) {
        float billowEdge = 1.0 - texel.a * 0.40;
        float relief3D = 0.50 + 0.42 * sphereZ + 0.42 * billowEdge;
        vec3 warmCol = mix(vec3(1.48, 0.60, 0.18), vec3(1.66, 1.40, 0.90), smoothstep(0.38, 0.94, vScatter));
        dynamicLight3D = max(dynamicLight3D, warmCol * (vScatter * relief3D));
    }

    // Soft Reinhard-style HDR internal illumination integration into the smoke albedo
    vec3 hdrSmoke = daylight + dynamicLight3D * mix(vec3(0.68), max(tint.rgb, vec3(0.55)), 0.65);
    vec3 lit = hdrSmoke / (vec3(1.0) + max(vec3(0.0), hdrSmoke - vec3(0.85)) * 0.36);

    fragColor = vec4(texel.rgb * lit, alpha);
}
