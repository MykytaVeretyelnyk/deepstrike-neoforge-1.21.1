#version 150
in vec3 volumePoint;
in float strength;
in float opticalStep;
in float flashMix;
uniform float BurnStage;
uniform float JetAspect;
out vec4 fragColor;

void main() {
    float segU = 0.0;
    vec3 delta = volumePoint;
    float radialScale = 1.0;
    float axialFade = 1.0;

    if (flashMix < 0.5 && JetAspect > 0.05) {
        float zNozzle = -0.38 * JetAspect;
        float zTail = 0.54 * JetAspect;
        segU = clamp((volumePoint.z - zNozzle) / max(0.001, zTail - zNozzle), 0.0, 1.0);
        float spineZ = mix(zNozzle, zTail, segU);
        // Widening downstream cone profile that tapers smoothly at the plume tail
        float coneProfile = (0.76 + 0.42 * sin(segU * 2.55)) * (1.0 - 0.38 * segU * segU);
        radialScale = 1.0 / max(0.42, coneProfile);
        delta = vec3(volumePoint.x * radialScale, volumePoint.y * radialScale, (volumePoint.z - spineZ) * 1.08);
        axialFade = 1.0 - 0.52 * smoothstep(0.25, 1.0, segU);
    }

    float r2 = dot(delta, delta);
    float outerBound = dot(volumePoint, volumePoint);
    // Continuous 3D density with smooth boundary fade to prevent any clipping edges
    float boundary = (1.0 - smoothstep(0.68, 1.0, r2)) * (1.0 - smoothstep(0.76, 1.02, outerBound));
    if (boundary <= 0.0005) discard;

    float localStage = clamp(BurnStage + segU * 0.24, 0.0, 1.0);

    // Hybrid 3D in-scattering profile: Cauchy/Gaussian volumetric halo + AMCW dual-Gaussian hot core & haze
    float halo = mix(.44, .34, localStage) * exp(-mix(4.8, 5.8, localStage) * r2);
    float core = exp(-mix(16.5, 19.5, localStage) * r2) * mix(1.48, 1.12, localStage) * (1.0 - 0.35 * segU);
    float amcwHaze = exp(-2.25 * r2) * mix(.24, .16, localStage);
    float density = (halo + core + amcwHaze) * boundary * axialFade;

    // Optical transmission along 3D view ray slice
    float alpha = 1.0 - exp(-density * 0.42 * opticalStep * strength * mix(2.35, 1.0, flashMix));
    if (alpha < 0.0004) discard;

    // Engine flame spectral palette (combustion ~2400K to 1300K)
    vec3 flameCore = mix(vec3(1.0, .90, .68), vec3(1.0, .80, .54), localStage);
    vec3 flameBody = mix(vec3(1.0, .50, .14), vec3(.96, .40, .10), localStage);
    vec3 flameRim  = mix(vec3(.96, .34, .085), vec3(.85, .26, .06), localStage);
    vec3 ourEngineColor = mix(flameRim, mix(flameBody, flameCore, smoothstep(0.10, 0.85, core)), smoothstep(0.02, 0.45, density));

    // AMCW motor jet & atmospheric haze palette
    float amcwHot = clamp(core * 0.85, 0.0, 1.15);
    float amcwRim = clamp(halo * 1.65, 0.0, 1.15);
    vec3 amcwColor = vec3(1.0, 0.59, 0.23) * (0.35 + amcwHaze * 1.4)
                   + vec3(1.0, 0.47, 0.12) * max(amcwRim - amcwHot * 0.72, 0.0) * 1.18
                   + vec3(1.0, 0.98, 0.91) * amcwHot * 1.38;
    vec3 engineColor = mix(ourEngineColor, amcwColor, 0.52);

    // Explosion flash spectral palette (~6500K peak into ~1400K fireball)
    vec3 flashWhite = vec3(0.98, 0.99, 1.0);
    vec3 flashGold  = vec3(1.0, 0.86, 0.54);
    vec3 flashAmber = vec3(1.0, 0.48, 0.13);
    vec3 flashColor = mix(flashAmber, mix(flashGold, flashWhite, smoothstep(0.14, 0.78, core)), smoothstep(0.01, 0.50, density));

    vec3 finalColor = mix(engineColor, flashColor, flashMix);

    // Reinhard tonemapping to keep intense 3D core brilliant white-gold while preserving saturated corona
    vec3 hdrColor = finalColor * (1.0 + core * 2.75);
    vec3 ldrColor = hdrColor / (vec3(1.0) + hdrColor * 0.34);

    fragColor = vec4(ldrColor, alpha);
}
