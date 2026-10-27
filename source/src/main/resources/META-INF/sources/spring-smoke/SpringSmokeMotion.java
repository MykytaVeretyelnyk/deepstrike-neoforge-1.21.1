/*
 * Derived from Recoil CSimpleParticleSystem::Init/Update, GPL-2.0-or-later.
 * Copyright Spring/Recoil contributors; pinned bb3ff796755a74add1c624153c808edffe173347.
 * Emission parameters from Zero-K rocket_trail_bar smoke in missiletrail.lua.
 * Modifications: normalized host basis, stable host random numbers, world-unit
 * scale; closed-form evaluation of the original 30 Hz recurrence and draw offset.
 * Rotation uses Wildfire Games / 0 A.D. cloud.xml and ParticleEmitterType.cpp
 * (GPL-2.0-or-later code, CC BY-SA 3.0 art configuration), pinned 61a3b950.
 */
package dev.sbw.deepstrike.client;

final class SpringSmokeMotion {
    static final double AIR_DRAG = .92;
    static final double SOURCE_HZ = 30;
    record Emission(double axial, double across, double up) {}

    static Emission emit(double scale, int seed) {
        double az = unit(seed, 0) * Math.PI * 2;
        double ay = Math.toRadians(-180 + 17 * unit(seed, 1));
        double speed = (1.5 + 2.5 * unit(seed, 2)) * scale;
        return new Emission(Math.cos(ay)*speed,
                -Math.cos(az)*Math.sin(ay)*speed,
                Math.sin(az)*Math.sin(ay)*speed);
    }

    // Source order: pos += speed; speed += gravity; speed *= drag.
    // A partial frame draws pos + speed * frameOffset, as in the original Draw.
    static double velocityDistance(double elapsed, double drag) {
        double n = Math.floor(Math.max(0, elapsed)), partial = Math.max(0, elapsed)-n;
        if (drag == 1) return n+partial;
        double power = Math.pow(drag,n);
        return (1-power)/(1-drag) + partial*power;
    }

    static double gravityDistance(double elapsed, double drag) {
        double n = Math.floor(Math.max(0, elapsed)), partial = Math.max(0, elapsed)-n;
        if (drag == 1) return n*(n-1)*.5 + partial*n;
        double power = Math.pow(drag,n), sum = (1-power)/(1-drag);
        return drag/(1-drag)*(n-sum) + partial*drag*(1-power)/(1-drag);
    }

    static double angle(int seed, double seconds) {
        // cloud.xml: angle uniform[-10,10], angular velocity uniform[-.05,.05].
        // ParticleEmitterType::UpdateEmitterStep: angle += angleSpeed * dt.
        return (-10+20*unit(seed,3)) + (-.05+.1*unit(seed,4))*seconds;
    }

    static int seed(float distance, int trail, int strand) {
        return Float.floatToIntBits(distance) ^ trail ^ (strand*0x9e3779b9);
    }

    static double unit(int seed, int channel) {
        // Host random-number adapter. Each deposited particle retains its samples.
        int x = seed + channel*0x9e3779b9;
        x ^= x >>> 16; x *= 0x7feb352d;
        x ^= x >>> 15; x *= 0x846ca68b;
        x ^= x >>> 16;
        return (x & 0xffffffffL) * 0x1.0p-32;
    }
    private SpringSmokeMotion() {}
}
