package net.borisshoes.endernexus.cca;

import net.borisshoes.endernexus.Destination;
import org.ladysnake.cca.api.v3.component.ComponentV3;

import java.util.List;

public interface IDestinationComponent extends ComponentV3 {
   List<Destination> getDestinations();
   boolean addDestination(Destination dest);
   boolean removeDestination(Destination dest);
}
