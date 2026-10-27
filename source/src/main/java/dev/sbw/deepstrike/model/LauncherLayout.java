package dev.sbw.deepstrike.model;

import dev.sbw.deepstrike.game.LauncherBlock;
import dev.sbw.deepstrike.game.MissileKind;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

public final class LauncherLayout {
   public static Matrix4f root(LauncherBlock.Family family, float facingYaw) {
      return family == LauncherBlock.Family.PEACEKEEPER
         ? new Matrix4f().translation(0.5F, 0.0F, 0.5F).rotateY((float)Math.toRadians(-facingYaw))
         : new Matrix4f().translation(0.5F, family == LauncherBlock.Family.JAGM ? 0.04F : 0.75F, 0.5F).rotateY((float)Math.toRadians(90.0F - facingYaw));
   }

   public static Matrix4f baseMount(LauncherBlock.Family family, float facingYaw, Map<String, Matrix4f> transforms) {
      return family == LauncherBlock.Family.PEACEKEEPER
         ? root(family, facingYaw)
         : root(family, facingYaw).mul((Matrix4fc)transforms.get(family == LauncherBlock.Family.JAGM ? "cradle_pitch" : "launch_frame"));
   }

   public static Matrix4f slotMount(LauncherBlock.Family family, Matrix4f baseMount, int slot) {
      Matrix4f result = new Matrix4f(baseMount);
      if (family == LauncherBlock.Family.PEACEKEEPER) {
         result.translate(0.0F, (float)MissileGeometry.tailDistance(MissileKind.PEACEKEEPER) + 0.9F, 0.0F);
      } else if (family == LauncherBlock.Family.JAGM) {
         result.translate(0.03125F, (24.1F + slot / 2 * 8) / 16.0F, (-5.05F + slot % 2 * 10.1F) / 16.0F);
      } else {
         result.translate(0.0F, 0.7625F, 0.0F);
      }

      return result;
   }

   public static Matrix4f mount(LauncherBlock.Family family, float facingYaw, float yaw, float elevation, int slot) {
      ModelGeometry model = ModelGeometry.get(ModelAssets.launcher(family));
      Map<String, Matrix4f> t = model.transforms(yaw, elevation);
      return slotMount(family, baseMount(family, facingYaw, t), slot);
   }

   public static Vec3 worldPosition(BlockPos pos, Matrix4f transform) {
      Vector3f p = transform.transformPosition(new Vector3f());
      return new Vec3(pos.getX() + p.x, pos.getY() + p.y, pos.getZ() + p.z);
   }

   public static Vec3 forward(LauncherBlock.Family family, Matrix4f transform) {
      Vector3f localDir = family == LauncherBlock.Family.PEACEKEEPER ? new Vector3f(0.0F, 1.0F, 0.0F) : new Vector3f(-1.0F, 0.0F, 0.0F);
      Vector3f d = transform.transformDirection(localDir).normalize();
      return new Vec3(d.x, d.y, d.z);
   }

   public static double verticalClearance(LauncherBlock.Family family, MissileKind kind) {
      ModelGeometry model = ModelGeometry.get(ModelAssets.launcher(family));
      Map<String, Matrix4f> transforms = model.transforms(0.0F, 0.0F);
      Matrix4f root = root(family, 0.0F);
      double ceiling = 0.0;

      for (ModelGeometry.Bone bone : model.bones) {
         for (ModelGeometry.Cube cube : bone.cubes()) {
            Matrix4f matrix = new Matrix4f(root).mul((Matrix4fc)transforms.get(bone.name())).mul(cube.transform());

            for (int i = 0; i < 8; i++) {
               Vector3f vertex = new Vector3f(cube.min())
                  .add((i & 1) == 0 ? 0.0F : cube.size().x, (i & 2) == 0 ? 0.0F : cube.size().y, (i & 4) == 0 ? 0.0F : cube.size().z);
               ceiling = Math.max(ceiling, (double)matrix.transformPosition(vertex).y);
            }
         }
      }

      Vec3 seated = worldPosition(BlockPos.ZERO, mount(family, 0.0F, 0.0F, 0.0F, 0));
      double tailFloor = MissileGeometry.worldBounds(kind, seated, new Vec3(0.0, 1.0, 0.0), 0.0F, -90.0F).minY;
      return Math.max(0.0, ceiling - tailFloor) + 2.0;
   }

   public static Vec3 forward(Matrix4f transform) {
      return forward(LauncherBlock.Family.FP5, transform);
   }
}
