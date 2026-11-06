package dev.sbw.deepstrike.game;

import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.item.misc.MonitorItem;
import dev.sbw.deepstrike.compat.NbtCompat;
import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import java.util.Comparator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Links a Superb Warfare monitor to a DeepStrike launcher. Sneak + right click on the launcher stores its position under the
 * "DeepStrikeLauncher" key of the monitor's custom_data; SBW only reads Linked / LinkedDrone / Using / Pos*, so the extra key does not
 * disturb its drone logic. Using the linked monitor (right click in the air) opens the camera feed of the missile that is flying
 * from that launcher, through the same CameraTarget packet as the JAGM camera tablet.
 */
@EventBusSubscriber(
   modid = "deepstrike"
)
public final class SbwMonitorLink {
   private static final String KEY = "DeepStrikeLauncher";

   private SbwMonitorLink() {
   }

   private static boolean isMonitor(ItemStack stack) {
      return stack.is(ModItems.MONITOR.get());
   }

   @SubscribeEvent
   public static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
      ItemStack stack = event.getItemStack();
      Player player = event.getEntity();
      if (isMonitor(stack) && player.isShiftKeyDown()) {
         BlockPos master = launcherMaster(event.getLevel().getBlockEntity(event.getPos()));
         if (master != null) {
            if (!event.getLevel().isClientSide) {
               String dimension = event.getLevel().dimension().location().toString();
               NbtCompat.update(stack, tag -> {
                  if (tag.getBoolean("Linked")) {
                     MonitorItem.disLink(tag, player);
                  }

                  CompoundTag link = new CompoundTag();
                  link.putLong("Pos", master.asLong());
                  link.putString("Dim", dimension);
                  tag.put(KEY, link);
               });
               player.displayClientMessage(
                  Component.translatable("message.deepstrike.monitor.linked", new Object[]{master.getX(), master.getY(), master.getZ()}), true
               );
            }

            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
         }
      }
   }

   private static BlockPos launcherMaster(BlockEntity entity) {
      return entity instanceof LauncherBlockEntity launcher
         ? launcher.getBlockPos()
         : (entity instanceof LauncherPartBlockEntity part ? part.master() : null);
   }

   @SubscribeEvent
   public static void onUse(PlayerInteractEvent.RightClickItem event) {
      ItemStack stack = event.getItemStack();
      if (!event.getLevel().isClientSide && isMonitor(stack) && event.getEntity() instanceof ServerPlayer player) {
         CompoundTag tag = NbtCompat.getTag(stack);
         // a monitor that SBW has linked to a drone stays under SBW's control
         if (tag != null && tag.contains(KEY, 10) && !tag.getBoolean("Linked")) {
            CompoundTag link = tag.getCompound(KEY);
            BlockPos launcher = BlockPos.of(link.getLong("Pos"));
            ServerLevel level = player.serverLevel();
            GuidedMissileEntity missile = !level.dimension().location().toString().equals(link.getString("Dim"))
               ? null
               : level.getEntities(
                     EntityTypeTest.forClass(GuidedMissileEntity.class),
                     e -> e.isAlive() && e.cameraAvailable() && launcher.equals(e.launcherPos()) && player.getUUID().equals(e.ownerId())
                  )
                  .stream()
                  .min(Comparator.comparingInt(e -> e.tickCount))
                  .orElse(null);
            if (missile == null) {
               player.displayClientMessage(Component.translatable("message.deepstrike.monitor.no_feed"), true);
            } else {
               DeepStrikeNetwork.sendCameraTarget(player, missile.getId());
            }

            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
         }
      }
   }

   @SubscribeEvent
   public static void onTooltip(ItemTooltipEvent event) {
      if (isMonitor(event.getItemStack())) {
         CompoundTag tag = NbtCompat.getTag(event.getItemStack());
         if (tag != null && tag.contains(KEY, 10)) {
            BlockPos pos = BlockPos.of(tag.getCompound(KEY).getLong("Pos"));
            event.getToolTip()
               .add(
                  Component.translatable("tooltip.deepstrike.monitor.launcher", new Object[]{pos.getX(), pos.getY(), pos.getZ()})
                     .withStyle(ChatFormatting.AQUA)
               );
         }
      }
   }
}
