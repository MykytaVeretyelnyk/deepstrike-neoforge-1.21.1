package dev.colonyjournal.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.colonyjournal.analysis.ColonyReport.Issue;
import dev.colonyjournal.analysis.ColonyReport.Row;
import dev.colonyjournal.analysis.ColonyReport.Severity;
import dev.colonyjournal.analysis.ColonyReport.Status;
import dev.colonyjournal.client.OverviewModel.Card;
import dev.colonyjournal.client.OverviewModel.Facts;
import dev.colonyjournal.client.OverviewModel.Light;
import dev.colonyjournal.client.OverviewModel.Todo;
import dev.colonyjournal.net.Views.ColonyRef;
import dev.colonyjournal.net.Views.EconomyView;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OverviewModelTest {
   @BeforeAll
   static void bootstrap() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   private static EconomyView eco(long balance, int tax, int tolerable, double taxFactor, long income, long expense, double prosperity, long trade) {
      return new EconomyView(new ColonyRef("minecraft:overworld", 1), "c", balance, tax, tolerable, 50, taxFactor, income, expense, true, List.of(), List.of(), List.of(), prosperity, trade);
   }

   private static EconomyView healthy() {
      return eco(500, 12, 36, 1.07, 100, 20, 0.6, 15);
   }

   private static Facts facts(List<Issue> issues, List<Row> rows, int citizens, int limit, double happiness, EconomyView eco) {
      return new Facts(issues, rows, citizens, limit, happiness, eco);
   }

   private static Issue issue(Severity s, String focus) {
      return new Issue(s, Component.literal("x"), focus);
   }

   private static Row row(String type, Status status) {
      return new Row(BlockPos.ZERO, type, Component.literal(type), 1, 5, 0, 1, 0, status);
   }

   @Test
   void healthyColonyIsGreenAndGetsSuggestions() {
      Facts f = facts(List.of(), List.of(), 10, 20, 8.0, healthy());
      for (Card c : OverviewModel.cards(f)) {
         assertEquals(Light.GREEN, c.light(), c.id());
      }

      List<Todo> todos = OverviewModel.todos(f);
      assertEquals(2, todos.size());
      assertEquals(Light.INFO, todos.get(0).light());
   }

   @Test
   void loadingEconomyGivesGreyCardsAndNoEconomyActions() {
      Facts f = facts(List.of(), List.of(), 10, 20, 8.0, null);
      List<Card> cards = OverviewModel.cards(f);
      assertEquals(Light.GREEN, cards.get(0).light());
      assertEquals(Light.NONE, cards.get(1).light());
      assertEquals(Light.GREEN, cards.get(2).light());
      assertEquals(Light.NONE, cards.get(3).light());
      for (Todo t : OverviewModel.todos(f)) {
         assertFalse(t.tab().equals("economy") && t.light() == Light.RED);
      }
   }

   @Test
   void errorsTurnTheChainCardRedAndComeFirst() {
      Facts f = facts(List.of(issue(Severity.ERROR, "miner"), issue(Severity.WARN, "smeltery")), List.of(), 10, 20, 8.0, healthy());
      assertEquals(Light.RED, OverviewModel.chains(f).light());
      Todo first = OverviewModel.todos(f).get(0);
      assertEquals(Light.RED, first.light());
      assertEquals("map", first.tab());
      assertEquals("miner", first.focus());
   }

   @Test
   void basicBuildingsOpenTheLinksTabAndUnknownOnesTheProblemsTab() {
      Facts f = facts(List.of(issue(Severity.ERROR, "townhall")), List.of(), 10, 20, 8.0, healthy());
      assertEquals("map", OverviewModel.todos(f).get(0).tab());
      assertEquals("townhall", OverviewModel.todos(f).get(0).focus());
      Facts g = facts(List.of(issue(Severity.ERROR, "stash")), List.of(), 10, 20, 8.0, healthy());
      assertEquals("problems", OverviewModel.todos(g).get(0).tab());
   }

   @Test
   void debtAndHighTaxAreFlagged() {
      Facts f = facts(List.of(), List.of(), 10, 20, 8.0, eco(-40, 30, 20, 0.7, 100, 20, 0.6, 0));
      assertEquals(Light.RED, OverviewModel.treasury(f).light());
      List<Todo> todos = OverviewModel.todos(f);
      assertEquals("journal.colonyjournal.ov.todo.debt", todos.get(0).key());
      assertEquals("journal.colonyjournal.ov.todo.tax_high", todos.get(1).key());
   }

   @Test
   void highTaxWithMoneyIsYellow() {
      Facts f = facts(List.of(), List.of(), 10, 20, 8.0, eco(900, 30, 20, 0.8, 100, 20, 0.6, 0));
      assertEquals(Light.YELLOW, OverviewModel.treasury(f).light());
   }

   @Test
   void runningOutOfCoinsIsRed() {
      Facts f = facts(List.of(), List.of(), 10, 20, 8.0, eco(30, 12, 36, 1.0, 10, 40, 0.6, 0));
      assertEquals(Light.RED, OverviewModel.treasury(f).light());
      assertEquals("journal.colonyjournal.ov.treasury.runout", OverviewModel.treasury(f).key());
   }

   @Test
   void moodFollowsHappinessAndTaxFactor() {
      assertEquals(Light.RED, OverviewModel.mood(facts(List.of(), List.of(), 10, 20, 3.0, healthy())).light());
      assertEquals(Light.YELLOW, OverviewModel.mood(facts(List.of(), List.of(), 10, 20, 6.0, healthy())).light());
      assertEquals(Light.RED, OverviewModel.mood(facts(List.of(), List.of(), 10, 20, 9.0, eco(500, 50, 20, 0.5, 100, 20, 0.6, 0))).light());
      assertEquals(Light.YELLOW, OverviewModel.mood(facts(List.of(), List.of(), 10, 20, 9.0, eco(500, 25, 20, 0.9, 100, 20, 0.6, 0))).light());
   }

   @Test
   void tradeCardNeedsWorkingProducers() {
      assertEquals(Light.YELLOW, OverviewModel.trade(facts(List.of(), List.of(), 10, 20, 8.0, eco(500, 12, 36, 1.0, 0, 20, 0.6, 0))).light());
      assertEquals(Light.YELLOW, OverviewModel.trade(facts(List.of(), List.of(), 10, 20, 8.0, eco(500, 12, 36, 1.0, 50, 20, 0.1, 0))).light());
      assertEquals(Light.GREEN, OverviewModel.trade(facts(List.of(), List.of(), 10, 20, 8.0, healthy())).light());
   }

   @Test
   void housingAndWorkerlessBuildingsAreListed() {
      Facts f = facts(List.of(), List.of(row("miner", Status.NO_WORKER), row("farmer", Status.NO_WORKER)), 20, 20, 8.0, healthy());
      List<Todo> todos = OverviewModel.todos(f);
      assertEquals("journal.colonyjournal.ov.todo.housing", todos.get(0).key());
      assertEquals("journal.colonyjournal.ov.todo.no_workers", todos.get(1).key());
      assertEquals("miner", todos.get(1).focus());
   }

   @Test
   void neverMoreThanFiveActions() {
      List<Issue> issues = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
         issues.add(issue(i < 6 ? Severity.ERROR : Severity.WARN, "miner"));
      }

      Facts f = facts(issues, List.of(row("farmer", Status.NO_WORKER)), 20, 20, 2.0, eco(-5, 40, 20, 0.6, 0, 30, 0.1, 0));
      List<Todo> todos = OverviewModel.todos(f);
      assertEquals(OverviewModel.MAX_TODOS, todos.size());
      assertTrue(todos.get(0).light() == Light.RED);
   }
}
