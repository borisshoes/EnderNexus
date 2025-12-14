package net.borisshoes.endernexus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

public class Destination {
   
   public static final Codec<Destination> CODEC = RecordCodecBuilder.create(instance -> instance.group(
         Codec.STRING.fieldOf("name").forGetter(Destination::getName),
         Vec3d.CODEC.fieldOf("pos").forGetter(Destination::getPos),
         Vec2f.CODEC.optionalFieldOf("rot", new Vec2f(0, 0)).forGetter(Destination::getRotation),
         Codec.STRING.fieldOf("world").forGetter(Destination::getWorldKey)
   ).apply(instance, Destination::new));
   
   private final String name;
   private final Vec3d pos;
   private final Vec2f rot;
   private final String worldKey;
   
   public Destination(String name, Vec3d pos, @Nullable Vec2f rot, String world){
      this.name = name;
      this.pos = pos;
      this.worldKey = world;
   
      this.rot = Objects.requireNonNullElseGet(rot, () -> new Vec2f(0, 0));
   }
   
   public Vec3d getPos(){
      return pos;
   }
   
   public Vec2f getRotation(){
      return rot;
   }
   
   public String getWorldKey(){
      return worldKey;
   }
   
   public String getName(){
      return name;
   }
   
   public ServerWorld getWorld(MinecraftServer server){
      for (ServerWorld w : server.getWorlds()){
         if(w.getRegistryKey().getValue().toString().equals(worldKey)){
            return w;
         }
      }
      EnderNexus.LOGGER.error("Unknown world teleport: {}",worldKey);
      return null;
   }
   
   @Override
   public boolean equals(Object o){
      if(this == o) return true;
      if(!(o instanceof Destination that)) return false;
      return Objects.equals(name.toLowerCase(Locale.ROOT), that.name.toLowerCase(Locale.ROOT)) && Objects.equals(pos, that.pos) && Objects.equals(rot, that.rot) && Objects.equals(worldKey, that.worldKey);
   }
   
   @Override
   public int hashCode(){
      return Objects.hash(name.toLowerCase(Locale.ROOT), pos, rot, worldKey);
   }
}
