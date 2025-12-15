package net.borisshoes.endernexus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

public class Destination {
   
   public static final Codec<Destination> CODEC = RecordCodecBuilder.create(instance -> instance.group(
         Codec.STRING.fieldOf("name").forGetter(Destination::getName),
         Vec3.CODEC.fieldOf("pos").forGetter(Destination::getPos),
         Vec2.CODEC.optionalFieldOf("rot", new Vec2(0, 0)).forGetter(Destination::getRotation),
         Codec.STRING.fieldOf("world").forGetter(Destination::getWorldKey)
   ).apply(instance, Destination::new));
   
   private final String name;
   private final Vec3 pos;
   private final Vec2 rot;
   private final String worldKey;
   
   public Destination(String name, Vec3 pos, @Nullable Vec2 rot, String world){
      this.name = name;
      this.pos = pos;
      this.worldKey = world;
   
      this.rot = Objects.requireNonNullElseGet(rot, () -> new Vec2(0, 0));
   }
   
   public Vec3 getPos(){
      return pos;
   }
   
   public Vec2 getRotation(){
      return rot;
   }
   
   public String getWorldKey(){
      return worldKey;
   }
   
   public String getName(){
      return name;
   }
   
   public ServerLevel getWorld(MinecraftServer server){
      for (ServerLevel w : server.getAllLevels()){
         if(w.dimension().identifier().toString().equals(worldKey)){
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
