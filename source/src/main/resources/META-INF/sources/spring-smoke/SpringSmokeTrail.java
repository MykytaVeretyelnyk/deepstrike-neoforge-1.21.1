/*
 * Derived from Spring/Recoil CSmokeTrailProjectile (GPL-2.0-or-later).
 * Copyright the Spring/Recoil contributors; see META-INF/licenses/spring-smoke-GPL-2.0.txt.
 * Original: RecoilEngine bb3ff796755a74add1c624153c808edffe173347,
 * rts/Rendering/Env/Particles/Classes/SmokeTrailProjectile.cpp.
 * Modified 2026-10-09: C++ -> Java/Forge; external width/lifetime/UV inputs,
 * straight-alpha vertex colour, safe degenerate directions, Minecraft lightmap.
 * The .98 turn threshold, .33 Bezier handles, midpoint subdivision,
 * camera cross products and .7 + abs(dot) opacity come from that source.
 */
package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class SpringSmokeTrail {
    record Point(Vec3 position, Vec3 direction, float width, float age, float alpha, float u, int light) {}

    static void draw(VertexConsumer out, PoseStack.Pose pose, Vec3 camera,
                     Point older, Point newer, float red, float green, float blue, int seed) {
        // Source pos1 is the newest endpoint, pos2 the oldest endpoint.
        Vec3 dif1 = newer.position.subtract(camera).normalize();
        Vec3 dif2 = older.position.subtract(camera).normalize();
        Vec3 odir1 = side(dif1, newer.direction);
        Vec3 odir2 = side(dif2, older.direction);
        if (odir2.dot(odir1) < 0) odir2 = odir2.scale(-1);
        float opacity1 = opacity(newer, dif1);
        float opacity2 = opacity(older, dif2);

        if (newer.direction.dot(older.direction) < .98) {
            double dist = newer.position.distanceTo(older.position);
            Vec3 dirpos1 = newer.position.subtract(newer.direction.scale(dist * .33));
            Vec3 dirpos2 = older.position.add(older.direction.scale(dist * .33));
            // CalcBeizer(.5, pos1, dirpos1, dirpos2, pos2), expanded exactly.
            Vec3 midpos = newer.position.add(older.position).scale(.125)
                    .add(dirpos1.add(dirpos2).scale(.375));
            Vec3 middir = newer.direction.add(older.direction).normalize();
            Point middle = new Point(midpos, middir, (newer.width + older.width) * .5f,
                    (newer.age + older.age) * .5f, (newer.alpha + older.alpha) * .5f,
                    (newer.u + older.u) * .5f, newer.light);
            Vec3 difm = midpos.subtract(camera).normalize();
            Vec3 odirm = side(difm, middir);
            if (odirm.dot(odir1) < 0) odirm = odirm.scale(-1);
            float opacitym = opacity(middle, difm);
            quad(out, pose, camera, newer, middle, odir1, odirm, opacity1, opacitym, red, green, blue, seed);
            quad(out, pose, camera, middle, older, odirm, odir2, opacitym, opacity2, red, green, blue, seed);
        } else {
            quad(out, pose, camera, newer, older, odir1, odir2, opacity1, opacity2, red, green, blue, seed);
        }
    }

    private static float opacity(Point p, Vec3 dif) {
        // Host alpha supplies the requested longer hold/fade instead of source 1-t.
        return Mth.clamp(p.alpha * (.7f + (float)Math.abs(dif.dot(p.direction))), 0, 1);
    }

    private static Vec3 side(Vec3 dif, Vec3 direction) {
        Vec3 side = dif.cross(direction);
        if (side.lengthSqr() < 1e-10)
            side = direction.cross(Math.abs(direction.y) > .9 ? new Vec3(1,0,0) : new Vec3(0,1,0));
        return side.normalize();
    }

    private static void quad(VertexConsumer out, PoseStack.Pose pose, Vec3 camera,
                             Point a, Point b, Vec3 acrossA, Vec3 acrossB, float alphaA, float alphaB,
                             float red, float green, float blue, int seed) {
        vertex(out,pose,camera,a,acrossA,-1,alphaA,red,green,blue,seed);
        vertex(out,pose,camera,b,acrossB,-1,alphaB,red,green,blue,seed);
        vertex(out,pose,camera,b,acrossB, 1,alphaB,red,green,blue,seed);
        vertex(out,pose,camera,a,acrossA, 1,alphaA,red,green,blue,seed);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vec3 camera, Point p, Vec3 side,
                               float sign, float alpha, float red, float green, float blue, int seed) {
        Vec3 pos = p.position.add(side.scale(sign * p.width)).subtract(camera);
        out.vertex(pose.pose(),(float)pos.x,(float)pos.y,(float)pos.z)
                .color(red,green,blue,alpha).uv(p.u,sign)
                .overlayCoords(seed,Math.min(32767,(int)(p.age*20))).uv2(p.light)
                .normal(0,1,0).endVertex();
    }

    private SpringSmokeTrail() {}
}
