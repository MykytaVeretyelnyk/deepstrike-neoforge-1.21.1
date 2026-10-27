package dev.sbw.deepstrike.client;

final class DagorSmokeAnimation {
   static final int FLIP_X = 1;
   static final int FLIP_Y = 2;

   static int instanceSeed(int particleId, int dispatchSeed) {
      int hash = particleId * -1640531527 + dispatchSeed;
      hash ^= hash >>> 16;
      hash *= 2146121005;
      hash ^= hash >>> 15;
      hash *= -2073254261;
      return hash ^ hash >>> 16;
   }

   static DagorSmokeAnimation.Frame frame(
      DagorSmokeAnimation.RandomState random,
      float life,
      int columns,
      int rows,
      int startMin,
      int startMax,
      boolean flipX,
      boolean flipY,
      boolean randomFlipX,
      boolean randomFlipY,
      boolean disableLoop,
      boolean animated,
      boolean frameBlend,
      float speedMin,
      float speedMax,
      DagorSmokeAnimation.LifeCurve animationCurve
   ) {
      if (columns >= 1 && rows >= 1) {
         int count = Math.multiplyExact(columns, rows);
         float initial = startMin + (startMax - startMin) * random.nextFloat();
         float value = (float)Math.rint(initial);
         int flags = (flipX ? 1 : 0) | (flipY ? 2 : 0);
         if (randomFlipX && random.nextFloat() > 0.5F) {
            flags |= 1;
         }

         if (randomFlipY && random.nextFloat() > 0.5F) {
            flags |= 2;
         }

         if (animated) {
            float speed = speedMin + (speedMax - speedMin) * random.nextFloat();
            float time = animationCurve == null ? life : animationCurve.evaluate(life);
            value += time * speed * count;
            if (disableLoop) {
               value = Math.min(value, count - 1.0F);
            }
         }

         int whole = (int)value;
         int index = Integer.remainderUnsigned(whole, count);
         return new DagorSmokeAnimation.Frame(index, (index + 1) % count, frameBlend ? value - whole : 0.0F, flags);
      } else {
         throw new IllegalArgumentException("Empty source atlas");
      }
   }

   private DagorSmokeAnimation() {
   }

   record Frame(int index, int nextIndex, float blend, int flags) {
   }

   @FunctionalInterface
   interface LifeCurve {
      float evaluate(float var1);
   }

   static final class RandomState {
      private int seed;

      RandomState(int seed) {
         this.seed = seed;
      }

      int seed() {
         return this.seed;
      }

      int nextInt() {
         this.seed = 214013 * Integer.remainderUnsigned(this.seed, 16777215) + 2531011;
         return this.seed >>> 16 & 32767;
      }

      float nextFloat() {
         return this.nextInt() * 3.051851E-5F;
      }
   }
}
