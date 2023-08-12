package net.borisshoes.endernexus.mixins;

import net.borisshoes.endernexus.EnderNexus;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {
   
   @Inject(method="onDeath",at=@At("TAIL"))
   private void endernexus_onPlayerDeath(DamageSource damageSource, CallbackInfo ci){
      ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
      EnderNexus.playerDied(player);
   }
}
