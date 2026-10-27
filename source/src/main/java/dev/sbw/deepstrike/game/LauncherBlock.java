package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.compat.NbtCompat;
import dev.sbw.deepstrike.client.DeepStrikeMapScreen;
import dev.sbw.deepstrike.menu.LauncherMenu;
import dev.sbw.deepstrike.registry.BFBlocks;
import dev.sbw.deepstrike.registry.BFItems;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import dev.sbw.deepstrike.compat.DistExecutor;

public final class LauncherBlock extends BaseEntityBlock {
   @Override
   protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
      return com.mojang.serialization.MapCodec.unit(this);
   }

   public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
   private final LauncherBlock.Family family;
   private final boolean graphite;

   public LauncherBlock(LauncherBlock.Family family, Properties properties) {
      this(family, false, properties);
   }

   public LauncherBlock(LauncherBlock.Family family, boolean graphite, Properties properties) {
      super(properties);
      this.family = family;
      this.graphite = graphite;
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH));
   }

   public LauncherBlock.Family family() {
      return this.family;
   }

   public boolean graphite() {
      return this.graphite;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{FACING});
   }

   public BlockState getStateForPlacement(BlockPlaceContext context) {
      return (BlockState)this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
   }

   public boolean hasAnalogOutputSignal(BlockState state) {
      return true;
   }

   public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
      return level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof LauncherBlockEntity be
         ? CommandCenterData.get(server).unitOutput(be.journalId())
         : 0;
   }

   public RenderShape getRenderShape(BlockState state) {
      return RenderShape.INVISIBLE;
   }

   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return level.getBlockEntity(pos) instanceof LauncherBlockEntity be ? be.collisionShape() : Shapes.empty();
   }

   public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return this.getShape(state, level, pos, context);
   }

   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new LauncherBlockEntity(pos, state);
   }

   @Nullable
   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
      return level.isClientSide
         ? createTickerHelper(type, (BlockEntityType)BFBlocks.LAUNCHER_BE.get(), LauncherBlockEntity::clientTick)
         : createTickerHelper(type, (BlockEntityType)BFBlocks.LAUNCHER_BE.get(), LauncherBlockEntity::serverTick);
   }

   public List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
      ItemStack item = new ItemStack(state.getBlock());
      BlockEntity be = (BlockEntity)builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
      if (be instanceof LauncherBlockEntity launcher) {
         NbtCompat.update(item, t -> t.put("BlockEntityTag", launcher.itemState(true)));
      }

      return List.of(item);
   }

   public ItemStack getCloneItemStack(BlockState state, HitResult hit, net.minecraft.world.level.LevelReader level, BlockPos pos, Player player) {
      ItemStack stack = new ItemStack(this);
      if (level.getBlockEntity(pos) instanceof LauncherBlockEntity launcher) {
         NbtCompat.update(stack, t -> t.put("BlockEntityTag", launcher.itemState(false)));
      }

      return stack;
   }

   public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack item) {
      super.setPlacedBy(level, pos, state, placer, item);
      if (level.getBlockEntity(pos) instanceof LauncherBlockEntity launcher) {
         launcher.placed(placer, item);
      }
   }

   public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
      if (!state.is(next.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof LauncherBlockEntity launcher) {
         launcher.destroyed();
         LauncherCollision.remove(launcher);
      }

      super.onRemove(state, level, pos, next, moving);
   }

   public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      InteractionHand hand = InteractionHand.MAIN_HAND;
      ItemStack held = player.getItemInHand(hand);
      if (held.is((Item)BFItems.JAGM_CAMERA_TABLET.get())) {
         if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof LauncherBlockEntity be) {
            String error = CommandCenterData.get(sp.serverLevel()).bind(sp, be);
            sp.displayClientMessage(Component.translatable("command.deepstrike." + (error.isEmpty() ? "bound" : "error." + error)), true);
         }

         return InteractionResult.sidedSuccess(level.isClientSide);
      } else if (this.family == LauncherBlock.Family.PEACEKEEPER && level.getBlockEntity(pos) instanceof LauncherBlockEntity platform && platform.refuel(player, hand)
         )
       {
         return InteractionResult.sidedSuccess(level.isClientSide);
      } else if (!(held.getItem() instanceof MissileItem missile)) {
         if (player.isShiftKeyDown() && held.isEmpty() && level.getBlockEntity(pos) instanceof LauncherBlockEntity launcher && !launcher.loaded().isEmpty()) {
            if (!level.isClientSide) {
               launcher.unload(player);
            }

            return InteractionResult.sidedSuccess(level.isClientSide);
         } else if (held.is((Item)BFItems.DEEPSTRIKE.get())) {
            if (level.isClientSide) {
               DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DeepStrikeMapScreen.open(pos, (Direction)state.getValue(FACING), this.family));
            }

            return InteractionResult.sidedSuccess(level.isClientSide);
         } else if (level.getBlockEntity(pos) instanceof LauncherBlockEntity be) {
            if (player instanceof ServerPlayer sp) {
               sp.openMenu(
                  new SimpleMenuProvider(
                     (id, inventory, p) -> new LauncherMenu(id, inventory, be),
                     Component.literal(
                        this.family == LauncherBlock.Family.JAGM
                           ? "JAGM / 6"
                           : (this.family == LauncherBlock.Family.PEACEKEEPER ? "LGM-118 Peacekeeper" : "FP-5 / Storm Shadow")
                     )
                  ),
                  pos
               );
            }

            return InteractionResult.sidedSuccess(level.isClientSide);
         } else {
            return InteractionResult.PASS;
         }
      } else {
         boolean accepted = switch (this.family) {
            case FP5 -> missile.kind() == MissileKind.FP5 || missile.kind() == MissileKind.STORM_SHADOW;
            case JAGM -> missile.kind() == MissileKind.JAGM;
            case PEACEKEEPER -> missile.kind() == MissileKind.PEACEKEEPER;
         };
         if (!accepted) {
            return InteractionResult.FAIL;
         } else if (!(!level.isClientSide && level.getBlockEntity(pos) instanceof LauncherBlockEntity launcher)) {
            return InteractionResult.SUCCESS;
         } else {
            return launcher.load(held, player) ? InteractionResult.CONSUME : InteractionResult.FAIL;
         }
      }
   }

   public static enum Family {
      FP5,
      JAGM,
      PEACEKEEPER;
   }
}
