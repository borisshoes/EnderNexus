package net.borisshoes.endernexus;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class Destination {
   
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
      EnderNexus.LOGGER.error("Unknown world teleport: "+worldKey);
      return null;
   }
}
