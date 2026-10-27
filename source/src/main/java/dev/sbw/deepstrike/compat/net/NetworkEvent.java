package dev.sbw.deepstrike.compat.net;

import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class NetworkEvent {
   private NetworkEvent() {
   }

   public static final class Context {
      private final IPayloadContext ctx;
      private final NetworkDirection direction;

      Context(IPayloadContext ctx, NetworkDirection direction) {
         this.ctx = ctx;
         this.direction = direction;
      }

      public ServerPlayer getSender() {
         return this.ctx.player() instanceof ServerPlayer sp ? sp : null;
      }

      public CompletableFuture<Void> enqueueWork(Runnable work) {
         return this.ctx.enqueueWork(work);
      }

      public void setPacketHandled(boolean handled) {
      }

      public NetworkDirection getDirection() {
         return this.direction;
      }
   }
}
