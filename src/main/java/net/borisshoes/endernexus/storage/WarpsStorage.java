package net.borisshoes.endernexus.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.borisshoes.borislib.callbacks.LoginCallback;
import net.borisshoes.borislib.datastorage.DataKey;
import net.borisshoes.borislib.datastorage.DataRegistry;
import net.borisshoes.borislib.testmod.GlobalTimestamp;
import net.borisshoes.borislib.utils.CodecUtils;
import net.borisshoes.endernexus.Destination;
import net.minecraft.util.Identifier;

import java.util.*;

import static net.borisshoes.endernexus.EnderNexus.MOD_ID;

public class WarpsStorage {
   public static final Codec<WarpsStorage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
         Destination.CODEC.listOf().fieldOf("warps").forGetter(storage -> new ArrayList<>(storage.getWarps()))
   ).apply(instance, warps -> {
      WarpsStorage storage = new WarpsStorage();
      storage.warps.addAll(warps);
      return storage;
   }));
   
   public static final DataKey<WarpsStorage> KEY = DataRegistry.register(DataKey.ofGlobal(Identifier.of(MOD_ID, "timestamp"),CODEC,WarpsStorage::new));
   
   public final Set<Destination> warps = new HashSet<>();
   
   public WarpsStorage(){}
   
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
}
