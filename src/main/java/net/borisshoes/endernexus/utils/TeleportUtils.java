package net.borisshoes.endernexus.utils;

import net.borisshoes.endernexus.EnderNexus;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.CommandBossBar;
import net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralTextContent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import static net.borisshoes.endernexus.EnderNexus.SERVER_TIMER_CALLBACKS;
import static net.borisshoes.endernexus.EnderNexus.SERVER_TIMER_CALLBACKS_QUEUE;

public class TeleportUtils {
   public static void genericTeleport(boolean bossBar, boolean particles, boolean sound, int standStillTime, ServerPlayerEntity who, Runnable onCounterDone) {
      MinecraftServer server = who.server;
      final int[] counter = {standStillTime};
      final Vec3d[] lastPos = {who.getPos()};
      CommandBossBar standStillBar = null;
      if (bossBar) {
         standStillBar = server.getBossBarManager().add(new Identifier("standstill-" + who.getUuidAsString()+"-"+ UUID.randomUUID()), Text.literal("Teleport Charging: "+((counter[0]/20)+1)+" Seconds Remaining").formatted(Formatting.LIGHT_PURPLE));
         standStillBar.addPlayer(who);
         standStillBar.setColor(BossBar.Color.GREEN);
      }
      who.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 10, 5));
      CommandBossBar finalStandStillBar = standStillBar;
      
      final ServerPlayerEntity[] whoFinal = {who};
      UUID timerId = UUID.randomUUID();
      TeleportTimer timer = new TeleportTimer(4,new TimerTask() {
         @Override
         public void run() {
            if (counter[0] == 0) {
               if (bossBar) {
                  finalStandStillBar.removePlayer(whoFinal[0]);
                  server.getBossBarManager().remove(finalStandStillBar);
               } else {
                  whoFinal[0].sendMessage(MutableText.of(new LiteralTextContent("Teleporting!")).formatted(Formatting.LIGHT_PURPLE), true);
               }
   
               SERVER_TIMER_CALLBACKS_QUEUE.put(UUID.randomUUID(), new GenericTimer(10, new TimerTask() {
                  @Override
                  public void run() {
                     whoFinal[0].networkHandler.sendPacket(new ClearTitleS2CPacket(true));
                  }
               }));
   
               if(SERVER_TIMER_CALLBACKS.containsKey(timerId)){
                  SERVER_TIMER_CALLBACKS.get(timerId).setTrash(true);
               }
               server.submit(onCounterDone);
               return;
            }
      
            Vec3d curPos = whoFinal[0].getPos();
            if (whoFinal[0].isRemoved()) {
               whoFinal[0] = server.getPlayerManager().getPlayer(whoFinal[0].getUuid());
               assert whoFinal[0] != null;
            } else if (lastPos[0].equals(curPos)) {
               counter[0] -= 4;
            } else {
               lastPos[0] = curPos;
               counter[0] = standStillTime;
            }
            
            if(particles){
               who.getServerWorld().spawnParticles(ParticleTypes.PORTAL,who.getPos().x,who.getPos().y+.5,who.getPos().z,20,.2,.5,.2,1);
            }
            
            if(sound){
               float pitch = ((standStillTime-counter[0]) / (float)standStillTime) * 1.5f + 0.5f;
               SoundUtils.playSongToPlayer(who, SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM, 0.8f, pitch);
            }
      
            if (bossBar) {
               finalStandStillBar.setPercent((counter[0] / (float)standStillTime));
               finalStandStillBar.setName(Text.literal("Teleport Charging: "+((counter[0]/20)+1)+" Seconds Remaining").formatted(Formatting.LIGHT_PURPLE));
            } else {
               whoFinal[0].sendMessage(MutableText.of(new LiteralTextContent("Stand still for ")).formatted(Formatting.LIGHT_PURPLE)
                     .append(MutableText.of(new LiteralTextContent(Integer.toString(counter[0]/20))).formatted(Formatting.GREEN))
                     .append(MutableText.of(new LiteralTextContent(" more seconds!")).formatted(Formatting.LIGHT_PURPLE)), true);
            }
   
            if(whoFinal[0] == null){
               SERVER_TIMER_CALLBACKS.get(timerId).setTrash(true);
               //finalStandStillBar.removePlayer(whoFinal[0]);
               server.getBossBarManager().remove(finalStandStillBar);
               return;
            }
            whoFinal[0].networkHandler.sendPacket(new SubtitleS2CPacket(MutableText.of(new LiteralTextContent("Please stand still..."))
                  .formatted(Formatting.GREEN, Formatting.ITALIC)));
            whoFinal[0].networkHandler.sendPacket(new TitleS2CPacket(MutableText.of(new LiteralTextContent("Teleporting!"))
                  .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)));
   
            if(SERVER_TIMER_CALLBACKS.containsKey(timerId)){
               SERVER_TIMER_CALLBACKS.get(timerId).setTimer(4);
            }
         }
      },false,who);
      SERVER_TIMER_CALLBACKS.put(timerId,timer);
   }
}
