package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.registry.BFParticles;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import dev.sbw.deepstrike.compat.TickEvent.LevelTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(
   modid = "deepstrike"
)
public final class BurningDebris {
   private static final Map<ServerLevel, List<BurningDebris.Site>> SITES = new WeakHashMap<>();

   public static List<BlockPos> fuelNear(ServerLevel level, Vec3 center) {
      ArrayList<BlockPos> result = new ArrayList<>();
      BlockPos origin = BlockPos.containing(center);

      for (int x = -6; x <= 6; x++) {
         for (int y = -4; y <= 4; y++) {
            for (int z = -6; z <= 6; z++) {
               BlockPos p = origin.offset(x, y, z);
               if (level.hasChunkAt(p)) {
                  BlockState s = level.getBlockState(p);
                  if (s.isFlammable(level, p, Direction.UP) || s.getFluidState().getType() == Fluids.LAVA) {
                     result.add(p);
                  }
               }
            }
         }
      }

      return result;
   }

   public static void ignite(ServerLevel level, Vec3 center, List<BlockPos> fuel) {
      if (!(level.random.nextFloat() >= 0.2F)) {
         List<BurningDebris.Site> sites = SITES.computeIfAbsent(level, k -> new ArrayList<>());
         int count = fuel.isEmpty() ? 2 : 6;

         for (int i = 0; i < count; i++) {
            BlockPos seed = fuel.isEmpty()
               ? BlockPos.containing(center).offset(level.random.nextInt(9) - 4, 0, level.random.nextInt(9) - 4)
               : fuel.get(level.random.nextInt(fuel.size()));
            if (level.hasChunkAt(seed)) {
               BlockPos p = level.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, seed);
               if (!(Math.abs(p.getY() - center.y) > 64.0) && level.getBlockState(p).isAir() && Blocks.FIRE.defaultBlockState().canSurvive(level, p)) {
                  level.setBlockAndUpdate(p, Blocks.FIRE.defaultBlockState());
                  sites.add(new BurningDebris.Site(p.immutable(), level.getGameTime() + 2400L));
               }
            }
         }

         while (sites.size() > 128) {
            sites.remove(0);
         }
      }
   }

   @SubscribeEvent
   public static void tick(LevelTickEvent event) {
      if (event.phase == Phase.END && event.level instanceof ServerLevel level) {
         List<BurningDebris.Site> sites = SITES.get(level);
         if (sites != null) {
            long now = level.getGameTime();
            sites.removeIf(
               sx -> now >= sx.expires() || level.hasChunkAt(sx.position()) && (level.isRainingAt(sx.position()) || !level.getFluidState(sx.position()).isEmpty())
            );
            if (now % 8L == 0L) {
               ListIterator<BurningDebris.Site> iterator = sites.listIterator();

               while (iterator.hasNext()) {
                  BurningDebris.Site s = iterator.next();
                  if (level.hasChunkAt(s.position())) {
                     BlockState block = level.getBlockState(s.position());
                     if (block.isAir() && !Blocks.FIRE.defaultBlockState().canSurvive(level, s.position())) {
                        BlockPos ground = level.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, s.position());
                        if (ground.getY() <= s.position().getY()
                           && s.position().getY() - ground.getY() <= 8
                           && level.getBlockState(ground).isAir()
                           && level.getFluidState(ground).isEmpty()
                           && Blocks.FIRE.defaultBlockState().canSurvive(level, ground)) {
                           s = new BurningDebris.Site(ground.immutable(), s.expires());
                           iterator.set(s);
                           block = level.getBlockState(ground);
                        }
                     }

                     if (block.isAir() && !level.isRainingAt(s.position()) && Blocks.FIRE.defaultBlockState().canSurvive(level, s.position())) {
                        level.setBlockAndUpdate(s.position(), Blocks.FIRE.defaultBlockState());
                        block = level.getBlockState(s.position());
                     }

                     if (block.is(Blocks.FIRE) || block.is(Blocks.SOUL_FIRE)) {
                        for (ServerPlayer player : level.players()) {
                           level.sendParticles(
                              player,
                              (SimpleParticleType)BFParticles.SOOT_SMOKE.get(),
                              true,
                              s.position().getX() + 0.5,
                              s.position().getY() + 0.3,
                              s.position().getZ() + 0.5,
                              1,
                              0.1,
                              0.1,
                              0.1,
                              0.02
                           );
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void extinguish(LeftClickBlock event) {
      if (event.getLevel() instanceof ServerLevel level) {
         List<BurningDebris.Site> sites = SITES.get(level);
         if (sites != null) {
            Direction face = event.getFace();
            sites.removeIf(s -> s.position().equals(event.getPos()) || face != null && s.position().equals(event.getPos().relative(face)));
         }
      }
   }

   private BurningDebris() {
   }

   private record Site(BlockPos position, long expires) {
   }
}
