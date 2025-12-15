package net.borisshoes.endernexus.cca;

import net.borisshoes.endernexus.Destination;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

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
   public void readData(ValueInput view){
      try{
         destinations.clear();
         for(CompoundTag destTag : view.listOrEmpty("Destinations", CompoundTag.CODEC)){
            ListTag posTag = destTag.getList("pos").orElse(new ListTag());
            Vec3 pos = new Vec3(posTag.getDoubleOr(0,0),posTag.getDoubleOr(1,0),posTag.getDoubleOr(2,0));
            ListTag rotTag = destTag.getList("rot").orElse(new ListTag());
            Vec2 rot = new Vec2(rotTag.getFloatOr(0,0),rotTag.getFloatOr(1,0));
            destinations.add(new Destination(destTag.getStringOr("name",""),pos,rot,destTag.getStringOr("world","")));
         }
      }catch(Exception e){
         e.printStackTrace();
      }
   }
   
   @Override
   public void writeData(ValueOutput view){
      try{
         ValueOutput.TypedOutputList<CompoundTag> listAppender = view.list("Destinations", CompoundTag.CODEC);
         for(Destination dest : destinations){
            CompoundTag blockTag = new CompoundTag();
            ListTag pos = new ListTag();
            pos.add(0, DoubleTag.valueOf(dest.getPos().x()));
            pos.add(1, DoubleTag.valueOf(dest.getPos().y()));
            pos.add(2, DoubleTag.valueOf(dest.getPos().z()));
            ListTag rot = new ListTag();
            rot.add(0, FloatTag.valueOf(dest.getRotation().x));
            rot.add(1, FloatTag.valueOf(dest.getRotation().y));
            blockTag.put("pos",pos);
            blockTag.put("rot",rot);
            blockTag.putString("name",dest.getName());
            blockTag.putString("world",dest.getWorldKey());
            listAppender.add(blockTag);
         }
      }catch(Exception e){
         e.printStackTrace();
      }
   }
}
