package dev.sbw.deepstrike.network;

import dev.sbw.deepstrike.compat.NbtCompat;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import dev.sbw.deepstrike.client.UnitJournalScreen;
import dev.sbw.deepstrike.game.LaunchJournal;
import dev.sbw.deepstrike.game.LauncherBlockEntity;
import dev.sbw.deepstrike.registry.BFItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import dev.sbw.deepstrike.compat.DistExecutor;
import dev.sbw.deepstrike.compat.net.NetworkDirection;
import dev.sbw.deepstrike.compat.net.NetworkRegistry;
import dev.sbw.deepstrike.compat.net.PacketDistributor;
import dev.sbw.deepstrike.compat.net.NetworkEvent.Context;
import dev.sbw.deepstrike.compat.net.SimpleChannel;

public final class UnitJournalNetwork {
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
      ResourceLocation.fromNamespaceAndPath("deepstrike", "unit_journal"), () -> "3", "3"::equals, "3"::equals
   );

   public static void register() {
      CHANNEL.messageBuilder(UnitJournalNetwork.RequestBlock.class, 2, NetworkDirection.PLAY_TO_SERVER)
         .encoder((m, b) -> {
            b.writeBlockPos(m.position);
            b.writeInt(m.page);
         })
         .decoder(b -> new UnitJournalNetwork.RequestBlock(b.readBlockPos(), b.readInt()))
         .consumerMainThread(
            (m, c) -> {
               ServerPlayer player = ((Context)c.get()).getSender();
               if (player != null && m.page >= 0 && m.page <= 100000 && !(player.distanceToSqr(Vec3.atCenterOf(m.position)) > 1.6777216E7)) {
                  if (player.serverLevel().getBlockEntity(m.position) instanceof LauncherBlockEntity launcher) {
                     CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new UnitJournalNetwork.Snapshot(-1, m.page, LaunchJournal.get(player.serverLevel()).page(launcher.journalId(), m.page))
                     );
                  }
               }
            }
         )
         .add();
      CHANNEL.messageBuilder(UnitJournalNetwork.Request.class, 0, NetworkDirection.PLAY_TO_SERVER)
         .encoder((m, b) -> {
            b.writeInt(m.entity);
            b.writeInt(m.page);
         })
         .decoder(b -> new UnitJournalNetwork.Request(b.readInt(), b.readInt()))
         .consumerMainThread(
            (m, c) -> {
               ServerPlayer player = ((Context)c.get()).getSender();
               if (player != null && m.page >= 0 && m.page <= 100000) {
                  Entity entity = player.serverLevel().getEntity(m.entity);
                  if (entity != null && EntityType.getKey(entity.getType()).getNamespace().equals("deepstrike")) {
                     boolean allowed = player.distanceToSqr(entity) <= 1.6777216E7 || player.getVehicle() == entity;
                     if (!allowed) {
                        for (ItemStack stack : player.getInventory().items) {
                           if (NbtCompat.hasTag(stack)
                              && NbtCompat.getTag(stack).hasUUID("TargetUUID")
                              && NbtCompat.getTag(stack).getUUID("TargetUUID").equals(entity.getUUID())) {
                              allowed = true;
                              break;
                           }
                        }
                     }

                     if (allowed) {
                        CHANNEL.send(
                           PacketDistributor.PLAYER.with(() -> player),
                           new UnitJournalNetwork.Snapshot(m.entity, m.page, LaunchJournal.get(player.serverLevel()).page(LaunchJournal.id(entity), m.page))
                        );
                     }
                  }
               }
            }
         )
         .add();
      CHANNEL.messageBuilder(UnitJournalNetwork.Snapshot.class, 1, NetworkDirection.PLAY_TO_CLIENT)
         .encoder((m, b) -> {
            b.writeInt(m.entity);
            b.writeInt(m.page);
            b.writeCollection(m.entries, (buf, line) -> buf.writeUtf(line, 2048));
         })
         .decoder(b -> new UnitJournalNetwork.Snapshot(b.readInt(), b.readInt(), b.readList(buf -> buf.readUtf(2048))))
         .consumerMainThread((m, c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> UnitJournalScreen.accept(m)))
         .add();
   }

   public static void interact(EntityInteract event) {
      Player player = event.getEntity();
      Entity unit = event.getTarget();
      if (player.isShiftKeyDown()
         && player.getItemInHand(event.getHand()).is((Item)BFItems.AIR_DEFENSE_TERMINAL.get())
         && unit instanceof VehicleEntity
         && EntityType.getKey(unit.getType()).getNamespace().equals("deepstrike")) {
         if (player.level().isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> UnitJournalScreen.open(unit.getId()));
         }

         event.setCanceled(true);
         event.setCancellationResult(InteractionResult.SUCCESS);
      }
   }

   public record Request(int entity, int page) {
   }

   public record RequestBlock(BlockPos position, int page) {
   }

   public record Snapshot(int entity, int page, List<String> entries) {
   }
}
