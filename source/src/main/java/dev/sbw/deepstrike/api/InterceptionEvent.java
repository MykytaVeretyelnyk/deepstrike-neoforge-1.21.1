package dev.sbw.deepstrike.api;

import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

/**
 * Posted on the NeoForge event bus (server side) after an air-defense interceptor destroys a target:
 * a guided missile, an aircraft or Project: Explosive ordnance. Intended for game-mode mods (kill rewards, scoring).
 */
public class InterceptionEvent extends Event {
   private final Entity target;
   private final Entity interceptor;
   private final UUID attackerOwner;
   private final UUID defenderOwner;
   private final Entity defenseUnit;
   private final int defense;

   public InterceptionEvent(Entity target, Entity interceptor, @Nullable UUID attackerOwner, @Nullable UUID defenderOwner, @Nullable Entity defenseUnit, int defense) {
      this.target = target;
      this.interceptor = interceptor;
      this.attackerOwner = attackerOwner;
      this.defenderOwner = defenderOwner;
      this.defenseUnit = defenseUnit;
      this.defense = defense;
   }

   /** The destroyed target. It may already be removed or marked as intercepted when handlers run. */
   public Entity target() {
      return this.target;
   }

   /** The interceptor missile that scored the hit. */
   public Entity interceptor() {
      return this.interceptor;
   }

   /** Owner of the target (launching player) when known. */
   @Nullable
   public UUID attackerOwner() {
      return this.attackerOwner;
   }

   /** Owner of the defending side (operator or shooter) when the unit stores one. */
   @Nullable
   public UUID defenderOwner() {
      return this.defenderOwner;
   }

   /** The launcher entity (Patriot, Iron Dome) that fired the interceptor, when available. */
   @Nullable
   public Entity defenseUnit() {
      return this.defenseUnit;
   }

   /** Warhead type of the interceptor as used by PatriotInterceptorEntity (3 is Tamir, 2 is MANPADS). */
   public int defense() {
      return this.defense;
   }
}
