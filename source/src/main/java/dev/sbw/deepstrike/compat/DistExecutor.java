package dev.sbw.deepstrike.compat;

import java.util.concurrent.Callable;
import java.util.function.Supplier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/** Minimal stand-in for the DistExecutor helper that NeoForge removed. */
public final class DistExecutor {
   private DistExecutor() {
   }

   public static void unsafeRunWhenOn(Dist dist, Supplier<Runnable> toRun) {
      if (FMLEnvironment.dist == dist) {
         toRun.get().run();
      }
   }

   public static void safeRunWhenOn(Dist dist, Supplier<Runnable> toRun) {
      unsafeRunWhenOn(dist, toRun);
   }

   public static <T> T unsafeCallWhenOn(Dist dist, Supplier<Callable<T>> toRun) {
      if (FMLEnvironment.dist == dist) {
         try {
            return toRun.get().call();
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }
      return null;
   }

   public static <T> T unsafeRunForDist(Supplier<Supplier<T>> client, Supplier<Supplier<T>> server) {
      return FMLEnvironment.dist == Dist.CLIENT ? client.get().get() : server.get().get();
   }

   public static <T> T safeRunForDist(Supplier<Supplier<T>> client, Supplier<Supplier<T>> server) {
      return unsafeRunForDist(client, server);
   }
}
