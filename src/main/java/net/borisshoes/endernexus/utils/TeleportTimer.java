package net.borisshoes.endernexus.utils;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.TimerTask;

public class TeleportTimer extends GenericTimer{
   
   public final ServerPlayerEntity player;
   
   public TeleportTimer(int time, TimerTask onTimer, ServerPlayerEntity player){
      super(time, onTimer);
      this.player = player;
   }
   
   public TeleportTimer(int time, TimerTask onTimer, boolean autoRemove, ServerPlayerEntity player){
      super(time, onTimer, autoRemove);
      this.player = player;
   }
   
   
}
