package dev.colonyjournal.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.colonyjournal.net.Views.ColonyRef;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.data.registries.VanillaRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** NBT round trip of the saved data. Needs only the vanilla bootstrap, not a running game. */
class EconomyDataTest {
   private static HolderLookup.Provider registries;

   @BeforeAll
   static void bootstrap() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
      registries = VanillaRegistries.createLookup();
   }

   @Test
   void roundTrip() {
      EconomyData d = new EconomyData();
      ColonyRef ref = new ColonyRef("minecraft:overworld", 7);
      EconomyData.Ledger l = d.ledger(ref, 15);
      l.setTaxRate(22);
      l.setLastDay(3);
      for (int i = 0; i < 20; i++) {
         l.settle(100 + i, 40, 0, 4 + i, 500);
      }

      l.setBalance(-50);
      l.setStock("minecraft:iron_ingot", 33);
      l.setStock("minecraft:coal", 5);
      UUID poster = UUID.randomUUID();
      UUID offerId = UUID.randomUUID();
      ItemStack stack = new ItemStack(Items.DIAMOND, 17);
      d.addOffer(new EconomyData.Offer(offerId, poster, "Steve", ref, "Newtown", stack, 4200));
      UUID other = UUID.randomUUID();
      d.addClaim(other, new ItemStack(Items.IRON_INGOT, 5));

      CompoundTag tag = d.save(new CompoundTag(), registries);
      EconomyData back = EconomyData.load(tag, registries);

      EconomyData.Ledger bl = back.peekLedger(ref);
      assertNotNull(bl);
      assertEquals(-50, bl.balance());
      assertEquals(22, bl.taxRate());
      assertEquals(23, bl.lastDay());
      assertEquals(EconomyData.HISTORY, bl.history().size());
      assertEquals(l.history(), bl.history());
      assertEquals(l.debtDays(), bl.debtDays());
      assertEquals(l.lastIncome(), bl.lastIncome());
      assertEquals(l.lastTrade(), bl.lastTrade());
      assertEquals(33, bl.stockOf("minecraft:iron_ingot"));
      assertEquals(5, bl.stockOf("minecraft:coal"));
      assertEquals(0, bl.stockOf("minecraft:gold_ingot"));

      EconomyData.Offer bo = back.offer(offerId);
      assertNotNull(bo);
      assertEquals(poster, bo.poster());
      assertEquals("Newtown", bo.colonyName());
      assertEquals(4200, bo.price());
      assertTrue(ItemStack.matches(stack, bo.stack()));
      List<ItemStack> claims = back.claims(other);
      assertEquals(1, claims.size());
      assertEquals(5, claims.get(0).getCount());

      // deleting the colony moves the escrowed item to the poster and drops the ledger
      assertEquals(1, back.dissolveColony(ref));
      assertNull(back.peekLedger(ref));
      assertNull(back.offer(offerId));
      assertEquals(17, back.claims(poster).get(0).getCount());
   }

   @Test
   void tradeIsPersisted() {
      EconomyData d = new EconomyData();
      ColonyRef ref = new ColonyRef("minecraft:overworld", 3);
      d.ledger(ref, 12).settle(10, 4, 21, 1, 500);
      EconomyData back = EconomyData.load(d.save(new CompoundTag(), registries), registries);
      assertEquals(21, back.peekLedger(ref).lastTrade());
      assertEquals(27, back.peekLedger(ref).balance());
      assertEquals(27L, back.peekLedger(ref).history().get(0));
   }

   @Test
   void oldSaveWithoutStockLoadsEmpty() {
      EconomyData d = new EconomyData();
      ColonyRef ref = new ColonyRef("minecraft:overworld", 1);
      d.ledger(ref, 12).setBalance(77);
      CompoundTag tag = d.save(new CompoundTag(), registries);
      // strip the new tags to imitate a save written before the goods market existed
      CompoundTag ledger = tag.getList("ledgers", 10).getCompound(0);
      ledger.remove("stock");
      ledger.remove("lastTrade");
      EconomyData back = EconomyData.load(tag, registries);
      EconomyData.Ledger bl = back.peekLedger(ref);
      assertNotNull(bl);
      assertEquals(77, bl.balance());
      assertTrue(bl.stock().isEmpty());
      assertEquals(0, bl.lastTrade());
   }

   @Test
   void debtFloorStopsTheSpiral() {
      EconomyData d = new EconomyData();
      EconomyData.Ledger l = d.ledger(new ColonyRef("minecraft:overworld", 2), 12);
      l.setBalance(-400);
      l.settle(0, 300, 0, 1, 500);
      assertEquals(-500, l.balance());
      l.settle(0, 300, 0, 2, 500);
      assertEquals(-500, l.balance());
      l.setBalance(-900);
      l.settle(0, 50, 0, 3, 500);
      assertEquals(-900, l.balance());
      l.settle(100, 0, 0, 4, 500);
      assertEquals(-800, l.balance());
   }
}
