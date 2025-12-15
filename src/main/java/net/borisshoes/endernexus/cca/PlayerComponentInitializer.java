package net.borisshoes.endernexus.cca;

import net.minecraft.resources.Identifier;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistryV3;
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;
import org.ladysnake.cca.api.v3.entity.RespawnCopyStrategy;

public class PlayerComponentInitializer implements EntityComponentInitializer {
   public static final ComponentKey<IDestinationComponent> HOMES = ComponentRegistryV3.INSTANCE.getOrCreate(Identifier.fromNamespaceAndPath("endernexus", "homes"), IDestinationComponent.class);
   
   @Override
   public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
      registry.registerForPlayers(HOMES, playerEntity -> new DestinationComponent(), RespawnCopyStrategy.ALWAYS_COPY);
   }
}