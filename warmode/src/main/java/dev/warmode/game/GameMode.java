package dev.warmode.game;

public enum GameMode {
   /** Destroy the enemy headquarters; the timer, if set, is decided by remaining headquarters health. */
   HQ("hq"),
   /** First team to reach the score limit wins; the timer, if set, is decided by score. */
   SCORE("score"),
   /** Plays until the timer runs out; the higher score wins. */
   TIMED("timed"),
   /** No end condition: only the bank, rewards and shop. */
   ENDLESS("endless");

   public final String id;

   GameMode(String id) {
      this.id = id;
   }

   public static GameMode byId(String id) {
      for (GameMode mode : values()) {
         if (mode.id.equalsIgnoreCase(id)) {
            return mode;
         }
      }

      return null;
   }
}
