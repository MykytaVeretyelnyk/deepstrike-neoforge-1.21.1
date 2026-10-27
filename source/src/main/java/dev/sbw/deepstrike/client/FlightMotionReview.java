package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.game.GuidedMissileEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

final class FlightMotionReview {
   private static final boolean ENABLED = Boolean.getBoolean("deepstrike.reviewMotionTrace");
   private static final StringBuilder CSV = new StringBuilder("nanos,id,kind,age,x,y,z,vx,vy,vz,yaw,pitch,intercepted\n");
   private static int rows;

   static void sample(Collection<GuidedMissileEntity> flights) {
      if (ENABLED && rows < 100000) {
         long now = System.nanoTime();

         for (GuidedMissileEntity e : flights) {
            Vec3 v = e.getDeltaMovement();
            CSV.append(now)
               .append(',')
               .append(e.getId())
               .append(',')
               .append(e.kind())
               .append(',')
               .append(e.tickCount)
               .append(',')
               .append(e.getX())
               .append(',')
               .append(e.getY())
               .append(',')
               .append(e.getZ())
               .append(',')
               .append(v.x)
               .append(',')
               .append(v.y)
               .append(',')
               .append(v.z)
               .append(',')
               .append(e.getYRot())
               .append(',')
               .append(e.getXRot())
               .append(',')
               .append(e.isIntercepted())
               .append('\n');
            rows++;
         }
      }
   }

   static void finish() {
      if (ENABLED) {
         Path path = Minecraft.getInstance()
            .gameDirectory
            .toPath()
            .resolve("screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase") + "/motion.csv");

         try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, CSV);
         } catch (IOException var2) {
            throw new IllegalStateException("Cannot write motion evidence", var2);
         }
      }
   }

   private FlightMotionReview() {
   }
}
