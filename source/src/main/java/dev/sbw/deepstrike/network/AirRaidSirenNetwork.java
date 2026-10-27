package dev.sbw.deepstrike.network;

import dev.sbw.deepstrike.game.AirRaidSirenBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import dev.sbw.deepstrike.compat.net.NetworkDirection;
import dev.sbw.deepstrike.compat.net.NetworkRegistry;
import dev.sbw.deepstrike.compat.net.NetworkEvent.Context;
import dev.sbw.deepstrike.compat.net.SimpleChannel;

public final class AirRaidSirenNetwork {
   private static final String PROTOCOL = "1";
   private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
      ResourceLocation.fromNamespaceAndPath("deepstrike", "sirens"), () -> "1", "1"::equals, "1"::equals
   );

   public static void register() {
      CHANNEL.messageBuilder(AirRaidSirenNetwork.Configure.class, 0, NetworkDirection.PLAY_TO_SERVER)
         .encoder(AirRaidSirenNetwork.Configure::encode)
         .decoder(AirRaidSirenNetwork.Configure::decode)
         .consumerMainThread((message, context) -> AirRaidSirenNetwork.Configure.handle(message, (Context)context.get()))
         .add();
   }

   public static void configure(BlockPos pos, int variant, int audibleRadius, boolean zoneOnly, double zoneX, double zoneZ, int zoneRadius) {
      CHANNEL.sendToServer(new AirRaidSirenNetwork.Configure(pos, variant, audibleRadius, zoneOnly, zoneX, zoneZ, zoneRadius));
   }

   private AirRaidSirenNetwork() {
   }

   public record Configure(BlockPos pos, int variant, int audibleRadius, boolean zoneOnly, double zoneX, double zoneZ, int zoneRadius) {
      static void encode(AirRaidSirenNetwork.Configure message, FriendlyByteBuf buffer) {
         buffer.writeBlockPos(message.pos);
         buffer.writeVarInt(message.variant);
         buffer.writeVarInt(message.audibleRadius);
         buffer.writeBoolean(message.zoneOnly);
         buffer.writeDouble(message.zoneX);
         buffer.writeDouble(message.zoneZ);
         buffer.writeVarInt(message.zoneRadius);
      }

      static AirRaidSirenNetwork.Configure decode(FriendlyByteBuf buffer) {
         return new AirRaidSirenNetwork.Configure(
            buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readDouble(), buffer.readDouble(), buffer.readVarInt()
         );
      }

      static void handle(AirRaidSirenNetwork.Configure message, Context context) {
         ServerPlayer player = context.getSender();
         if (player != null && !(player.distanceToSqr(message.pos.getX() + 0.5, message.pos.getY() + 0.5, message.pos.getZ() + 0.5) > 64.0)) {
            if (player.level().getBlockEntity(message.pos) instanceof AirRaidSirenBlockEntity siren) {
               siren.configure(message.variant, message.audibleRadius, message.zoneOnly, message.zoneX, message.zoneZ, message.zoneRadius);
            }
         }
      }
   }
}
