package net.borisshoes.endernexus;

import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.timers.GenericTimer;
import net.borisshoes.borislib.timers.TickTimerCallback;
import net.borisshoes.borislib.utils.ParticleEffectUtils;
import net.borisshoes.borislib.utils.SoundUtils;
import net.borisshoes.borislib.utils.TextUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import static net.borisshoes.endernexus.EnderNexus.CONFIG;
import static net.borisshoes.endernexus.EnderNexus.RECENT_TELEPORTS;

public class TeleportTimer extends TickTimerCallback {
   
   public final ServerPlayer player;
   public final EnderNexus.TPType type;
   public final TeleportTransition tpTarget;
   public final long startTime;
   public final CustomBossEvent bossBar;
   public final Vec3 lastPos;
   
   public TeleportTimer(EnderNexus.TPType type, ServerPlayer player, TeleportTransition tpTarget, long startTime, Vec3 lastPos, @Nullable CustomBossEvent bossbar){
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
      long newStart = lastPos.distanceTo(player.position()) < 0.1 ?  startTime : System.currentTimeMillis();
      double seconds = EnderNexus.readConfigWarmup(type);
      double timeDiff = (System.currentTimeMillis()-newStart) / 1000.0;
      if(timeDiff >= seconds){
         player.connection.send(new ClientboundClearTitlesPacket(true));
         if (bossBar != null) {
            bossBar.removePlayer(player);
            player.level().getServer().getCustomBossEvents().remove(bossBar);
         } else {
            player.displayClientMessage(Component.translatable("text.endernexus.teleporting").withStyle(ChatFormatting.LIGHT_PURPLE), true);
         }
         player.teleport(tpTarget);
         RECENT_TELEPORTS.add(new EnderNexus.Teleport(player,type,System.currentTimeMillis()));
         if(CONFIG.getBoolean(EnderNexusRegistry.PARTICLES_ENABLED)) teleportParticles(tpTarget.newLevel(),tpTarget.position(),0);
         if(CONFIG.getBoolean(EnderNexusRegistry.SOUND_ENABLED)) SoundUtils.playSound(tpTarget.newLevel(), BlockPos.containing(tpTarget.position()), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,0.5f,0.8f + player.getRandom().nextFloat()*0.4f);
      }else{
         if(player.level().getServer().getPlayerList().getPlayer(player.getUUID()) == null){
            if(bossBar != null) player.level().getServer().getCustomBossEvents().remove(bossBar);
            return;
         }
         if(player.level().getServer().getTickCount() % 5 == 0){
            if(CONFIG.getBoolean(EnderNexusRegistry.PARTICLES_ENABLED)){
               player.level().sendParticles(ParticleTypes.PORTAL,player.position().x,player.position().y+.5,player.position().z,20,.2,.5,.2,1);
            }
            
            if(CONFIG.getBoolean(EnderNexusRegistry.SOUND_ENABLED)){
               float pitch = (float) ((timeDiff / seconds) * 1.5f + 0.5f);
               SoundUtils.playSongToPlayer(player, SoundEvents.NOTE_BLOCK_BASEDRUM, 0.8f, pitch);
            }
            
            if (bossBar == null) {
               player.displayClientMessage(Component.translatable("text.endernexus.stand_still_for",
                     Component.literal(TextUtils.readableInt((int) (seconds-timeDiff))).withStyle(ChatFormatting.GREEN)
               ).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("text.endernexus.stand_still").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC)));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("text.endernexus.teleporting").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)));
         }
         
         if (bossBar != null && player.level().getServer().getTickCount() % 3 == 0) {
            bossBar.setProgress(1.0f - (float) (timeDiff / seconds));
            bossBar.setName(Component.translatable("text.endernexus.charging_teleport",TextUtils.readableInt((int)(seconds-timeDiff))).withStyle(ChatFormatting.LIGHT_PURPLE));
         }
         
         BorisLib.addTickTimerCallback(new TeleportTimer(type,player,tpTarget,newStart,player.position(),bossBar));
      }
   }
   
   public static TeleportTimer startTeleport(EnderNexus.TPType type, ServerPlayer player, TeleportTransition tpTarget){
      MinecraftServer server = player.level().getServer();
      CustomBossEvent standStillBar = null;
      long start = System.currentTimeMillis();
      if(CONFIG.getBoolean(EnderNexusRegistry.BOSSBAR_ENABLED)){
         int seconds = (int)EnderNexus.readConfigWarmup(type);
         standStillBar  = server.getCustomBossEvents().create(
               Identifier.parse("standstill-" + player.getStringUUID()+"-"+type.label),
               Component.translatable("text.endernexus.charging_teleport",TextUtils.readableInt(seconds)).withStyle(ChatFormatting.LIGHT_PURPLE)
         );
         standStillBar.addPlayer(player);
         standStillBar.setColor(BossEvent.BossBarColor.GREEN);
         player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 10, 5));
      }
      return new TeleportTimer(type,player,tpTarget,start,player.position(),standStillBar);
   }
   
   private static void teleportParticles(ServerLevel world, Vec3 pos, int tick){
      int animLength = 20;
      if(tick < 5) world.sendParticles(ParticleTypes.REVERSE_PORTAL,pos.x,pos.y+.5,pos.z,30,.1,.4,.1,0.2);
      if(tick % 3 == 0) ParticleEffectUtils.circle(world,null,pos.subtract(0,0.5,0), ParticleTypes.WITCH,1,20,1,0.1,0);
      if(tick < animLength){
         BorisLib.addTickTimerCallback(world, new GenericTimer(1, () -> teleportParticles(world,pos,tick+1)));
      }
   }
}
