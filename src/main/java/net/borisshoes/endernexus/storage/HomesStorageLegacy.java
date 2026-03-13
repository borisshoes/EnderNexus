package net.borisshoes.endernexus.storage;

import net.borisshoes.borislib.datastorage.DataKey;
import net.borisshoes.borislib.datastorage.DataRegistry;
import net.borisshoes.borislib.datastorage.StorableData;
import net.borisshoes.endernexus.Destination;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static net.borisshoes.endernexus.EnderNexus.MOD_ID;

/**
 * Legacy storage class for migrating homes from the old "timestamp" key.
 * This class only reads data and does not write anything to prevent overwriting.
 */
public class HomesStorageLegacy implements StorableData {
   
   public static final DataKey<HomesStorageLegacy> KEY = DataRegistry.register(DataKey.ofPlayer(Identifier.fromNamespaceAndPath(MOD_ID, "timestamp"), HomesStorageLegacy::new));
   
   public final Set<Destination> homes = new HashSet<>();
   public final UUID playerID;
   private boolean hasData = false;
   
   public HomesStorageLegacy(UUID playerID){
      this.playerID = playerID;
   }
   
   @Override
   public void read(ValueInput view){
      homes.clear();
      if(view.contains("homes")){
         for(Destination dest : view.listOrEmpty("homes", Destination.CODEC)){
            homes.add(dest);
         }
         if(!homes.isEmpty()){
            hasData = true;
         }
      }
   }
   
   @Override
   public void writeNbt(CompoundTag tag){
      // Intentionally empty - we don't want to save anything to the old key
      // This prevents the "Saved 0 homes" overwrite issue
   }
   
   public Set<Destination> getHomes(){
      return homes;
   }
   
   public boolean hasLegacyData(){
      return hasData;
   }
}

