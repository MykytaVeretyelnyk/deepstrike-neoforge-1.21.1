package dev.sbw.deepstrike.registry;

import dev.sbw.deepstrike.game.AirRaidSirenBlock;
import dev.sbw.deepstrike.game.AirRaidSirenBlockEntity;
import dev.sbw.deepstrike.game.AirRaidSirenTowerPartBlock;
import dev.sbw.deepstrike.game.CommandRelayBlock;
import dev.sbw.deepstrike.game.LauncherBlock;
import dev.sbw.deepstrike.game.LauncherBlockEntity;
import dev.sbw.deepstrike.game.LauncherPartBlock;
import dev.sbw.deepstrike.game.LauncherPartBlockEntity;
import dev.sbw.deepstrike.game.MissileAssemblyBlock;
import dev.sbw.deepstrike.game.MissileAssemblyBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityType.Builder;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class BFBlocks {
   public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK, "deepstrike");
   public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, "deepstrike");
   public static final DeferredHolder<Block, LauncherBlock> FP5_LAUNCHER = BLOCKS.register(
      "fp5_launcher",
      () -> new LauncherBlock(LauncherBlock.Family.FP5, true, Properties.of().mapColor(MapColor.COLOR_GRAY).strength(5.0F).noOcclusion().dynamicShape())
   );
   public static final DeferredHolder<Block, LauncherBlock> FP5_LAUNCHER_GRAPHITE = BLOCKS.register(
      "fp5_launcher_graphite",
      () -> new LauncherBlock(LauncherBlock.Family.FP5, true, Properties.of().mapColor(MapColor.COLOR_GRAY).strength(5.0F).noOcclusion().dynamicShape())
   );
   public static final DeferredHolder<Block, LauncherBlock> JAGM_LAUNCHER = BLOCKS.register(
      "jagm_launcher",
      () -> new LauncherBlock(LauncherBlock.Family.JAGM, Properties.of().mapColor(MapColor.COLOR_GRAY).strength(6.0F).noOcclusion().dynamicShape())
   );
   public static final DeferredHolder<Block, LauncherBlock> PEACEKEEPER_PLATFORM = BLOCKS.register(
      "peacekeeper_platform",
      () -> new LauncherBlock(LauncherBlock.Family.PEACEKEEPER, Properties.of().mapColor(MapColor.COLOR_GRAY).strength(8.0F).noOcclusion().dynamicShape())
   );
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LauncherBlockEntity>> LAUNCHER_BE = BLOCK_ENTITY_TYPES.register(
      "launcher",
      () -> Builder.of(
            LauncherBlockEntity::new,
            new Block[]{(Block)FP5_LAUNCHER.get(), (Block)FP5_LAUNCHER_GRAPHITE.get(), (Block)JAGM_LAUNCHER.get(), (Block)PEACEKEEPER_PLATFORM.get()}
         )
         .build(null)
   );
   public static final DeferredHolder<Block, Block> LAUNCHER_PART = BLOCKS.register(
      "launcher_part", () -> new LauncherPartBlock(Properties.of().strength(5.0F).noOcclusion().dynamicShape().noLootTable().dynamicShape())
   );
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LauncherPartBlockEntity>> LAUNCHER_PART_BE = BLOCK_ENTITY_TYPES.register(
      "launcher_part", () -> Builder.of(LauncherPartBlockEntity::new, new Block[]{(Block)LAUNCHER_PART.get()}).build(null)
   );
   public static final DeferredHolder<Block, AirRaidSirenBlock> SIREN_TOWER = BLOCKS.register(
      "siren_tower", () -> new AirRaidSirenBlock(AirRaidSirenBlock.Tier.TOWER, Properties.of().mapColor(MapColor.METAL).strength(6.0F).noOcclusion())
   );
   public static final DeferredHolder<Block, Block> SIREN_TOWER_PART = BLOCKS.register(
      "siren_tower_part", () -> new AirRaidSirenTowerPartBlock(Properties.of().mapColor(MapColor.METAL).strength(6.0F).noOcclusion().noLootTable())
   );
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirRaidSirenBlockEntity>> SIREN_BE = BLOCK_ENTITY_TYPES.register(
      "air_raid_siren", () -> Builder.of(AirRaidSirenBlockEntity::new, new Block[]{(Block)SIREN_TOWER.get()}).build(null)
   );
   public static final DeferredHolder<Block, MissileAssemblyBlock> MISSILE_ASSEMBLY_STATION = BLOCKS.register(
      "missile_assembly_station", () -> new MissileAssemblyBlock(Properties.of().mapColor(MapColor.METAL).strength(4.5F).noOcclusion())
   );
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MissileAssemblyBlockEntity>> MISSILE_ASSEMBLY_BE = BLOCK_ENTITY_TYPES.register(
      "missile_assembly_station", () -> Builder.of(MissileAssemblyBlockEntity::new, new Block[]{(Block)MISSILE_ASSEMBLY_STATION.get()}).build(null)
   );
   public static final DeferredHolder<Block, Block> COMMAND_RELAY = BLOCKS.register(
      "command_relay", () -> new CommandRelayBlock(Properties.of().mapColor(MapColor.METAL).strength(3.0F))
   );
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CommandRelayBlock.Relay>> COMMAND_RELAY_BE = BLOCK_ENTITY_TYPES.register(
      "command_relay", () -> Builder.of(CommandRelayBlock.Relay::new, new Block[]{(Block)COMMAND_RELAY.get()}).build(null)
   );

   private BFBlocks() {
   }
}
