package net.borisshoes.endernexus.mixins;

import net.borisshoes.endernexus.EnderNexus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
   
   @Inject(method= "die",at=@At("TAIL"))
   private void endernexus_onPlayerDeath(DamageSource damageSource, CallbackInfo ci){
      ServerPlayer player = (ServerPlayer) (Object) this;
      EnderNexus.playerDied(player);
   }
}
