package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.compat.NbtCompat;
import dev.sbw.deepstrike.registry.BFBlocks;
import dev.sbw.deepstrike.registry.BFItems;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.BlockHitResult;

public final class CommandRelayBlock extends BaseEntityBlock {
   @Override
   protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
      return com.mojang.serialization.MapCodec.unit(this);
   }

   public CommandRelayBlock(Properties properties) {
      super(properties);
   }

   public RenderShape getRenderShape(BlockState state) {
      return RenderShape.MODEL;
   }

   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new CommandRelayBlock.Relay(pos, state);
   }

   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
      return level.isClientSide ? null : createTickerHelper(type, (BlockEntityType)BFBlocks.COMMAND_RELAY_BE.get(), CommandRelayBlock.Relay::tick);
   }

   public boolean hasAnalogOutputSignal(BlockState state) {
      return true;
   }

   public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
      return level.getBlockEntity(pos) instanceof CommandRelayBlock.Relay relay ? relay.output : 0;
   }

   public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      InteractionHand hand = InteractionHand.MAIN_HAND;
      if (!player.getItemInHand(hand).is((Item)BFItems.JAGM_CAMERA_TABLET.get())) {
         return InteractionResult.PASS;
      } else {
         if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof CommandRelayBlock.Relay relay) {
            CompoundTag tag = NbtCompat.getOrCreateTag(player.getItemInHand(hand));
            CommandCenterData data = CommandCenterData.get(sp.serverLevel());
            if (!tag.hasUUID("CommandNetwork")
               || !tag.hasUUID("CommandTask")
               || !data.bindRelay(sp, tag.getUUID("CommandNetwork"), tag.getUUID("CommandTask"))) {
               sp.displayClientMessage(Component.translatable("command.deepstrike.error.select_relay"), true);
            } else if (relay.network != null && !data.bindRelay(sp, relay.network, relay.task)) {
               sp.displayClientMessage(Component.translatable("command.deepstrike.error.access"), true);
            } else {
               relay.network = tag.getUUID("CommandNetwork");
               relay.task = tag.getUUID("CommandTask");
               data.rememberRelay(sp, relay.network, relay.task, pos);
               relay.gate = new RedstonePulseGate();
               relay.setChanged();
               sp.displayClientMessage(Component.translatable("command.deepstrike.relay_bound"), true);
            }
         }

         return InteractionResult.sidedSuccess(level.isClientSide);
      }
   }

   public static final class Relay extends BlockEntity {
      private UUID network;
      private UUID task;
      private RedstonePulseGate gate = new RedstonePulseGate();
      private int output;

      public Relay(BlockPos pos, BlockState state) {
         super((BlockEntityType)BFBlocks.COMMAND_RELAY_BE.get(), pos, state);
      }

      public static void tick(Level level, BlockPos pos, BlockState state, CommandRelayBlock.Relay relay) {
         CommandCenterData data = CommandCenterData.get((ServerLevel)level);
         if (relay.gate.sample(level.hasNeighborSignal(pos), level.getGameTime()) && relay.network != null && relay.task != null) {
            data.relay((ServerLevel)level, relay.network, relay.task, pos);
         }

         int next = relay.network == null ? 0 : data.relayOutput(relay.network, relay.task);
         if (next != relay.output) {
            relay.output = next;
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
         }
      }

      protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
         super.saveAdditional(tag, registries);
         if (this.network != null) {
            tag.putUUID("Network", this.network);
         }

         if (this.task != null) {
            tag.putUUID("Task", this.task);
         }
      }

      public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
         super.loadAdditional(tag, registries);
         this.network = tag.hasUUID("Network") ? tag.getUUID("Network") : null;
         this.task = tag.hasUUID("Task") ? tag.getUUID("Task") : null;
         this.gate = new RedstonePulseGate();
      }
   }
}
