package net.borisshoes.endernexus.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.borisshoes.borislib.datastorage.DataKey;
import net.borisshoes.borislib.datastorage.DataRegistry;
import net.borisshoes.borislib.utils.CodecUtils;
import net.borisshoes.endernexus.Destination;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.*;

import static net.borisshoes.endernexus.EnderNexus.MOD_ID;

public class HomesStorage {
   public static final Codec<HomesStorage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
         CodecUtils.UUID_CODEC.fieldOf("player_id").forGetter(storage -> storage.playerID),
         Destination.CODEC.listOf().fieldOf("homes").forGetter(storage -> new ArrayList<>(storage.getHomes()))
   ).apply(instance, (uuid, homes) -> {
      HomesStorage storage = new HomesStorage(uuid);
      storage.homes.addAll(homes);
      return storage;
   }));
   
   public static final DataKey<HomesStorage> KEY = DataRegistry.register(DataKey.ofPlayer(Identifier.of(MOD_ID, "timestamp"),CODEC,HomesStorage::new));
   
   public final Set<Destination> homes = new HashSet<>();
   public final UUID playerID;
   
   public HomesStorage(UUID playerID){
      this.playerID = playerID;
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
