package dev.sbw.deepstrike.game;

import net.minecraft.world.phys.Vec3;

public final class CruiseDynamics {
   public static final int SUBSTEPS = 4;
   public static final double STEP_SECONDS = 0.0125;

   public static CruiseDynamics.State step(CruiseDynamics.State state, CruiseDynamics.Command command) {
      CruisePropulsion.State propulsion = state.propulsion() == null
         ? CruisePropulsion.create(command.kind, 1.0, 1.0, command.kind.maxRange)
         : state.propulsion();
      double approach = command.target.subtract(state.position).dot(state.velocity.normalize());
      if (command.terminal && !command.clearing && approach > 0.0 && command.target.distanceTo(state.position) <= state.velocity.length() * 1.0 * 2.0) {
         propulsion = propulsion.cutOff();
      }

      CruisePropulsion.State nextPropulsion = propulsion.advance(0.0125);
      double mass = CruisePropulsion.mass(command.kind, nextPropulsion);
      Vec3 gravity = new Vec3(0.0, -9.81, 0.0);
      Vec3 drag = FlightDynamics.drag(state.velocity, Atmosphere.density(state.position.y), 0.04, 0.5);
      if (!propulsion.powered() && !propulsion.terminalCoast()) {
         FlightDynamics.State coast = FlightDynamics.integrate(new FlightDynamics.State(state.position, state.velocity, mass), drag, gravity, 0.0125).state();
         return new CruiseDynamics.State(coast.position(), coast.velocity(), nextPropulsion);
      } else {
         double speed = state.velocity.length();
         Vec3 delta = command.target.subtract(state.position);
         double remaining = delta.length();
         double desiredSpeed = FlightUnits.metresPerSecond(command.speedKmh);
         double longitudinal = command.kind == MissileKind.SPIKE_NLOS
            ? (command.clearing ? 20 : 30)
            : (command.clearing ? 14.0 : (command.kind == MissileKind.JAGM && !command.fallback ? 26.0 : 20.0));
         double braking = command.kind == MissileKind.SPIKE_NLOS ? 30.0 : 28.0;
         double lateral = FlightDynamics.lateralAcceleration(command.kind, command.fallback);
         Vec3 current = speed > 1.0E-8 ? state.velocity.scale(1.0 / speed) : command.launchDirection.normalize();
         if (current.lengthSqr() < 1.0E-12) {
            current = delta.normalize();
         }

         Vec3 wanted;
         if (command.clearing) {
            wanted = command.launchDirection.normalize();
         } else if (!command.trackPath && (!command.terminal || !(delta.horizontalDistance() < Math.max(24.0, speed * 1.5)))) {
            double controlSpeed = Math.max(10.0, Math.min(desiredSpeed, speed + longitudinal * 0.0125));
            double vertical = Math.max(-controlSpeed * 0.45, Math.min(controlSpeed * 0.45, delta.y * 0.6));
            Vec3 horizontal = new Vec3(delta.x, 0.0, delta.z).normalize();
            wanted = horizontal.scale(Math.sqrt(Math.max(0.0, controlSpeed * controlSpeed - vertical * vertical))).add(0.0, vertical, 0.0).normalize();
         } else {
            wanted = delta.normalize();
         }

         if (wanted.lengthSqr() < 1.0E-12) {
            wanted = current;
         }

         desiredSpeed = Math.max(command.trackPath ? 0.05 : FlightUnits.metresPerSecond(command.kind.minSpeed), desiredSpeed);
         double age = propulsion.seconds();
         double spool = 1.0 - Math.exp(-age / 1.2);
         double acceleration = Math.min(longitudinal * (0.35 + 0.65 * spool), Math.max(0.0, (desiredSpeed - speed) * 0.75));
         double nextSpeed = Math.max(0.0, speed + (desiredSpeed >= speed ? acceleration : Math.max(-braking, (desiredSpeed - speed) * 0.75)) * 0.0125);
         if (propulsion.terminalCoast()) {
            nextSpeed = Math.max(0.0, speed + (gravity.dot(current) - drag.length() / mass) * 0.0125);
         }

         double angularCap = FlightDynamics.angularRate(command.kind, command.fallback) * 0.0125;
         double angleLimit = Math.min(angularCap, lateral * 0.0125 / Math.max(0.1, Math.max(speed, nextSpeed)));
         if (command.trackPath && !command.clearing) {
            double alignment = clamp(current.dot(wanted), -1.0, 1.0);
            double sine = alignment < 0.0 ? 1.0 : Math.sqrt(Math.max(0.0, 1.0 - alignment * alignment));
            double rate = 2.0 * Math.max(speed, nextSpeed) * sine / Math.max(2.0, remaining);
            angleLimit = Math.min(angleLimit, rate * 0.0125);
         }

         Vec3 direction = turn(current, wanted, angleLimit);
         Vec3 nextVelocity = direction.scale(nextSpeed);
         Vec3 thrust = nextVelocity.subtract(state.velocity).scale(mass / 0.0125).subtract(gravity.scale(mass)).subtract(drag);
         FlightDynamics.State result = FlightDynamics.integrate(
               new FlightDynamics.State(state.position, state.velocity, mass), thrust.add(drag), gravity, 0.0125
            )
            .state();
         return new CruiseDynamics.State(result.position(), result.velocity(), nextPropulsion);
      }
   }

   private static Vec3 turn(Vec3 current, Vec3 wanted, double limit) {
      double dot = clamp(current.dot(wanted), -1.0, 1.0);
      double angle = Math.acos(dot);
      if (angle <= limit) {
         return wanted;
      } else {
         Vec3 tangent = wanted.subtract(current.scale(dot));
         if (tangent.lengthSqr() < 1.0E-12) {
            tangent = current.cross(Math.abs(current.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0));
         }

         return current.scale(Math.cos(limit)).add(tangent.normalize().scale(Math.sin(limit))).normalize();
      }
   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }

   private CruiseDynamics() {
   }

   public record Command(
      Vec3 target, Vec3 launchDirection, double speedKmh, boolean clearing, boolean terminal, MissileKind kind, boolean fallback, boolean trackPath
   ) {
   }

   public record State(Vec3 position, Vec3 velocity, CruisePropulsion.State propulsion) {
      public State(Vec3 position, Vec3 velocity) {
         this(position, velocity, null);
      }
   }
}
