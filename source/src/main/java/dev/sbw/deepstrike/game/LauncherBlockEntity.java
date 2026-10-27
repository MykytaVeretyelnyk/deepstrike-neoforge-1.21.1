package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.compat.NbtCompat;
import dev.sbw.deepstrike.DeepStrikeAddon;
import dev.sbw.deepstrike.model.LauncherLayout;
import dev.sbw.deepstrike.model.MissileGeometry;
import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import dev.sbw.deepstrike.registry.BFBlocks;
import dev.sbw.deepstrike.registry.BFEntities;
import dev.sbw.deepstrike.registry.BFSounds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import dev.sbw.deepstrike.compat.ForgeChunkManager;
import dev.sbw.deepstrike.compat.net.PacketDistributor;
import org.joml.Matrix4f;

public final class LauncherBlockEntity extends BlockEntity implements Container {
   private final NonNullList<ItemStack> ammunition = NonNullList.withSize(6, ItemStack.EMPTY);
   private final long[] loadedAt = new long[6];
   private float yaw;
   private float elevation;
   private float targetYaw;
   private float targetElevation;
   private float visualYaw;
   private float visualElevation;
   private boolean visualInitialized;
   private long launchStarted = -1L;
   private long launchAtTick = -1L;
   private long lastServerTick = Long.MIN_VALUE;
   private PeacekeeperDynamics.Plan queuedPeacekeeperPlan;
   private String routeFallbackReason = "";
   private boolean countdownStarted;
   private boolean countdownSent;
   private int firingSlot = -1;
   private boolean coverEjected;
   private boolean queuedBallistic;
   private List<RoutePoint> queuedRoute = List.of();
   private UUID queuedOwner;
   private UUID queuedMissionId;
   private String queuedFrequency = "";
   private String transmitFrequency = "";
   private FlightRoutePlanner routePlanner;
   private List<RoutePoint> plannedRoute;
   private boolean planningFinished;
   private FlightTerrain flightTerrain;
   private UUID journalId = UUID.randomUUID();
   private UUID ownerId;
   private final RedstonePulseGate commandSignal = new RedstonePulseGate();
   private UUID commandNetwork;
   private boolean commandHold;
   private boolean commandLaunch;
   private boolean publicAccess = true;
   private int platformFuelMask;
   private int fallbackFuelBuckets;
   public boolean collisionDirty = true;
   private long nextCollisionRefresh;
   public Set<BlockPos> collisionParts = new HashSet<>();
   private long[] collisionBits = new long[0];
   private VoxelShape collisionShape = Shapes.empty();
   private Component lastFireError;

   public String transmitFrequency() {
      return this.transmitFrequency;
   }

   public UUID journalId() {
      return this.journalId;
   }

   public CompoundTag itemState(boolean transfer) {
      CompoundTag tag = NbtCompat.saveBE(this);

      for (String key : List.of(
         "CollisionVoxels",
         "CollisionParts",
         "QueuedOwner",
         "QueuedMissionId",
         "QueuedRoute",
         "PlannedRoute",
         "PlanningFinished",
         "QueuedFrequency",
         "LaunchStarted",
         "LaunchAtTick",
         "CoverEjected",
         "CountdownStarted",
         "CommandNetwork",
         "CommandHold",
         "CommandLaunch"
      )) {
         tag.remove(key);
      }

      tag.putInt("FiringSlot", -1);
      if (transfer && this.level instanceof ServerLevel server) {
         tag.putUUID("UnitTransfer", LaunchJournal.get(server).packBlock(this.journalId));
      }

      return tag;
   }

   public void placed(LivingEntity placer, ItemStack item) {
      if (this.level instanceof ServerLevel server) {
         CompoundTag tag = NbtCompat.getTagElement(item, "BlockEntityTag");
         UUID token = tag != null && tag.hasUUID("UnitTransfer") ? tag.getUUID("UnitTransfer") : null;
         this.journalId = LaunchJournal.get(server).placeBlock(server, this.journalId, this.worldPosition, token);
         if (this.ownerId == null && placer != null) {
            this.ownerId = placer.getUUID();
         }

         this.changed();
      }
   }

   public void destroyed() {
      this.resetLaunch();
   }

   public boolean canOperate(ServerPlayer player) {
      return this.publicAccess || this.ownerId == null || this.ownerId.equals(player.getUUID()) || player.hasPermissions(2);
   }

   public boolean platformFueled() {
      return PeacekeeperFuel.tfmgPresent() ? this.platformFuelMask == 7 : this.fallbackFuelBuckets == 3;
   }

   public Component platformFuelStatusComponent() {
      return PeacekeeperFuel.tfmgPresent()
         ? Component.translatable(
            "message.deepstrike.platform.fuel_types",
            new Object[]{
               (this.platformFuelMask & 1) != 0 ? "✓" : "—", (this.platformFuelMask & 2) != 0 ? "✓" : "—", (this.platformFuelMask & 4) != 0 ? "✓" : "—"
            }
         )
         : Component.translatable("message.deepstrike.platform.fuel_buckets", new Object[]{this.fallbackFuelBuckets});
   }

   public String platformFuelStatus() {
      return this.platformFuelStatusComponent().getString();
   }

   public boolean refuel(Player player, InteractionHand hand) {
      int slot = PeacekeeperFuel.fuelSlot(player.getItemInHand(hand));
      if (slot < 0 || this.family() != LauncherBlock.Family.PEACEKEEPER) {
         return false;
      } else if (this.level.isClientSide) {
         return true;
      } else if (this.firingSlot < 0 && (slot == 3 ? this.fallbackFuelBuckets < 3 : (this.platformFuelMask & 1 << slot) == 0)) {
         if (slot == 3) {
            this.fallbackFuelBuckets++;
         } else {
            this.platformFuelMask |= 1 << slot;
         }

         if (!player.getAbilities().instabuild) {
            ItemStack held = player.getItemInHand(hand);
            held.shrink(1);
            ItemStack empty = new ItemStack(Items.BUCKET);
            if (held.isEmpty()) {
               player.setItemInHand(hand, empty);
            } else if (!player.getInventory().add(empty)) {
               player.drop(empty, false);
            }
         }

         this.changed();
         player.displayClientMessage(this.platformFuelStatusComponent(), true);
         return true;
      } else {
         player.displayClientMessage(Component.translatable("message.deepstrike.platform.fuel_busy"), true);
         return true;
      }
   }

   public VoxelShape collisionShape() {
      return this.collisionShape;
   }

