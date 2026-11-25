package fr.letitan.client.mixin;

import fr.letitan.LeTitan;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sous le Titan, le ciel ne s'assombrit pas en dessous de l'horizon : on y voit le sol
 * du monde d'en bas, dessiné par le mod.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
	@Inject(method = "shouldRenderDarkDisc", at = @At("HEAD"), cancellable = true)
	private void letitan$pasDeDisqueSombre(float partialTicks, ClientLevel niveau, CallbackInfoReturnable<Boolean> cir) {
		if (niveau.dimension() == LeTitan.DOS) {
			cir.setReturnValue(false);
		}
	}
}
