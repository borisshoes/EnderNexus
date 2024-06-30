package net.borisshoes.endernexus.cca;

import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistryV3;
import org.ladysnake.cca.api.v3.world.WorldComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.world.WorldComponentInitializer;
import net.minecraft.util.Identifier;

public class WorldDataComponentInitializer implements WorldComponentInitializer {
   public static final ComponentKey<IDestinationComponent> WARPS = ComponentRegistryV3.INSTANCE.getOrCreate(Identifier.of("endernexus", "warps"), IDestinationComponent.class);
   
   @Override
   public void registerWorldComponentFactories(WorldComponentFactoryRegistry registry){
      registry.register(WARPS, DestinationComponent.class, world -> new DestinationComponent());
   }
}
