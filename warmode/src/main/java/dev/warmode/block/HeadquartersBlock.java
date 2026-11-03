package dev.warmode.block;

import com.mojang.serialization.MapCodec;
import dev.warmode.game.WarData;
import dev.warmode.game.WarTeam;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Team headquarters. Unbreakable: it only loses health from explosions while the HQ mode is running. */
public class HeadquartersBlock extends Block {
   public static final MapCodec<HeadquartersBlock> CODEC = simpleCodec(HeadquartersBlock::new);
   public static final EnumProperty<Side> TEAM = EnumProperty.create("team", Side.class);

   public enum Side implements StringRepresentable {
      BLUE("blue"),
      RED("red");

      private final String name;

      Side(String name) {
         this.name = name;
      }

      @Override
      public String getSerializedName() {
         return this.name;
      }

      public WarTeam team() {
         return this == BLUE ? WarTeam.BLUE : WarTeam.RED;
      }

      public static Side of(WarTeam team) {
         return team == WarTeam.BLUE ? BLUE : RED;
      }
   }

   public HeadquartersBlock(Properties properties) {
      super(properties);
      this.registerDefaultState(this.stateDefinition.any().setValue(TEAM, Side.BLUE));
   }

   @Override
   protected MapCodec<? extends Block> codec() {
      return CODEC;
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(TEAM);
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
         WarData data = WarData.get(serverPlayer.getServer());
         WarTeam team = state.getValue(TEAM).team();
         double max = Math.max(1.0, data.setting("hq_health"));
         serverPlayer.displayClientMessage(
            Component.translatable("msg.warmode.hq_status", team.title(), Math.round(data.hqHealth[team.ordinal()] / max * 100.0)), true
         );
      }

      return InteractionResult.sidedSuccess(level.isClientSide);
   }
}
