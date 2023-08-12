package net.borisshoes.endernexus.cca;

import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistryV3;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy;
import net.minecraft.util.Identifier;

public class PlayerComponentInitializer implements EntityComponentInitializer {
   public static final ComponentKey<IDestinationComponent> HOMES = ComponentRegistryV3.INSTANCE.getOrCreate(new Identifier("endernexus", "homes"), IDestinationComponent.class);
   
   @Override
   public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
      registry.registerForPlayers(HOMES, playerEntity -> new DestinationComponent(), RespawnCopyStrategy.ALWAYS_COPY);
   }
}