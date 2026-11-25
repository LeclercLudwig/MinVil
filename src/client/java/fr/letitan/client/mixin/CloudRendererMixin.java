package fr.letitan.client.mixin;

import fr.letitan.LeTitan;
import fr.letitan.client.EtatClient;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sur le Titan, les nuages (qui passent sous lui) défilent vers l'arrière à la vitesse
 * de sa marche : on voit qu'il avance. On décale la position de la caméra utilisée
 * seulement pour placer les nuages, de la distance parcourue.
 */
@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {
	/** Il y a plusieurs méthodes « prepare » : on vise celle qui place les nuages. */
	@Unique
	private static final String PREPARE = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V";

	@Unique
	private float letitan$fraction;

	@Inject(method = PREPARE, at = @At("HEAD"))
	private void letitan$retenirFraction(int color, CloudStatus status, float bottomY, int range, Vec3 camera,
			long gameTime, float partialTicks, CallbackInfo ci) {
		letitan$fraction = partialTicks;
	}

	@ModifyVariable(method = PREPARE, at = @At("HEAD"), argsOnly = true)
	private Vec3 letitan$defilement(Vec3 camera) {
		var niveau = Minecraft.getInstance().level;
		if (niveau == null || niveau.dimension() != LeTitan.DOS) {
			return camera;
		}
		// Le Titan regarde vers le nord : le paysage (et les nuages) défile vers le sud.
		return camera.add(0, 0, -EtatClient.odometre(niveau.getGameTime() + letitan$fraction));
	}
}
