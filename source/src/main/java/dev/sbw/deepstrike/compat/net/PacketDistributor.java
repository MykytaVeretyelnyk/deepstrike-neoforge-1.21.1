package dev.sbw.deepstrike.compat.net;

import java.util.function.Supplier;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class PacketDistributor<T> {
   public static final PacketDistributor<ServerPlayer> PLAYER = new PacketDistributor<>(0);
   public static final PacketDistributor<ResourceKey<Level>> DIMENSION = new PacketDistributor<>(1);
   public static final PacketDistributor<Void> ALL = new PacketDistributor<>(2);
   public static final PacketDistributor<Entity> TRACKING_ENTITY = new PacketDistributor<>(3);
   public static final PacketDistributor<Entity> TRACKING_ENTITY_AND_SELF = new PacketDistributor<>(4);
   private final int kind;

   private PacketDistributor(int kind) {
      this.kind = kind;
   }

   public PacketTarget with(Supplier<T> arg) {
      return new PacketTarget(this.kind, arg.get());
   }

   public PacketTarget noArg() {
      return new PacketTarget(this.kind, null);
   }

   public static final class PacketTarget {
      private final int kind;
      private final Object arg;

      PacketTarget(int kind, Object arg) {
         this.kind = kind;
         this.arg = arg;
      }

      @SuppressWarnings("unchecked")
      void send(CustomPacketPayload payload) {
         switch (this.kind) {
            case 0 -> {
               if (this.arg instanceof ServerPlayer p) {
                  net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, payload);
               }
            }
            case 1 -> {
               MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
               ServerLevel level = server == null ? null : server.getLevel((ResourceKey<Level>)this.arg);
               if (level != null) {
                  net.neoforged.neoforge.network.PacketDistributor.sendToPlayersInDimension(level, payload);
               }
            }
            case 2 -> net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(payload);
            case 3 -> net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity((Entity)this.arg, payload);
            case 4 -> net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf((Entity)this.arg, payload);
            default -> throw new IllegalStateException();
         }
      }
   }
}
