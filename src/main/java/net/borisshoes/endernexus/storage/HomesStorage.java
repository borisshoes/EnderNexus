package net.borisshoes.endernexus.storage;

import net.borisshoes.borislib.datastorage.DataKey;
import net.borisshoes.borislib.datastorage.DataRegistry;
import net.borisshoes.borislib.datastorage.StorableData;
import net.borisshoes.endernexus.Destination;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static net.borisshoes.endernexus.EnderNexus.MOD_ID;

public class HomesStorage implements StorableData {
   
   public static final DataKey<HomesStorage> KEY = DataRegistry.register(DataKey.ofPlayer(Identifier.fromNamespaceAndPath(MOD_ID, "timestamp"), HomesStorage::new));
   
   public final Set<Destination> homes = new HashSet<>();
   public final UUID playerID;
   
   public HomesStorage(UUID playerID){
      this.playerID = playerID;
   }
   
   @Override
   public void read(ValueInput view){
      homes.clear();
      for(Destination dest : view.listOrEmpty("homes", Destination.CODEC)){
         homes.add(dest);
      }
   }
   
   @Override
   public void writeNbt(CompoundTag tag){
      ListTag homesList = new ListTag();
      for(Destination home : homes){
         Destination.CODEC.encodeStart(NbtOps.INSTANCE, home).result().ifPresent(homesList::add);
      }
      tag.put("homes", homesList);
   }
   
   public Set<Destination> getHomes(){
      return homes;
   }
   
   public boolean addHome(Destination home){
      if(homes.contains(home)) return false;
      return homes.add(home);
   }
   
   public boolean removeHome(Destination home){
      return homes.remove(home);
   }
}
