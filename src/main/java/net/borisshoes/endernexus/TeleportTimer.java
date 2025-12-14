package net.borisshoes.endernexus;

import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.timers.GenericTimer;
import net.borisshoes.borislib.timers.TickTimerCallback;
import net.borisshoes.borislib.utils.ParticleEffectUtils;
import net.borisshoes.borislib.utils.SoundUtils;
import net.borisshoes.borislib.utils.TextUtils;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.CommandBossBar;
import net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import org.jetbrains.annotations.Nullable;

import static net.borisshoes.endernexus.EnderNexus.CONFIG;
import static net.borisshoes.endernexus.EnderNexus.RECENT_TELEPORTS;

public class TeleportTimer extends TickTimerCallback {
   
   public final ServerPlayerEntity player;
   public final EnderNexus.TPType type;
   public final TeleportTarget tpTarget;
   public final long startTime;
   public final CommandBossBar bossBar;
   public final Vec3d lastPos;
   
   public TeleportTimer(EnderNexus.TPType type, ServerPlayerEntity player, TeleportTarget tpTarget, long startTime, Vec3d lastPos, @Nullable CommandBossBar bossbar){
      super(1, null, player);
      this.player = player;
      this.type = type;
      this.tpTarget = tpTarget;
      this.startTime = startTime;
      this.bossBar = bossbar;
      this.lastPos = lastPos;
   }
   
   @Override
   public void onTimer(){
      long newStart = lastPos.distanceTo(player.getEntityPos()) < 0.1 ?  startTime : System.currentTimeMillis();
      double seconds = EnderNexus.readConfigWarmup(type);
      double timeDiff = (System.currentTimeMillis()-newStart) / 1000.0;
      if(timeDiff >= seconds){
         player.networkHandler.sendPacket(new ClearTitleS2CPacket(true));
         if (bossBar != null) {
            bossBar.removePlayer(player);
            player.getEntityWorld().getServer().getBossBarManager().remove(bossBar);
         } else {
            player.sendMessage(Text.translatable("text.endernexus.teleporting").formatted(Formatting.LIGHT_PURPLE), true);
         }
         player.teleportTo(tpTarget);
         RECENT_TELEPORTS.add(new EnderNexus.Teleport(player,type,System.currentTimeMillis()));
         if(CONFIG.getBoolean(EnderNexusRegistry.PARTICLES_ENABLED)) teleportParticles(tpTarget.world(),tpTarget.position(),0);
         if(CONFIG.getBoolean(EnderNexusRegistry.SOUND_ENABLED)) SoundUtils.playSound(tpTarget.world(), BlockPos.ofFloored(tpTarget.position()),SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS,0.5f,0.8f + player.getRandom().nextFloat()*0.4f);
      }else{
         if(player.getEntityWorld().getServer().getPlayerManager().getPlayer(player.getUuid()) == null){
            if(bossBar != null) player.getEntityWorld().getServer().getBossBarManager().remove(bossBar);
            return;
         }
         if(player.getEntityWorld().getServer().getTicks() % 5 == 0){
            if(CONFIG.getBoolean(EnderNexusRegistry.PARTICLES_ENABLED)){
               player.getEntityWorld().spawnParticles(ParticleTypes.PORTAL,player.getEntityPos().x,player.getEntityPos().y+.5,player.getEntityPos().z,20,.2,.5,.2,1);
            }
            
            if(CONFIG.getBoolean(EnderNexusRegistry.SOUND_ENABLED)){
               float pitch = (float) ((timeDiff / seconds) * 1.5f + 0.5f);
               SoundUtils.playSongToPlayer(player, SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM, 0.8f, pitch);
            }
            
            if (bossBar != null) {
               bossBar.setPercent(1.0f - (float) (timeDiff / seconds));
               bossBar.setName(Text.translatable("text.endernexus.charging_teleport",TextUtils.readableInt((int)(seconds-timeDiff))).formatted(Formatting.LIGHT_PURPLE));
            } else {
               
               player.sendMessage(Text.translatable("text.endernexus.stand_still_for",
                     Text.literal(TextUtils.readableInt((int) (seconds-timeDiff))).formatted(Formatting.GREEN)
               ).formatted(Formatting.LIGHT_PURPLE), true);
            }
            
            player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("text.endernexus.stand_still").formatted(Formatting.GREEN, Formatting.ITALIC)));
            player.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("text.endernexus.teleporting").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)));
         }
         
         BorisLib.addTickTimerCallback(new TeleportTimer(type,player,tpTarget,newStart,player.getEntityPos(),bossBar));
      }
   }
   
   public static TeleportTimer startTeleport(EnderNexus.TPType type, ServerPlayerEntity player, TeleportTarget tpTarget){
      MinecraftServer server = player.getEntityWorld().getServer();
      CommandBossBar standStillBar = null;
      long start = System.currentTimeMillis();
      if(CONFIG.getBoolean(EnderNexusRegistry.BOSSBAR_ENABLED)){
         int seconds = (int)EnderNexus.readConfigWarmup(type);
         standStillBar  = server.getBossBarManager().add(
               Identifier.of("standstill-" + player.getUuidAsString()+"-"+type.label),
               Text.translatable("text.endernexus.charging_teleport",TextUtils.readableInt(seconds)).formatted(Formatting.LIGHT_PURPLE)
         );
         standStillBar.addPlayer(player);
         standStillBar.setColor(BossBar.Color.GREEN);
         player.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 10, 5));
      }
      return new TeleportTimer(type,player,tpTarget,start,player.getEntityPos(),standStillBar);
   }
   
   private static void teleportParticles(ServerWorld world, Vec3d pos, int tick){
      int animLength = 20;
      if(tick < 5) world.spawnParticles(ParticleTypes.REVERSE_PORTAL,pos.x,pos.y+.5,pos.z,30,.1,.4,.1,0.2);
      if(tick % 3 == 0) ParticleEffectUtils.circle(world,null,pos.subtract(0,0.5,0),ParticleTypes.WITCH,1,20,1,0.1,0);
      if(tick < animLength){
         BorisLib.addTickTimerCallback(world, new GenericTimer(1, () -> teleportParticles(world,pos,tick+1)));
      }
   }
}
