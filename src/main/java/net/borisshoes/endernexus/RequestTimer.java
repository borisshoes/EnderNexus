package net.borisshoes.endernexus;

import net.borisshoes.borislib.timers.GenericTimer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Objects;
import java.util.TimerTask;
import java.util.UUID;

import static net.borisshoes.borislib.BorisLib.SERVER_TIMER_CALLBACKS;

public class RequestTimer extends GenericTimer{
   private ServerPlayerEntity tFrom;
   private ServerPlayerEntity tTo;
   private final boolean tpahere;
   private final UUID timerId;
   
   public RequestTimer(ServerPlayerEntity tFrom, ServerPlayerEntity tTo, boolean tpahere) {
      super(
            (int)EnderNexus.CONFIG.getDouble(EnderNexusRegistry.TPA_TIMEOUT) * 20,
            new TimerTask() {
               @Override
               public void run(){
                  Text str = tpahere ? Text.translatable("text.endernexus.tpahere") : Text.translatable("text.endernexus.tpa");
                  tFrom.sendMessage(Text.translatable("text.endernexus.your_tpa_timeout",str,tTo.getName().getString()).formatted(Formatting.RED), false);
                  tTo.sendMessage(Text.translatable("text.endernexus.their_tpa_timeout",str,tFrom.getName().getString()).formatted(Formatting.RED), false);
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
   
   public ServerPlayerEntity tTo(){
      return tTo;
   }
   
   public ServerPlayerEntity tFrom(){
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
      this.tFrom = tFrom.getEntityWorld().getServer().getPlayerManager().getPlayer(tFrom.getUuid());
      this.tTo = tTo.getEntityWorld().getServer().getPlayerManager().getPlayer(tTo.getUuid());
      assert tFrom != null && tTo != null;
   }
}