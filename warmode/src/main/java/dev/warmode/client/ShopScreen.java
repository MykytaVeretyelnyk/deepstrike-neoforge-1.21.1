package dev.warmode.client;

import dev.warmode.game.WarTeam;
import dev.warmode.net.WarNet;
import dev.warmode.shop.ShopEntry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** The supply shop: category tabs, a paged list of lots and a buy button on each row. */
public final class ShopScreen extends Screen {
   private static final int ROWS = 7;
   private static final int ROW_HEIGHT = 24;
   private static final int WIDTH = 330;

   private final BlockPos terminal;
   private final List<ShopEntry> entries;
   private final List<String> categories = new ArrayList<>();
   private final List<Button> buyButtons = new ArrayList<>();
   private final List<ShopEntry> visible = new ArrayList<>();
   private String category;
   private int page;

   public ShopScreen(BlockPos terminal, List<ShopEntry> entries) {
      super(Component.translatable("screen.warmode.shop"));
      this.terminal = terminal;
      this.entries = entries;
      Set<String> seen = new LinkedHashSet<>();

      for (ShopEntry entry : entries) {
         seen.add(entry.category());
      }

      this.categories.addAll(seen);
      this.category = this.categories.isEmpty() ? "" : this.categories.get(0);
   }

   private static Component categoryTitle(String category) {
      return Component.translatableWithFallback("shop.warmode.cat." + category, category);
   }

   private static ItemStack stackOf(ShopEntry entry) {
      ResourceLocation id = ResourceLocation.tryParse(entry.item());
      return id != null && BuiltInRegistries.ITEM.containsKey(id) ? new ItemStack(BuiltInRegistries.ITEM.get(id), entry.count()) : ItemStack.EMPTY;
   }

   private int left() {
      return (this.width - WIDTH) / 2;
   }

   @Override
   protected void init() {
      this.buyButtons.clear();
      this.visible.clear();
      int left = this.left();
      int x = left;
      int tabWidth = Math.max(40, Math.min(80, WIDTH / Math.max(1, this.categories.size())));

      for (String cat : this.categories) {
         Button tab = Button.builder(categoryTitle(cat), b -> {
            this.category = cat;
            this.page = 0;
            this.rebuildWidgets();
         }).bounds(x, 28, tabWidth - 2, 18).build();
         tab.active = !cat.equals(this.category);
         this.addRenderableWidget(tab);
         x += tabWidth;
      }

      List<ShopEntry> inCategory = new ArrayList<>();

      for (ShopEntry entry : this.entries) {
         if (entry.category().equals(this.category)) {
            inCategory.add(entry);
         }
      }

      int pages = Math.max(1, (inCategory.size() + ROWS - 1) / ROWS);
      this.page = Math.min(this.page, pages - 1);
      int top = 54;

      for (int row = 0; row < ROWS; row++) {
         int index = this.page * ROWS + row;
         if (index >= inCategory.size()) {
            break;
         }

         ShopEntry entry = inCategory.get(index);
         this.visible.add(entry);
         Button buy = Button.builder(
            Component.translatable("screen.warmode.buy"),
            b -> PacketDistributor.sendToServer(new WarNet.Buy(this.terminal, entry.id()))
         ).bounds(left + WIDTH - 60, top + row * ROW_HEIGHT, 60, 20).build();
         this.buyButtons.add(buy);
         this.addRenderableWidget(buy);
      }

      int footer = top + ROWS * ROW_HEIGHT + 6;
      Button prev = Button.builder(Component.literal("<"), b -> {
         this.page--;
         this.rebuildWidgets();
      }).bounds(left, footer, 30, 20).build();
      prev.active = this.page > 0;
      Button next = Button.builder(Component.literal(">"), b -> {
         this.page++;
         this.rebuildWidgets();
      }).bounds(left + WIDTH - 30, footer, 30, 20).build();
      next.active = this.page < pages - 1;
      this.addRenderableWidget(prev);
      this.addRenderableWidget(next);
   }

   @Override
   public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
      this.renderTransparentBackground(g);
      int left = this.left();
      WarNet.State s = WarClient.state;
      WarTeam team = s == null ? null : WarTeam.byOrdinal(s.team());
      int bank = s == null || team == null ? 0 : WarClient.bankOf(s, team);
      g.drawString(this.font, this.title, left, 10, 0xFFFFFFFF, true);
      if (team != null) {
         Component bankLine = Component.translatable("screen.warmode.bank", bank);
         g.drawString(this.font, bankLine, left + WIDTH - this.font.width(bankLine), 10, 0xFF000000 | team.color, true);
      }

      int top = 54;

      for (int row = 0; row < this.visible.size(); row++) {
         ShopEntry entry = this.visible.get(row);
         ItemStack stack = stackOf(entry);
         int y = top + row * ROW_HEIGHT;
         g.fill(left, y, left + WIDTH - 64, y + 20, 0x66000000);
         g.renderItem(stack, left + 2, y + 2);
         String name = (entry.count() > 1 ? entry.count() + "x " : "") + stack.getHoverName().getString();
         g.drawString(this.font, this.font.plainSubstrByWidth(name, 200), left + 24, y + 6, 0xFFFFFFFF, true);
         String price = Integer.toString(entry.price());
         boolean affordable = bank >= entry.price();
         g.drawString(this.font, price, left + WIDTH - 70 - this.font.width(price), y + 6, affordable ? 0xFF7CFC7C : 0xFFFF6B6B, true);
         this.buyButtons.get(row).active = affordable;
      }

      super.render(g, mouseX, mouseY, partial);

      for (int row = 0; row < this.visible.size(); row++) {
         int y = top + row * ROW_HEIGHT;
         if (mouseX >= left && mouseX < left + WIDTH - 64 && mouseY >= y && mouseY < y + 20) {
            g.renderTooltip(this.font, stackOf(this.visible.get(row)), mouseX, mouseY);
         }
      }
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }
}
