package net.borisshoes.endernexus.storage;

import net.borisshoes.borislib.datastorage.DataAccess;
import net.borisshoes.borislib.datastorage.DataKey;
import net.borisshoes.borislib.datastorage.DataRegistry;
import net.borisshoes.borislib.datastorage.StorableData;
import net.borisshoes.endernexus.Destination;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.ValueInput;

import java.util.HashSet;
import java.util.Set;

import static net.borisshoes.endernexus.EnderNexus.LOGGER;
import static net.borisshoes.endernexus.EnderNexus.MOD_ID;

public class WarpsStorage implements StorableData {
   
   public static final DataKey<WarpsStorage> KEY = DataRegistry.register(DataKey.ofGlobal(Identifier.fromNamespaceAndPath(MOD_ID, "warps"), WarpsStorage::new));
   
   public final Set<Destination> warps = new HashSet<>();
   
   public WarpsStorage(){}
   
   @Override
   public void read(ValueInput view){
      warps.clear();
      int count = 0;
      for(Destination dest : view.listOrEmpty("warps", Destination.CODEC)){
         warps.add(dest);
         count++;
      }
      if(!view.contains("warps")){
         LOGGER.warn("Found no saved warps!");
      }else{
         LOGGER.info("Loaded {} warps", count);
      }
   }
   
   @Override
   public void writeNbt(CompoundTag tag){
      ListTag warpsList = new ListTag();
      for(Destination warp : warps){
         Destination.CODEC.encodeStart(NbtOps.INSTANCE, warp).result().ifPresent(warpsList::add);
      }
      tag.put("warps", warpsList);
   }
   
   public Set<Destination> getWarps(){
      return warps;
   }
   
   public boolean addWarp(Destination warp){
      if(warps.contains(warp)) return false;
      return warps.add(warp);
   }
   
   public boolean removeWarp(Destination warp){
      return warps.remove(warp);
   }
   
   public static void onServerStopping(MinecraftServer server){
      WarpsStorage storage = DataAccess.getGlobal(KEY);
      LOGGER.info("Saving {} warps on server shutdown", storage.getWarps().size());
   }
}
