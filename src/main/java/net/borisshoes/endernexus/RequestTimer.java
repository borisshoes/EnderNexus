package net.borisshoes.endernexus;

import net.borisshoes.borislib.timers.GenericTimer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.TimerTask;
import java.util.UUID;

import static net.borisshoes.borislib.BorisLib.SERVER_TIMER_CALLBACKS;

public class RequestTimer extends GenericTimer{
   private ServerPlayer tFrom;
   private ServerPlayer tTo;
   private final boolean tpahere;
   private final UUID timerId;
   
   public RequestTimer(ServerPlayer tFrom, ServerPlayer tTo, boolean tpahere) {
      super(
            (int)EnderNexus.CONFIG.getDouble(EnderNexusRegistry.TPA_TIMEOUT) * 20,
            new TimerTask() {
               @Override
               public void run(){
                  Component str = tpahere ? Component.translatable("text.endernexus.tpahere") : Component.translatable("text.endernexus.tpa");
                  tFrom.displayClientMessage(Component.translatable("text.endernexus.your_tpa_timeout",str,tTo.getName().getString()).withStyle(ChatFormatting.RED), false);
                  tTo.displayClientMessage(Component.translatable("text.endernexus.their_tpa_timeout",str,tFrom.getName().getString()).withStyle(ChatFormatting.RED), false);
               }
            });
      this.tFrom = tFrom;
      this.tTo = tTo;
      this.tpahere = tpahere;
      this.timerId = UUID.randomUUID();
   }
   
   public boolean isTPAhere(){
      return tpahere;
   }
   
   void cancelTimeout() {
      SERVER_TIMER_CALLBACKS.removeIf(timer -> (timer instanceof RequestTimer rt) && rt.timerId.equals(this.timerId));
   }
   
   public ServerPlayer tTo(){
      return tTo;
   }
   
   public ServerPlayer tFrom(){
      return tFrom;
   }
   
   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      RequestTimer that = (RequestTimer) o;
      return this.tFrom.equals(that.tFrom) && this.tTo.equals(that.tTo);
   }
   
   @Override
   public int hashCode() {
      return Objects.hash(tFrom, tTo);
   }
   
   @Override
   public String toString() {
      return "TPARequest{" + "tFrom=" + tFrom +
            ", tTo=" + tTo +
            '}';
   }
   
   public void refreshPlayers() {
      this.tFrom = tFrom.level().getServer().getPlayerList().getPlayer(tFrom.getUUID());
      this.tTo = tTo.level().getServer().getPlayerList().getPlayer(tTo.getUUID());
      assert tFrom != null && tTo != null;
   }
}