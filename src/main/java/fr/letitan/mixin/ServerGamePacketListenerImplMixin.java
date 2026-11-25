package fr.letitan.mixin;

import fr.letitan.vie.Accroche;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Un joueur agrippé au Titan ne flotte pas : le serveur ne l'expulse pas pour vol. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@Inject(method = "noBlocksAround", at = @At("HEAD"), cancellable = true)
	private void letitan$agrippe(Entity entite, CallbackInfoReturnable<Boolean> cir) {
		if (entite instanceof ServerPlayer joueur && Accroche.agrippe(joueur)) {
			cir.setReturnValue(false);
		}
	}
}
