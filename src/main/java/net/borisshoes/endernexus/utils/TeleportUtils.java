package net.borisshoes.endernexus.utils;

import net.borisshoes.endernexus.EnderNexus;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.CommandBossBar;
import net.minecraft.fluid.FluidState;
import net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;

import java.util.ArrayList;
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
         standStillBar = server.getBossBarManager().add(Identifier.of("standstill-" + who.getUuidAsString()+"-"+ UUID.randomUUID()), Text.literal("Teleport Charging: "+((counter[0]/20)+1)+" Seconds Remaining").formatted(Formatting.LIGHT_PURPLE));
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
                  whoFinal[0].sendMessage(Text.literal("Teleporting!").formatted(Formatting.LIGHT_PURPLE), true);
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
               whoFinal[0].sendMessage(Text.literal("Stand still for ").formatted(Formatting.LIGHT_PURPLE)
                     .append(Text.literal(Integer.toString(counter[0]/20)).formatted(Formatting.GREEN))
                     .append(Text.literal(" more seconds!").formatted(Formatting.LIGHT_PURPLE)), true);
            }
   
            if(whoFinal[0] == null){
               SERVER_TIMER_CALLBACKS.get(timerId).setTrash(true);
               //finalStandStillBar.removePlayer(whoFinal[0]);
               server.getBossBarManager().remove(finalStandStillBar);
               return;
            }
            whoFinal[0].networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("Please stand still...").formatted(Formatting.GREEN, Formatting.ITALIC)));
            whoFinal[0].networkHandler.sendPacket(new TitleS2CPacket(Text.literal("Teleporting!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)));
   
            if(SERVER_TIMER_CALLBACKS.containsKey(timerId)){
               SERVER_TIMER_CALLBACKS.get(timerId).setTimer(4);
            }
         }
      },false,who);
      SERVER_TIMER_CALLBACKS.put(timerId,timer);
   }
   
   
   private static int getY(BlockView blockView, int maxY, Vec3d pos) {
      BlockPos.Mutable mutable = new BlockPos.Mutable(pos.x, maxY + 1, pos.z);
      boolean bl = blockView.getBlockState(mutable).isAir();
      mutable.move(Direction.DOWN);
      boolean bl2 = blockView.getBlockState(mutable).isAir();
      while (mutable.getY() > blockView.getBottomY()) {
         mutable.move(Direction.DOWN);
         boolean bl3 = blockView.getBlockState(mutable).isAir();
         if (!bl3 && bl2 && bl) {
            return mutable.getY() + 1;
         }
         bl = bl2;
         bl2 = bl3;
      }
      return maxY + 1;
   }
   
   public static boolean isSafe(BlockView world, int maxY, Vec3d pos) {
      BlockPos blockPos = BlockPos.ofFloored(pos.x, getY(world, maxY, pos) - 1, pos.z);
      BlockState blockState = world.getBlockState(blockPos);
      FluidState fluidState = world.getFluidState(blockPos);
      boolean invalid = blockState.isOf(Blocks.WITHER_ROSE) || blockState.isOf(Blocks.SWEET_BERRY_BUSH) || blockState.isOf(Blocks.CACTUS) || blockState.isOf(Blocks.POWDER_SNOW) || blockState.isIn(BlockTags.PREVENT_MOB_SPAWNING_INSIDE) || LandPathNodeMaker.isFireDamaging(blockState);
      return blockPos.getY() < maxY && fluidState.isEmpty() && !invalid;
   }
   
   public static ArrayList<BlockPos> makeSpawnLocations(int num, int range, int maxY, ServerWorld world, BlockPos center){
      ArrayList<BlockPos> positions = new ArrayList<>();
      for(int i = 0; i < num; i++){
         int tries = 0;
         int x,z;
         do{
            x = center.getX() + (int) (Math.random() * range * 2 - range);
            z = center.getZ() + (int) (Math.random() * range * 2 - range);
            tries++;
         }while(!isSafe(world,maxY, new Vec3d(x,0,z)) && tries < 10000);
         positions.add(BlockPos.ofFloored(x,getY(world,maxY,new Vec3d(x,0,z)),z));
      }
      return positions;
   }
}
