package dev.sbw.deepstrike.game;

import java.util.function.Consumer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.block.Block;

public final class ModelBlockItem extends BlockItem {
   public ModelBlockItem(Block block, Properties properties) {
      super(block, properties);
   }

}
