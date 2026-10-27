package dev.sbw.deepstrike.compat.net;

import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;

public final class NetworkRegistry {
   private NetworkRegistry() {
   }

   public static SimpleChannel newSimpleChannel(ResourceLocation name, Supplier<String> version, Predicate<String> client, Predicate<String> server) {
      return new SimpleChannel(name, version.get());
   }
}
