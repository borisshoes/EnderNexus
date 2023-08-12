package net.borisshoes.endernexus.cca;

import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistryV3;
import dev.onyxstudios.cca.api.v3.world.WorldComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.world.WorldComponentInitializer;
import net.minecraft.util.Identifier;

public class WorldDataComponentInitializer implements WorldComponentInitializer {
   public static final ComponentKey<IDestinationComponent> WARPS = ComponentRegistryV3.INSTANCE.getOrCreate(new Identifier("endernexus", "warps"), IDestinationComponent.class);
   
   @Override
   public void registerWorldComponentFactories(WorldComponentFactoryRegistry registry){
      registry.register(WARPS, DestinationComponent.class, world -> new DestinationComponent());
   }
}
