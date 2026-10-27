package dev.sbw.deepstrike.game;

import java.util.function.Consumer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;

public class AirDefenseMissileItem extends Item {
   private final String modelName;
   private final String textureName;
   private final float scale;

   public AirDefenseMissileItem(String modelName, String textureName, float scale, Properties properties) {
      super(properties);
      this.modelName = modelName;
      this.textureName = textureName;
      this.scale = scale;
   }


   public String modelName() {
      return this.modelName;
   }

   public String textureName() {
      return this.textureName;
   }

   public float scale() {
      return this.scale;
   }
}
