package net.borisshoes.endernexus.cca;

import net.borisshoes.endernexus.Destination;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class DestinationComponent implements IDestinationComponent{
   
   public final ArrayList<Destination> destinations = new ArrayList<>();
   
   @Override
   public List<Destination> getDestinations(){
      return destinations;
   }
   
   @Override
   public boolean addDestination(Destination dest){
      if (destinations.contains(dest)) return false;
      return destinations.add(dest);
   }
   
   @Override
   public boolean removeDestination(Destination dest){
      if (!destinations.contains(dest)) return false;
      return destinations.remove(dest);
   }
   
   @Override
   public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup){
      try{
         destinations.clear();
         NbtList destsTag = tag.getList("Destinations").orElse(new NbtList());
         for (NbtElement e : destsTag) {
            NbtCompound destTag = (NbtCompound) e;
            NbtList posTag = destTag.getList("pos").orElse(new NbtList());
            Vec3d pos = new Vec3d(posTag.getDouble(0,0),posTag.getDouble(1,0),posTag.getDouble(2,0));
            NbtList rotTag = destTag.getList("rot").orElse(new NbtList());
            Vec2f rot = new Vec2f(rotTag.getFloat(0,0),rotTag.getFloat(1,0));
            destinations.add(new Destination(destTag.getString("name",""),pos,rot,destTag.getString("world","")));
         }
      }catch(Exception e){
         e.printStackTrace();
      }
   }
   
   @Override
   public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup){
      try{
         NbtList destsTag = new NbtList();
         for(Destination dest : destinations){
            NbtCompound blockTag = new NbtCompound();
            NbtList pos = new NbtList();
            pos.add(0, NbtDouble.of(dest.getPos().getX()));
            pos.add(1, NbtDouble.of(dest.getPos().getY()));
            pos.add(2, NbtDouble.of(dest.getPos().getZ()));
            NbtList rot = new NbtList();
            rot.add(0, NbtFloat.of(dest.getRotation().x));
            rot.add(1, NbtFloat.of(dest.getRotation().y));
            blockTag.put("pos",pos);
            blockTag.put("rot",rot);
            blockTag.putString("name",dest.getName());
            blockTag.putString("world",dest.getWorldKey());
            destsTag.add(blockTag);
         }
         tag.put("Destinations",destsTag);
      }catch(Exception e){
         e.printStackTrace();
      }
   }
}