   public void setCollisionBits(long[] bits) {
      if (!Arrays.equals(bits, this.collisionBits)) {
         this.collisionBits = bits;
         this.collisionShape = LauncherCollision.shape(bits);
         this.setChanged();
         if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
         }
      }
   }

   public LauncherBlockEntity(BlockPos pos, BlockState state) {
      super((BlockEntityType)BFBlocks.LAUNCHER_BE.get(), pos, state);
   }

   public LauncherBlock.Family family() {
      return ((LauncherBlock)this.getBlockState().getBlock()).family();
   }

   public int getContainerSize() {
      return this.capacity();
   }

   public boolean isEmpty() {
      return this.loadCount() == 0;
   }

   public ItemStack getItem(int slot) {
      return this.slot(slot);
   }

   public boolean slotLocked(int slot) {
      return this.commandNetwork != null || slot == this.firingSlot;
   }

   public ItemStack removeItem(int slot, int count) {
      if (this.slotLocked(slot)) {
         return ItemStack.EMPTY;
      } else {
         ItemStack result = ContainerHelper.removeItem(this.ammunition, slot, count);
         this.changed();
         return result;
      }
   }

   public ItemStack removeItemNoUpdate(int slot) {
      return this.slotLocked(slot) ? ItemStack.EMPTY : ContainerHelper.takeItem(this.ammunition, slot);
   }

   public void setItem(int slot, ItemStack stack) {
      if (!this.slotLocked(slot)) {
         if (stack.isEmpty() || this.canPlaceItem(slot, stack)) {
            this.ammunition.set(slot, stack);
            if (!stack.isEmpty()) {
               stack.setCount(1);
               this.loadedAt[slot] = this.level == null ? 0L : this.level.getGameTime();
            }

            this.changed();
         }
      }
   }

   public boolean stillValid(Player player) {
      return this.level != null && this.level.getBlockEntity(this.worldPosition) == this && player.distanceToSqr(Vec3.atCenterOf(this.worldPosition)) < 100.0;
   }

   public void clearContent() {
      for (int i = 0; i < this.capacity(); i++) {
         if (!this.slotLocked(i)) {
            this.ammunition.set(i, ItemStack.EMPTY);
         }
      }

      this.changed();
   }

   public boolean canPlaceItem(int slot, ItemStack stack) {
      if (!this.slotLocked(slot) && stack.getItem() instanceof MissileItem missile) {
         switch (this.family()) {
            case JAGM:
               if (missile.kind() == MissileKind.JAGM) {
                  return true;
               }
               break;
            case FP5:
               if (missile.kind() == MissileKind.FP5) {
                  return true;
               }
               break;
            case PEACEKEEPER:
               if (missile.kind() == MissileKind.PEACEKEEPER) {
                  return true;
               }
               break;
            default:
               throw new IncompatibleClassChangeError();
         }
      }

      return false;
   }

   public int getMaxStackSize() {
      return 1;
   }

   public int capacity() {
      return this.family() == LauncherBlock.Family.JAGM ? 6 : 1;
   }

   public ItemStack slot(int i) {
      return (ItemStack)this.ammunition.get(i);
   }

   public ItemStack loaded() {
      for (int i = 0; i < this.capacity(); i++) {
         if (!this.slot(i).isEmpty()) {
            return this.slot(i);
         }
      }

      return ItemStack.EMPTY;
   }

   public int loadCount() {
      int n = 0;

      for (int i = 0; i < this.capacity(); i++) {
         if (!this.slot(i).isEmpty()) {
            n++;
         }
      }

      return n;
   }

   public float yaw() {
      return this.yaw;
   }

   public float elevation() {
      return this.elevation;
   }

   public float renderYaw() {
      return this.level != null && this.level.isClientSide ? this.visualYaw : this.yaw;
   }

   public float renderElevation() {
      return this.level != null && this.level.isClientSide ? this.visualElevation : this.elevation;
   }

   public float facingYaw() {
      return ((Direction)this.getBlockState().getValue(LauncherBlock.FACING)).toYRot();
   }

   public float loadProgress(int slot, float partial) {
      return this.level == null ? 1.0F : Mth.clamp(((float)(this.level.getGameTime() - this.loadedAt[slot]) + partial) / 16.0F, 0.0F, 1.0F);
   }

   public boolean coverVisible(int slot) {
      return !this.slot(slot).isEmpty() && (slot != this.firingSlot || !this.coverEjected);
   }

   public static void clientTick(Level level, BlockPos pos, BlockState state, LauncherBlockEntity be) {
      if (!be.visualInitialized) {
         be.visualYaw = be.yaw;
         be.visualElevation = be.elevation;
         be.visualInitialized = true;
      }

      be.visualYaw = be.visualYaw + Mth.wrapDegrees(be.yaw - be.visualYaw) * 0.65F;
      be.visualElevation = Mth.lerp(0.65F, be.visualElevation, be.elevation);
   }

   public void adjust(float y, float p) {
      if (this.firingSlot < 0 && this.commandNetwork == null) {
         this.targetYaw = Mth.wrapDegrees(this.targetYaw + y);
         this.targetElevation = Mth.clamp(this.targetElevation + p, 0.0F, 80.0F);
         this.changed();
      }
   }

   public boolean load(ItemStack stack, Player player) {
      for (int i = 0; i < this.capacity(); i++) {
         if (!this.slotLocked(i) && this.slot(i).isEmpty() && this.canPlaceItem(i, stack)) {
            this.ammunition.set(i, stack.copyWithCount(1));
            this.loadedAt[i] = this.level.getGameTime();
            if (!player.getAbilities().instabuild) {
               stack.shrink(1);
            }

            this.changed();
            return true;
         }
      }

      return false;
   }

   public void unload(Player player) {
      for (int i = this.capacity() - 1; i >= 0; i--) {
         if (!this.slotLocked(i) && !this.slot(i).isEmpty()) {
            ItemStack ammo = this.slot(i);
            this.ammunition.set(i, ItemStack.EMPTY);
            if (!player.getInventory().add(ammo)) {
               Containers.dropItemStack(this.level, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1, this.worldPosition.getZ() + 0.5, ammo);
            }

            this.changed();
            return;
         }
      }
   }

   public void dropContents() {
      for (int i = 0; i < this.capacity(); i++) {
         if (!this.slot(i).isEmpty()) {
            Containers.dropItemStack(this.level, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1, this.worldPosition.getZ() + 0.5, this.slot(i));
            this.ammunition.set(i, ItemStack.EMPTY);
         }
      }
   }

   private static Component launchError(String key, Object... args) {
      return Component.translatable("error.deepstrike.launcher." + key, args);
   }

   private static Component ballisticReason(String reason) {
      return (Component)(switch (reason) {
         case "INVALID_RANGE" -> launchError("ballistic_range");
         case "TARGET_ABOVE_REACHABLE_ARC" -> launchError("ballistic_height");
         case "NO_REACHABLE_BALLISTIC_PROFILE" -> launchError("ballistic_profile");
         default -> Component.literal(reason);
      });
   }

   public Component getLastFireErrorComponent() {
      return this.lastFireError == null ? launchError("unknown") : this.lastFireError;
   }

   public String getLastFireError() {
      return this.getLastFireErrorComponent().getString();
   }

   public boolean fire(ServerPlayer player, List<RoutePoint> route, boolean ballistic) {
      return this.fire(player, route, ballistic, this.transmitFrequency);
   }

   public boolean fire(ServerPlayer player, List<RoutePoint> route, boolean ballistic, String frequency) {
      return this.fire(player, route, ballistic, frequency, UUID.randomUUID());
   }

   public boolean fire(ServerPlayer player, List<RoutePoint> route, boolean ballistic, String frequency, UUID commandId) {
      if (this.commandNetwork != null) {
         this.lastFireError = launchError("busy");
         return false;
      } else {
         return this.fireInternal(player, route, ballistic, frequency, commandId, false);
      }
   }

   private boolean fireInternal(ServerPlayer player, List<RoutePoint> route, boolean ballistic, String frequency, UUID commandId, boolean trustedCommand) {
      if (this.loaded().getItem() instanceof MissileItem m && m.kind() == MissileKind.STORM_SHADOW) {
         this.lastFireError = Component.translatable("su24.only_aircraft");
         return false;
      } else if (this.level instanceof ServerLevel server && ActiveFlights.get(server).knowsMission(commandId)) {
         if (!ActiveFlights.get(server).acceptedBy(commandId, this.journalId, player.getUUID())) {
            this.lastFireError = launchError("used");
            return false;
         } else {
            if (commandId.equals(this.queuedMissionId)) {
               this.sendCountdown(server, player);
            }

            return true;
         }
      } else if (!trustedCommand && !this.canOperate(player)) {
         this.lastFireError = launchError("access");
         return false;
      } else if (!RadioFrequency.valid(frequency)) {
         this.lastFireError = launchError("frequency");
         return false;
      } else {
         this.lastFireError = null;
         if (!(this.level instanceof ServerLevel)) {
            this.lastFireError = launchError("server");
            return false;
         } else if (player.level() != this.level) {
            this.lastFireError = launchError("dimension");
            return false;
         } else if (!trustedCommand && player.distanceToSqr(Vec3.atCenterOf(this.worldPosition)) > 1.6777216E7) {
            this.lastFireError = launchError("distance");
            return false;
         } else if (this.firingSlot >= 0) {
            this.lastFireError = launchError("busy");
            return false;
         } else if (route.isEmpty()) {
            this.lastFireError = launchError("empty_route");
            return false;
         } else if (route.size() > 32) {
            this.lastFireError = launchError("route_count");
            return false;
         } else if (this.family() != LauncherBlock.Family.PEACEKEEPER || route.size() == 1 && this.platformFueled()) {
            this.firingSlot = -1;

            for (int i = 0; i < this.capacity(); i++) {
               ItemStack s = this.slot(i);
               if (!s.isEmpty() && s.getItem() instanceof MissileItem) {
                  this.firingSlot = i;
                  break;
               }
            }

            if (this.firingSlot < 0) {
               this.lastFireError = launchError("empty_launcher");
               return false;
            } else {
               ItemStack ammo = this.slot(this.firingSlot);
               MissileData data = MissileData.fromItemStack(ammo);
               if (data == null || data.supportsWarhead() && (this.family() == LauncherBlock.Family.PEACEKEEPER || data.fuel() > 0)) {
                  MissileKind kind = ammo.getItem() instanceof MissileItem m
                     ? m.kind()
                     : (this.family() == LauncherBlock.Family.JAGM ? MissileKind.JAGM : MissileKind.FP5);
                  int maxR = data != null ? data.calculateMaxRange() : kind.maxRange;
                  int minR = kind.minRange;
                  RoutePoint finalTarget = route.get(route.size() - 1);
                  double dx = finalTarget.x() - this.worldPosition.getX() - 0.5;
                  double dz = finalTarget.z() - this.worldPosition.getZ() - 0.5;
                  double dist = Math.sqrt(dx * dx + dz * dz);
                  if (dist < minR - 5.0) {
                     this.firingSlot = -1;
                     this.lastFireError = launchError("min_range", minR, (int)Math.round(dist));
                     return false;
                  } else if (dist > maxR + 25.0) {
                     this.firingSlot = -1;
                     this.lastFireError = launchError("max_range", maxR, (int)Math.round(dist));
                     return false;
                  } else {
                     List<RoutePoint> sanitized = new ArrayList<>();
                     int maxAllowedSpeed = data != null ? data.calculateMaxSpeed() : kind.maxSpeed;

                     for (RoutePoint p : route) {
                        if (Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z())) {
                           double waypointDistance = Math.hypot(p.x() - this.worldPosition.getX() - 0.5, p.z() - this.worldPosition.getZ() - 0.5);
                           if (!(waypointDistance > maxR + 25.0) && this.level.getWorldBorder().isWithinBounds(BlockPos.containing(p.x(), p.y(), p.z()))) {
                              double clampedY = Mth.clamp(p.y(), this.level.getMinBuildHeight() + 2, this.level.getMaxBuildHeight() - 2);
                              double rawSpd = Double.isFinite(p.speed()) ? p.speed() : kind.defaultSpeed;
                              double clampedSpd = Mth.clamp(rawSpd, kind.minSpeed, maxAllowedSpeed);
                              sanitized.add(new RoutePoint(p.x(), clampedY, p.z(), clampedSpd));
                              continue;
                           }

                           this.firingSlot = -1;
                           this.lastFireError = launchError("waypoint_range");
                           return false;
                        }

                        this.firingSlot = -1;
                        this.lastFireError = launchError("coordinates");
                        return false;
                     }

                     if (sanitized.isEmpty()) {
                        this.firingSlot = -1;
                        this.lastFireError = launchError("coordinates");
                        return false;
                     } else {
                        this.queuedPeacekeeperPlan = null;
                        if (kind == MissileKind.PEACEKEEPER) {
                           Matrix4f launchMount = LauncherLayout.mount(this.family(), this.facingYaw(), this.yaw, this.elevation, this.firingSlot);
                           Vec3 origin = LauncherLayout.worldPosition(this.worldPosition, launchMount);
                           RoutePoint target = sanitized.get(0);
                           PeacekeeperDynamics.Selection selection = PeacekeeperDynamics.select(
                              origin, new Vec3(target.x(), target.y(), target.z()), target.speed()
                           );
                           if (!selection.valid()) {
                              this.firingSlot = -1;
                              this.lastFireError = launchError("ballistic", ballisticReason(selection.reason()));
                              return false;
                           }

                           this.queuedPeacekeeperPlan = selection.plan();
                        }

                        this.queuedOwner = player.getUUID();
                        this.queuedMissionId = commandId;
                        this.queuedFrequency = frequency;
                        this.transmitFrequency = frequency;
                        this.queuedRoute = List.copyOf(sanitized);
                        this.queuedBallistic = this.family() == LauncherBlock.Family.PEACEKEEPER || ballistic;
                        this.coverEjected = false;
                        if (this.family() == LauncherBlock.Family.JAGM) {
                           RoutePoint target = this.queuedRoute.get(0);
                           Vec3 delta = new Vec3(
                              target.x() - this.worldPosition.getX() - 0.5,
                              target.y() - this.worldPosition.getY() - 0.5,
                              target.z() - this.worldPosition.getZ() - 0.5
                           );
                           float targetWorldYaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
                           this.targetYaw = Mth.wrapDegrees(targetWorldYaw - this.facingYaw());
                           this.targetElevation = Mth.clamp(
                              (float)Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))),
                              0.0F,
                              80.0F
                           );
                        }

                        this.countdownStarted = true;
                        this.countdownSent = false;
                        this.launchStarted = this.level.getGameTime();
                        this.launchAtTick = this.launchStarted + 100L;
                        RoutePoint acceptedTarget = this.queuedRoute.get(this.queuedRoute.size() - 1);
                        if (!ActiveFlights.get((ServerLevel)this.level)
                           .acceptLaunch(
                              (ServerLevel)this.level,
                              this.queuedMissionId,
                              this.journalId,
                              this.queuedOwner,
                              this.worldPosition,
                              new Vec3(acceptedTarget.x(), acceptedTarget.y(), acceptedTarget.z()),
                              kind.id,
                              this.launchAtTick
                           )) {
                           this.firingSlot = -1;
                           this.lastFireError = launchError("processed");
                           return false;
                        } else {
                           this.routePlanner = null;
                           this.routeFallbackReason = "";
                           this.plannedRoute = null;
                           this.planningFinished = false;
                           Matrix4f mount = LauncherLayout.mount(
                              this.family(),
                              this.facingYaw(),
                              this.family() == LauncherBlock.Family.JAGM ? this.targetYaw : this.yaw,
                              this.family() == LauncherBlock.Family.JAGM ? this.targetElevation : this.elevation,
                              this.firingSlot
                           );
                           this.flightTerrain = new FlightTerrain(
                              (ServerLevel)this.level,
                              LauncherLayout.worldPosition(this.worldPosition, mount),
                              this.queuedRoute,
                              kind,
                              this.queuedPeacekeeperPlan
                           );
                           this.holdLaunchChunk((ServerLevel)this.level, true);
                           if (!trustedCommand) {
                              this.sendCountdown((ServerLevel)this.level, player);
                           }

                           this.changed();
                           return true;
                        }
                     }
                  }
               } else {
                  this.firingSlot = -1;
                  this.lastFireError = launchError("missile_fuel");
                  return false;
               }
            }
         } else {
            this.lastFireError = route.size() != 1 ? launchError("peacekeeper_target") : launchError("platform_refuel", this.platformFuelStatusComponent());
            return false;
         }
      }
   }

   public boolean prepareCommand(UUID network, ServerPlayer actor, List<RoutePoint> route, boolean ballistic, String frequency, UUID mission) {
      if (this.commandNetwork != null && !this.commandNetwork.equals(network)) {
         return false;
      } else if (this.firingSlot >= 0) {
         return false;
      } else if (!this.fireInternal(actor, route, ballistic, frequency, mission, true)) {
         return false;
      } else {
         this.commandNetwork = network;
         this.commandHold = true;
         this.commandLaunch = true;
         this.changed();
         return true;
      }
   }

   public boolean commandReady(UUID network) {
      return network.equals(this.commandNetwork)
         && this.commandHold
         && this.firingSlot >= 0
         && this.planningFinished
         && (
            this.family() != LauncherBlock.Family.JAGM
               || Math.abs(Mth.wrapDegrees(this.targetYaw - this.yaw)) <= 2.0F && Math.abs(this.targetElevation - this.elevation) <= 1.0F
         );
   }

   public boolean commandExitClear() {
      return this.commandExitIssue().isEmpty();
   }

   public String commandExitIssue() {
      if (this.level instanceof ServerLevel server && this.firingSlot >= 0 && this.slot(this.firingSlot).getItem() instanceof MissileItem item) {
         Matrix4f var13 = LauncherLayout.mount(this.family(), this.facingYaw(), this.yaw, this.elevation, this.firingSlot);
         Vec3 origin = LauncherLayout.worldPosition(this.worldPosition, var13);
         Vec3 direction = LauncherLayout.forward(this.family(), var13);
         GuidedMissileEntity probe = new GuidedMissileEntity(server, item.kind());
         probe.setPos(origin);
         probe.setLaunchMaster(this.worldPosition);
         AABB bounds = MissileGeometry.worldBounds(item.kind(), origin, direction, 0.0F, 0.0F);
         FlightCollision.SweepResult hit = FlightCollision.sweepSegment(server, probe, origin, origin.add(direction.scale(6.0)), bounds, e -> false);
         boolean clear = hit.hit().getType() == Type.MISS && hit.terrainComplete();
         if (!clear && System.getProperty("forge.enabledGameTestNamespaces", "").contains("deepstrike_command")) {
            DeepStrikeAddon.LOGGER
               .warn(
                  "COMMAND_EXIT_DIAGNOSTIC pad={} origin={} direction={} bounds={} hit={} contact={} missing={}",
                  new Object[]{
                     this.worldPosition,
                     origin,
                     direction,
                     bounds,
                     hit.hit().getType(),
                     hit.hit() instanceof BlockHitResult block ? block.getBlockPos() : hit.hit().getLocation(),
                     hit.missingTerrain()
                  }
               );
         }

         return !hit.terrainComplete() ? "terrain" : (clear ? "" : "launch_exit");
      } else {
         return "not_ready";
      }
   }

   public boolean commandBusy() {
      return this.firingSlot >= 0;
   }

   public UUID commandMission() {
      return this.queuedMissionId;
   }

   public boolean releaseCommand(UUID network, long tick) {
      if (this.commandReady(network) && tick >= this.level.getGameTime() + 10L && this.commandExitClear()) {
         this.commandHold = false;
         this.launchAtTick = tick;
         this.launchStarted = tick - 100L;
         this.changed();
         return true;
      } else {
         return false;
      }
   }

   public void cancelCommand(UUID network) {
      if (network.equals(this.commandNetwork)) {
         this.resetLaunch();
         this.commandNetwork = null;
         this.commandHold = false;
         this.commandLaunch = false;
         this.changed();
      }
   }

   public boolean canBindCommand(ServerPlayer player) {
      return this.ownerId == null || this.ownerId.equals(player.getUUID()) || player.hasPermissions(2);
   }

   private void holdLaunchChunk(ServerLevel server, boolean active) {
      ChunkPos chunk = new ChunkPos(this.worldPosition);
      ForgeChunkManager.forceChunk(server, "deepstrike", this.worldPosition, chunk.x, chunk.z, active, true);
   }

   private void sendCountdown(ServerLevel server, ServerPlayer player) {
      if (player != null && player.serverLevel() == server) {
         DeepStrikeNetwork.sendLaunchCountdown(
            player,
            this.worldPosition,
            this.queuedMissionId,
            this.launchAtTick,
            true,
            this.planningFinished ? (this.plannedRoute != null ? "READY" : "FALLBACK") : "PREPARING"
         );
      }
   }

   public void onLoad() {
      super.onLoad();
      if (this.level instanceof ServerLevel server) {
         LaunchJournal.get(server).rememberBlock(server, this.journalId, this.worldPosition);
      }

      if (this.level instanceof ServerLevel server && this.commandNetwork != null) {
         if (this.firingSlot >= 0 && ActiveFlights.get(server).wasLaunched(this.queuedMissionId)) {
            this.ammunition.set(this.firingSlot, ItemStack.EMPTY);
            if (this.family() == LauncherBlock.Family.PEACEKEEPER) {
               this.platformFuelMask = 0;
               this.fallbackFuelBuckets = 0;
            }
         }

         this.resetLaunch();
         this.commandNetwork = null;
         this.commandHold = false;
         this.commandLaunch = false;
         this.changed();
      } else {
         if (this.level instanceof ServerLevel server && this.firingSlot >= 0) {
            if (this.queuedRoute.isEmpty() || this.queuedOwner == null) {
               this.resetLaunch();
               return;
            }

            if (ActiveFlights.get(server).wasLaunched(this.queuedMissionId)) {
               this.ammunition.set(this.firingSlot, ItemStack.EMPTY);
               if (this.family() == LauncherBlock.Family.PEACEKEEPER) {
                  this.platformFuelMask = 0;
                  this.fallbackFuelBuckets = 0;
               }

               this.resetLaunch();
               return;
            }

            if (this.launchAtTick < 0L) {
               this.launchStarted = server.getGameTime();
               this.launchAtTick = this.launchStarted + 100L;
            }

            if (this.queuedMissionId == null) {
               this.queuedMissionId = UUID.randomUUID();
            }

            if (this.queuedOwner != null && !this.queuedRoute.isEmpty() && !ActiveFlights.get(server).knowsMission(this.queuedMissionId)) {
               RoutePoint target = this.queuedRoute.get(this.queuedRoute.size() - 1);
               MissileKind kind = this.slot(this.firingSlot).getItem() instanceof MissileItem item ? item.kind() : MissileKind.FP5;
               ActiveFlights.get(server)
                  .acceptLaunch(
                     server,
                     this.queuedMissionId,
                     this.journalId,
                     this.queuedOwner,
                     this.worldPosition,
                     new Vec3(target.x(), target.y(), target.z()),
                     kind.id,
                     this.launchAtTick
                  );
            }

            this.holdLaunchChunk(server, true);
         }
      }
   }

   public static void serverTick(Level level, BlockPos pos, BlockState state, LauncherBlockEntity be) {
      if (be.lastServerTick != level.getGameTime()) {
         be.lastServerTick = level.getGameTime();
         float beforeYaw = be.yaw;
         float beforePitch = be.elevation;
         boolean autoAimingJagm = be.firingSlot >= 0 && be.slot(be.firingSlot).getItem() instanceof MissileItem loaded && loaded.kind() == MissileKind.JAGM;
         if (be.firingSlot < 0 || autoAimingJagm || be.slot(be.firingSlot).getItem() instanceof MissileItem ammo && ammo.kind() == MissileKind.FP5) {
            be.yaw = be.yaw + Mth.clamp(Mth.wrapDegrees(be.targetYaw - be.yaw), -2.0F, 2.0F);
            be.elevation = Mth.approach(be.elevation, be.targetElevation, 1.0F);
         }

         if (beforeYaw != be.yaw || beforePitch != be.elevation) {
            be.changed();
         }

         boolean moving = beforeYaw != be.yaw || beforePitch != be.elevation;
         if (be.collisionDirty && (!moving || level.getGameTime() >= be.nextCollisionRefresh)) {
            LauncherCollision.refresh(be);
            be.collisionDirty = false;
            be.nextCollisionRefresh = level.getGameTime() + 4L;
         }

         if (be.commandSignal.sample(level.hasNeighborSignal(pos), level.getGameTime())) {
            CommandCenterData.get((ServerLevel)level).redstone((ServerLevel)level, be.journalId());
         }

         if (be.firingSlot >= 0) {
            ServerLevel server = (ServerLevel)level;
            if (!(be.slot(be.firingSlot).getItem() instanceof MissileItem item)) {
               be.resetLaunch();
            } else {
               if (be.launchAtTick < 0L) {
                  be.launchStarted = level.getGameTime();
                  be.launchAtTick = be.launchStarted + 100L;
                  be.changed();
               }

               if (!be.commandHold || !be.planningFinished) {
                  long elapsed = level.getGameTime() - be.launchStarted;
                  if (!be.commandHold && (!be.countdownSent || elapsed % 5L == 0L)) {
                     be.sendCountdown(server, be.queuedOwner == null ? null : server.getServer().getPlayerList().getPlayer(be.queuedOwner));
                     be.countdownSent = true;
                  }

                  if (be.flightTerrain == null) {
                     Matrix4f mount = LauncherLayout.mount(be.family(), be.facingYaw(), be.yaw, be.elevation, be.firingSlot);
                     be.flightTerrain = new FlightTerrain(
                        server, LauncherLayout.worldPosition(pos, mount), be.queuedRoute, item.kind(), be.queuedPeacekeeperPlan
                     );
                     if (be.plannedRoute != null) {
                        be.flightTerrain.retainValidatedCorridor(LauncherLayout.worldPosition(pos, mount), be.plannedRoute, item.kind());
                     }
                  }

                  be.flightTerrain.ready();
                  if (!be.planningFinished) {
                     if (item.kind() != MissileKind.PEACEKEEPER) {
                        if (be.routePlanner == null) {
                           Matrix4f mount = LauncherLayout.mount(
                              be.family(),
                              be.facingYaw(),
                              item.kind() == MissileKind.JAGM ? be.targetYaw : be.yaw,
                              item.kind() == MissileKind.JAGM ? be.targetElevation : be.elevation,
                              be.firingSlot
                           );
                           be.routePlanner = new FlightRoutePlanner(
                              server, pos, LauncherLayout.worldPosition(pos, mount), LauncherLayout.forward(be.family(), mount), item.kind(), be.queuedRoute
                           );
                        }

                        be.routePlanner.step();
                        if (be.routePlanner.done()) {
                           be.routeFallbackReason = be.routePlanner.failureReason().name();
                           be.plannedRoute = be.routePlanner.result();
                           if (be.plannedRoute != null) {
                              Matrix4f mount = LauncherLayout.mount(
                                 be.family(),
                                 be.facingYaw(),
                                 item.kind() == MissileKind.JAGM ? be.targetYaw : be.yaw,
                                 item.kind() == MissileKind.JAGM ? be.targetElevation : be.elevation,
                                 be.firingSlot
                              );
                              be.flightTerrain.retainValidatedCorridor(LauncherLayout.worldPosition(pos, mount), be.plannedRoute, item.kind());
                           }

                           be.routePlanner = null;
                           be.planningFinished = true;
                           be.changed();
                        }
                     } else {
                        if (be.routePlanner != null) {
                           be.routePlanner.cancel();
                           be.routeFallbackReason = be.routePlanner.failureReason().name();
                           be.routePlanner = null;
                        } else if (item.kind() != MissileKind.FP5 && item.kind() != MissileKind.PEACEKEEPER) {
                           be.routeFallbackReason = "TIME_LIMIT";
                        }

                        be.planningFinished = true;
                        be.changed();
                     }
                  }

                  if (item.kind() != MissileKind.PEACEKEEPER && be.plannedRoute == null && (be.planningFinished || level.getGameTime() >= be.launchAtTick)) {
                     if (be.routePlanner != null) {
                        be.routePlanner.cancel();
                        be.routePlanner = null;
                     }

                     Matrix4f mount = LauncherLayout.mount(be.family(), be.facingYaw(), be.yaw, be.elevation, be.firingSlot);
                     be.plannedRoute = SimpleFlightPlan.build(
                        LauncherLayout.worldPosition(pos, mount), LauncherLayout.forward(be.family(), mount), item.kind(), be.queuedRoute
                     );
                     be.planningFinished = true;
                  }

                  if (be.commandHold) {
                     if (be.planningFinished && be.flightTerrain != null) {
                        be.flightTerrain.close();
                        be.flightTerrain = null;
                     }
                  } else {
                     if (be.family() == LauncherBlock.Family.JAGM && level.getGameTime() >= be.launchAtTick - 10L && !be.coverEjected) {
                        Matrix4f mount = LauncherLayout.mount(be.family(), be.facingYaw(), be.yaw, be.elevation, be.firingSlot);
                        Matrix4f coverTransform = new Matrix4f(mount).translate(-1.196875F, 0.0F, 0.0F);
                        Vec3 p = LauncherLayout.worldPosition(pos, coverTransform);
                        Vec3 d = LauncherLayout.forward(be.family(), mount);
                        LaunchCoverEntity cover = (LaunchCoverEntity)((EntityType)BFEntities.LAUNCH_COVER.get()).create(server);
                        if (cover != null) {
                           cover.moveTo(
                              p.x,
                              p.y,
                              p.z,
                              (float)Math.toDegrees(Math.atan2(-d.x, d.z)),
                              (float)(-Math.toDegrees(Math.asin(d.y)))
                           );
                           cover.setDeltaMovement(d.scale(0.22).add(0.0, 0.07, 0.0));
                           server.addFreshEntity(cover);
                        }

                        be.coverEjected = true;
                        be.changed();
                     }

                     if (level.getGameTime() >= be.launchAtTick) {
                        be.launchNow(server);
                     }
                  }
               }
            }
         }
      }
   }

   private void launchNow(ServerLevel server) {
      if (ActiveFlights.get(server).wasLaunched(this.queuedMissionId)) {
         this.ammunition.set(this.firingSlot, ItemStack.EMPTY);
         if (this.family() == LauncherBlock.Family.PEACEKEEPER) {
            this.platformFuelMask = 0;
            this.fallbackFuelBuckets = 0;
         }

         this.resetLaunch();
      } else {
         ItemStack stack = this.slot(this.firingSlot);
         if (!(stack.getItem() instanceof MissileItem item)) {
            this.resetLaunch();
         } else if (item.kind() == MissileKind.STORM_SHADOW) {
            this.lastFireError = Component.translatable("su24.only_aircraft");
            this.resetLaunch();
         } else {
            MissileData data = MissileData.fromItemStack(stack);
            String camo = data != null ? data.camo() : MissileData.defaultCamo(item.kind());
            Matrix4f mount = LauncherLayout.mount(this.family(), this.facingYaw(), this.yaw, this.elevation, this.firingSlot);
            Vec3 p = LauncherLayout.worldPosition(this.worldPosition, mount);
            Vec3 d = LauncherLayout.forward(this.family(), mount);
            GuidedMissileEntity missile = new GuidedMissileEntity(server, item.kind(), item.graphite(), camo);
            missile.setUUID(this.queuedMissionId);
            missile.applyMissileData(data);
            missile.setFrequency(this.queuedFrequency);
            missile.moveTo(
               p.x, p.y, p.z, (float)Math.toDegrees(Math.atan2(-d.x, d.z)), (float)(-Math.toDegrees(Math.asin(d.y)))
            );
            missile.setLaunchVector(d, 12);
            missile.setLaunchMaster(this.worldPosition);
            missile.setOwnerId(this.queuedOwner);
            missile.setMission(this.queuedRoute, this.queuedBallistic);
            missile.setMissionId(this.queuedMissionId);
            if (this.commandNetwork != null) {
               missile.getPersistentData().putUUID("DeepStrikeCommandNetwork", this.commandNetwork);
            }

            if (item.kind() == MissileKind.PEACEKEEPER) {
               if (this.queuedPeacekeeperPlan == null) {
                  RoutePoint target = this.queuedRoute.get(0);
                  PeacekeeperDynamics.Selection selection = PeacekeeperDynamics.select(p, new Vec3(target.x(), target.y(), target.z()), target.speed());
                  if (!selection.valid()) {
                     this.lastFireError = launchError("ballistic", ballisticReason(selection.reason()));
                     ServerPlayer pilot = server.getServer().getPlayerList().getPlayer(this.queuedOwner);
                     if (pilot != null) {
                        pilot.displayClientMessage(this.getLastFireErrorComponent(), false);
                     }

                     this.resetLaunch();
                     return;
                  }

                  this.queuedPeacekeeperPlan = selection.plan();
               }

               missile.setPeacekeeperPlan(this.queuedPeacekeeperPlan);
               if (!missile.claimPeacekeeperFlight(server)) {
                  this.ammunition.set(this.firingSlot, ItemStack.EMPTY);
                  this.resetLaunch();
                  return;
               }
            }

            if (this.plannedRoute != null) {
               missile.setPlannedRoute(this.plannedRoute);
            } else if (item.kind() != MissileKind.FP5 && item.kind() != MissileKind.PEACEKEEPER) {
               missile.forceFp5Fallback();
            }

            if (server.addFreshEntity(missile)) {
               if (this.plannedRoute == null && item.kind() != MissileKind.FP5 && item.kind() != MissileKind.PEACEKEEPER) {
                  RoutePoint target = this.queuedRoute.get(this.queuedRoute.size() - 1);
                  LaunchJournal.recordOnce(
                     server,
                     this.journalId,
                     this.queuedMissionId,
                     "route_fallback",
                     Vec3.atCenterOf(this.worldPosition),
                     item.kind().id,
                     new Vec3(target.x(), target.y(), target.z()),
                     Component.translatable(
                        "journal.deepstrike.ROUTE_FALLBACK", new Object[]{this.routeFallbackReason.isEmpty() ? "TIME_LIMIT" : this.routeFallbackReason}
                     )
                  );
               }

               missile.setPreparedTerrain(this.flightTerrain);
               this.flightTerrain = null;
               MissileLaunchEffects.spawnLaunchPlume(server, p, d, item.kind() == MissileKind.PEACEKEEPER);
               this.playTerrainAdaptiveLaunchSound(server, p, item.kind());
               this.ammunition.set(this.firingSlot, ItemStack.EMPTY);
               if (this.family() == LauncherBlock.Family.PEACEKEEPER) {
                  this.platformFuelMask = 0;
                  this.fallbackFuelBuckets = 0;
               }

               ServerPlayer pilot = server.getServer().getPlayerList().getPlayer(this.queuedOwner);
               if (!this.commandLaunch && pilot != null && (item.kind() == MissileKind.JAGM || data != null && data.hasCamera())) {
                  DeepStrikeNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> pilot), new DeepStrikeNetwork.CameraTarget(missile.getId()));
               }

               RoutePoint last = this.queuedRoute.get(this.queuedRoute.size() - 1);
               LaunchJournal.recordOnce(
                  server,
                  this.journalId,
                  this.queuedMissionId,
                  "launch",
                  Vec3.atCenterOf(this.worldPosition),
                  item.kind().id,
                  new Vec3(last.x(), last.y(), last.z()),
                  Component.translatable("journal.deepstrike.launch_frequency", new Object[]{this.queuedFrequency})
                     .append(
                        this.plannedRoute == null && item.kind() != MissileKind.FP5 && item.kind() != MissileKind.PEACEKEEPER
                           ? Component.translatable("journal.deepstrike.fallback_suffix")
                           : Component.empty()
                     )
               );
               MissionLogSavedData.record(
                  server, item.kind(), this.worldPosition, pilot == null ? "Pilot" : pilot.getGameProfile().getName(), this.queuedRoute.size(), this.queuedBallistic
               );
            } else if (item.kind() == MissileKind.PEACEKEEPER) {
               missile.releasePeacekeeperFlight(server);
            }

            this.resetLaunch();
         }
      }
   }

   private void playTerrainAdaptiveLaunchSound(ServerLevel server, Vec3 pos, MissileKind kind) {
      int elevatedCount = 0;
      int totalSamples = 12;
      int launcherY = this.worldPosition.getY();

      for (int i = 0; i < totalSamples; i++) {
         double angle = i * ((Math.PI * 2) / totalSamples);
         int sx = this.worldPosition.getX() + (int)Math.round(Math.cos(angle) * 20.0);
         int sz = this.worldPosition.getZ() + (int)Math.round(Math.sin(angle) * 20.0);
         LevelChunk chunk = server.getChunkSource().getChunkNow(sx >> 4, sz >> 4);
         if (chunk != null) {
            int height = chunk.getHeight(Types.MOTION_BLOCKING, sx & 15, sz & 15) + 1;
            if (height >= launcherY + 4) {
               elevatedCount++;
            }
         }
      }

      boolean indoorOrCanyon = false;

      for (int dy = 3; dy <= 16; dy++) {
         if (!server.getBlockState(this.worldPosition.above(dy)).isAir()) {
            indoorOrCanyon = true;
            break;
         }
      }

      float enclosure = (float)elevatedCount / totalSamples;
      if (indoorOrCanyon) {
         enclosure = Math.min(1.0F, enclosure + 0.4F);
      }

      float pitchVariation = (server.random.nextFloat() - 0.5F) * 0.08F;
      float basePitch = kind == MissileKind.FP5 ? 0.96F : 1.05F;
      float finalPitch = Math.max(0.75F, Math.min(1.25F, basePitch - enclosure * 0.12F + pitchVariation));
      float volume = 3.5F + enclosure * 0.5F;
      server.playSound(null, pos.x, pos.y, pos.z, BFSounds.launchForKind(kind), SoundSource.BLOCKS, volume, finalPitch);
      if (enclosure >= 0.22F) {
         float echoEnclosure = enclosure;
         int delayTicks = 3 + (int)(enclosure * 6.0F);
         server.getServer().tell(new TickTask(server.getServer().getTickCount() + delayTicks, () -> {
            if (server.isLoaded(this.worldPosition)) {
               float echoPitch = Math.max(0.7F, 0.84F + (server.random.nextFloat() - 0.5F) * 0.06F);
               float echoVol = Math.max(0.35F, Math.min(2.0F, echoEnclosure * 1.6F));
               server.playSound(null, pos.x, pos.y, pos.z, (SoundEvent)BFSounds.LAUNCH_ECHO.get(), SoundSource.BLOCKS, echoVol, echoPitch);
            }
         }));
      }
   }

   private void resetLaunch() {
      if (this.level instanceof ServerLevel server) {
         this.holdLaunchChunk(server, false);
         ActiveFlights.get(server).cancelLaunch(this.queuedMissionId);
      }

      if (this.level instanceof ServerLevel server && this.queuedOwner != null) {
         ServerPlayer pilot = server.getServer().getPlayerList().getPlayer(this.queuedOwner);
         if (pilot != null) {
            DeepStrikeNetwork.sendLaunchCountdown(pilot, this.worldPosition, this.queuedMissionId, this.launchAtTick, false, "FINISHED");
         }
      }

      if (this.routePlanner != null) {
         this.routePlanner.cancel();
      }

      if (this.flightTerrain != null) {
         this.flightTerrain.close();
         this.flightTerrain = null;
      }

      this.firingSlot = -1;
      this.launchStarted = -1L;
      this.launchAtTick = -1L;
      this.countdownStarted = false;
      this.countdownSent = false;
      this.coverEjected = false;
      this.queuedOwner = null;
      this.queuedMissionId = null;
      this.queuedFrequency = "";
      this.queuedRoute = List.of();
      this.queuedPeacekeeperPlan = null;
      this.routePlanner = null;
      this.plannedRoute = null;
      this.planningFinished = false;
      this.changed();
   }

   public void setRemoved() {
      if (this.level instanceof ServerLevel server && this.firingSlot >= 0 && this.queuedOwner != null) {
         ServerPlayer pilot = server.getServer().getPlayerList().getPlayer(this.queuedOwner);
         if (pilot != null) {
            DeepStrikeNetwork.sendLaunchCountdown(pilot, this.worldPosition, this.queuedMissionId, this.launchAtTick, false, "FINISHED");
         }
      }

      if (this.routePlanner != null) {
         this.routePlanner.cancel();
      }

      if (this.flightTerrain != null) {
         this.flightTerrain.close();
         this.flightTerrain = null;
      }

      super.setRemoved();
   }

   private void changed() {
      this.setChanged();
      this.collisionDirty = true;
      if (this.level != null) {
         this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
      }
   }

   public AABB getRenderBoundingBox() {
      return new AABB(this.worldPosition).inflate(7.0);
   }

   protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
      super.saveAdditional(tag, registries);
      DeepStrikeData.stamp(tag);
      if (this.commandNetwork != null) {
         tag.putUUID("CommandNetwork", this.commandNetwork);
      }

      tag.putBoolean("CommandHold", this.commandHold);
      tag.putBoolean("CommandLaunch", this.commandLaunch);
      tag.putUUID("LaunchJournalId", this.journalId);
      if (this.ownerId != null) {
         tag.putUUID("UnitOwner", this.ownerId);
      }

      tag.putBoolean("PublicAccess", this.publicAccess);
      tag.putInt("PlatformFuelMask", this.platformFuelMask);
      tag.putInt("FallbackFuelBuckets", this.fallbackFuelBuckets);
      tag.putLongArray("CollisionVoxels", this.collisionBits);
      tag.putLongArray("CollisionParts", this.collisionParts.stream().mapToLong(BlockPos::asLong).toArray());
      ContainerHelper.saveAllItems(tag, this.ammunition, registries);
      tag.putLongArray("LoadedAt", this.loadedAt);
      tag.putFloat("Yaw", this.yaw);
      tag.putFloat("Elevation", this.elevation);
      tag.putFloat("TargetYaw", this.targetYaw);
      tag.putFloat("TargetElevation", this.targetElevation);
      tag.putInt("FiringSlot", this.firingSlot);
      tag.putLong("LaunchStarted", this.launchStarted);
      tag.putLong("LaunchAtTick", this.launchAtTick);
      tag.putBoolean("CountdownStarted", this.countdownStarted);
      tag.putBoolean("CoverEjected", this.coverEjected);
      tag.putBoolean("QueuedBallistic", this.queuedBallistic);
      tag.putString("QueuedFrequency", this.queuedFrequency);
      tag.putString("RadioFrequency", this.transmitFrequency);
      tag.putString("RouteFallbackReason", this.routeFallbackReason);
      if (this.queuedPeacekeeperPlan != null) {
         tag.put("QueuedPeacekeeperPlan", PeacekeeperDynamics.save(this.queuedPeacekeeperPlan));
      }

      if (this.queuedOwner != null) {
         tag.putUUID("QueuedOwner", this.queuedOwner);
      }

      if (this.queuedMissionId != null) {
         tag.putUUID("QueuedMissionId", this.queuedMissionId);
      }

      ListTag points = new ListTag();

      for (RoutePoint p : this.queuedRoute) {
         CompoundTag point = new CompoundTag();
         point.putDouble("X", p.x());
         point.putDouble("Y", p.y());
         point.putDouble("Z", p.z());
         point.putDouble("Speed", p.speed());
         points.add(point);
      }

      tag.put("QueuedRoute", points);
      tag.putBoolean("PlanningFinished", this.planningFinished);
      if (this.plannedRoute != null) {
         ListTag planned = new ListTag();

         for (RoutePoint p : this.plannedRoute) {
            CompoundTag point = new CompoundTag();
            point.putDouble("X", p.x());
            point.putDouble("Y", p.y());
            point.putDouble("Z", p.z());
            point.putDouble("Speed", p.speed());
            planned.add(point);
         }

         tag.put("PlannedRoute", planned);
      }
   }

   public void loadAdditional(CompoundTag source, net.minecraft.core.HolderLookup.Provider registries) {
      CompoundTag tag = DeepStrikeData.migrateLauncher(source);
      super.loadAdditional(tag, registries);
      if (tag.hasUUID("LaunchJournalId")) {
         this.journalId = tag.getUUID("LaunchJournalId");
      }

      this.commandNetwork = tag.hasUUID("CommandNetwork") ? tag.getUUID("CommandNetwork") : null;
      this.commandHold = tag.getBoolean("CommandHold");
      this.commandLaunch = tag.getBoolean("CommandLaunch");
      this.ownerId = tag.hasUUID("UnitOwner") ? tag.getUUID("UnitOwner") : null;
      this.publicAccess = !tag.contains("PublicAccess") || tag.getBoolean("PublicAccess");
      this.platformFuelMask = tag.getInt("PlatformFuelMask") & 7;
      this.fallbackFuelBuckets = Math.max(0, Math.min(3, tag.getInt("FallbackFuelBuckets")));
      this.collisionBits = tag.getLongArray("CollisionVoxels");
      this.collisionShape = LauncherCollision.shape(this.collisionBits);
      this.collisionParts = new HashSet<>();

      for (long position : tag.getLongArray("CollisionParts")) {
         this.collisionParts.add(BlockPos.of(position));
      }

      this.ammunition.clear();
      ContainerHelper.loadAllItems(tag, this.ammunition, registries);
      if (tag.contains("Loaded") && !tag.contains("Items")) {
         this.ammunition.set(0, ItemStack.parseOptional(registries, tag.getCompound("Loaded")));
      }

      long[] times = tag.getLongArray("LoadedAt");

      for (int i = 0; i < 6; i++) {
         this.loadedAt[i] = i < times.length ? times[i] : 0L;
      }

      this.yaw = tag.getFloat("Yaw");
      this.elevation = tag.getFloat("Elevation");
      this.targetYaw = tag.contains("TargetYaw") ? tag.getFloat("TargetYaw") : this.yaw;
      this.targetElevation = tag.contains("TargetElevation") ? tag.getFloat("TargetElevation") : this.elevation;
      this.firingSlot = tag.contains("FiringSlot") ? tag.getInt("FiringSlot") : -1;
      if (this.firingSlot < 0 || this.firingSlot >= this.capacity()) {
         this.firingSlot = -1;
      }

      this.launchStarted = tag.contains("LaunchStarted") ? tag.getLong("LaunchStarted") : -1L;
      this.launchAtTick = tag.contains("LaunchAtTick") ? tag.getLong("LaunchAtTick") : -1L;
      this.countdownStarted = this.firingSlot >= 0;
      this.countdownSent = false;
      this.coverEjected = tag.getBoolean("CoverEjected");
      this.queuedBallistic = tag.getBoolean("QueuedBallistic");
      this.queuedFrequency = RadioFrequency.valid(tag.getString("QueuedFrequency")) ? tag.getString("QueuedFrequency") : "";
      this.transmitFrequency = RadioFrequency.valid(tag.getString("RadioFrequency")) ? tag.getString("RadioFrequency") : "";
      this.queuedOwner = tag.hasUUID("QueuedOwner") ? tag.getUUID("QueuedOwner") : null;
      this.queuedMissionId = tag.hasUUID("QueuedMissionId") ? tag.getUUID("QueuedMissionId") : null;
      List<RoutePoint> points = new ArrayList<>();

      for (Tag raw : tag.getList("QueuedRoute", 10)) {
         CompoundTag p = (CompoundTag)raw;
         double x = p.getDouble("X");
         double y = p.getDouble("Y");
         double z = p.getDouble("Z");
         double speed = p.getDouble("Speed");
         if (points.size() < 32 && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Double.isFinite(speed)) {
            points.add(new RoutePoint(x, y, z, speed));
         }
      }

      this.queuedRoute = List.copyOf(points);
      this.collisionDirty = true;
      this.queuedPeacekeeperPlan = tag.contains("QueuedPeacekeeperPlan") ? PeacekeeperDynamics.load(tag.getCompound("QueuedPeacekeeperPlan")) : null;
      this.routeFallbackReason = tag.getString("RouteFallbackReason");
      this.plannedRoute = null;
      this.planningFinished = tag.getBoolean("PlanningFinished");
      if (tag.contains("PlannedRoute", 9)) {
         ListTag planned = tag.getList("PlannedRoute", 10);
         List<RoutePoint> restored = new ArrayList<>();
         boolean valid = !planned.isEmpty() && planned.size() <= 32768;
         if (valid) {
            for (Tag rawx : planned) {
               CompoundTag p = (CompoundTag)rawx;
               double x = p.getDouble("X");
               double y = p.getDouble("Y");
               double z = p.getDouble("Z");
               double speed = p.getDouble("Speed");
               if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(speed) || speed <= 0.0) {
                  valid = false;
                  break;
               }

               restored.add(new RoutePoint(x, y, z, speed));
            }
         }

         if (valid) {
            this.plannedRoute = List.copyOf(restored);
         } else {
            this.planningFinished = false;
         }
      }
   }

   public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
      CompoundTag tag = NbtCompat.saveBE(this);
      tag.remove("PlannedRoute");
      tag.remove("PlanningFinished");
      return tag;
   }

   public ClientboundBlockEntityDataPacket getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }
}
