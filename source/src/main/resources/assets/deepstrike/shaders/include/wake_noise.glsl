// Original smooth value noise; no external textures or copied effect assets.
float wakeHash(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}
float wakeNoise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(wakeHash(i), wakeHash(i+vec3(1,0,0)), f.x),
                   mix(wakeHash(i+vec3(0,1,0)), wakeHash(i+vec3(1,1,0)), f.x), f.y),
               mix(mix(wakeHash(i+vec3(0,0,1)), wakeHash(i+vec3(1,0,1)), f.x),
                   mix(wakeHash(i+vec3(0,1,1)), wakeHash(i+vec3(1,1,1)), f.x), f.y), f.z);
}
float wakeFbm(vec3 p) {
    return 0.57*wakeNoise(p) + 0.28*wakeNoise(p*2.07+11.3) + 0.15*wakeNoise(p*4.19+27.7);
}
