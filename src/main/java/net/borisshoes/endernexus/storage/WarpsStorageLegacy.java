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

import static net.borisshoes.endernexus.EnderNexus.LOGGER;
import static net.borisshoes.endernexus.EnderNexus.MOD_ID;

/**
 * Legacy storage class for migrating warps from the old "timestamp" key.
 * This class only reads data and does not write anything to prevent overwriting.
 */
public class WarpsStorageLegacy implements StorableData {
   
   public static final DataKey<WarpsStorageLegacy> KEY = DataRegistry.register(DataKey.ofGlobal(Identifier.fromNamespaceAndPath(MOD_ID, "timestamp"), WarpsStorageLegacy::new));
   
   public final Set<Destination> warps = new HashSet<>();
   private boolean hasData = false;
   
   public WarpsStorageLegacy(){}
   
   @Override
   public void read(ValueInput view){
      warps.clear();
      if(view.contains("warps")){
         for(Destination dest : view.listOrEmpty("warps", Destination.CODEC)){
            warps.add(dest);
         }
         if(!warps.isEmpty()){
            hasData = true;
            LOGGER.info("Found {} warps in legacy storage for migration", warps.size());
         }
      }
   }
   
   @Override
   public void writeNbt(CompoundTag tag){
      // Intentionally empty - we don't want to save anything to the old key
      // This prevents the "Saved 0 warps" overwrite issue
   }
   
   public Set<Destination> getWarps(){
      return warps;
   }
   
   public boolean hasLegacyData(){
      return hasData;
   }
}

