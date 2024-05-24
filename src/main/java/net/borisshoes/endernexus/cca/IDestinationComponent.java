package net.borisshoes.endernexus.cca;

import org.ladysnake.cca.api.v3.component.ComponentV3;
import net.borisshoes.endernexus.Destination;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

public interface IDestinationComponent extends ComponentV3 {
   List<Destination> getDestinations();
   boolean addDestination(Destination dest);
   boolean removeDestination(Destination dest);
}
