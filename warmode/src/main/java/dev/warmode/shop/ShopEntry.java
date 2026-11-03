package dev.warmode.shop;

import dev.warmode.game.WarTeam;
import net.minecraft.network.FriendlyByteBuf;

/**
 * One line of the supply shop.
 *
 * @param id       unique key used by the purchase packet
 * @param item     registry id of the item that is handed out
 * @param count    stack size per purchase
 * @param price    cost in team supply
 * @param category display group, also a lang key suffix
 * @param team     "any", "blue" or "red"
 */
public record ShopEntry(String id, String item, int count, int price, String category, String team) {
   public boolean availableTo(WarTeam viewer) {
      return this.team == null || this.team.isEmpty() || this.team.equals("any") || viewer != null && this.team.equalsIgnoreCase(viewer.id);
   }

   public void write(FriendlyByteBuf buf) {
      buf.writeUtf(this.id);
      buf.writeUtf(this.item);
      buf.writeVarInt(this.count);
      buf.writeVarInt(this.price);
      buf.writeUtf(this.category);
   }

   public static ShopEntry read(FriendlyByteBuf buf) {
      return new ShopEntry(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(), "any");
   }
}
