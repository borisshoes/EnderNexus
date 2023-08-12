package net.borisshoes.endernexus.utils;

import java.util.TimerTask;

public class GenericTimer {
   protected int timer;
   protected TimerTask onTimer;
   public final boolean autoRemove;
   protected boolean trash;
   
   public GenericTimer(int time, TimerTask onTimer){
      this.timer = time;
      this.onTimer = onTimer;
      this.autoRemove = true;
      this.trash = false;
   }
   
   public GenericTimer(int time, TimerTask onTimer, boolean autoRemove){
      this.timer = time;
      this.onTimer = onTimer;
      this.autoRemove = false;
      this.trash = false;
   }
   
   public void setTrash(boolean trash){
      this.trash = trash;
   }
   
   public boolean isTrash(){
      return trash;
   }
   
   public int getTimer(){
      return timer;
   }
   
   public int decreaseTimer(){
      return this.timer--;
   }
   
   public void setTimer(int timer){
      this.timer = timer;
   }
   
   public void onTimer(){
      if(!trash){
         onTimer.run();
      }
   }
}
