package fr.letitan.client.mixin;

import fr.letitan.LeTitan;
import fr.letitan.client.EtatClient;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Les nuages du Titan.
 *
 * Sur son dos, les nuages (qui passent sous lui) défilent vers l'arrière à la vitesse de sa
 * marche : on voit qu'il avance. Dans le monde d'en bas, ils forment une couverture épaisse,
 * presque continue : les nuages ordinaires, épaissis.
 */
@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {
	/** Il y a plusieurs méthodes « prepare » : celle qui place les nuages, et celle qui lit leur texture. */
	@Unique
	private static final String PREPARE = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V";
	@Unique
	private static final String LECTURE = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Ljava/util/Optional;";
	/** Épaississement des nuages d'en bas, en cases de nuage. */
	@Unique
	private static final int EPAISSISSEMENT = 2;

	@Shadow
	private CloudRenderer.@Nullable TextureData texture;
	@Shadow
	private boolean needsRebuild;

	@Unique
	private float letitan$fraction;
	@Unique
	private CloudRenderer.@Nullable TextureData letitan$ordinaires, letitan$denses;

	@Inject(method = LECTURE, at = @At("RETURN"))
	private void letitan$epaissir(ResourceManager ressources, ProfilerFiller profileur,
			CallbackInfoReturnable<Optional<CloudRenderer.TextureData>> cir) {
		letitan$ordinaires = cir.getReturnValue().orElse(null);
		letitan$denses = letitan$ordinaires == null ? null : letitan$epaissis(letitan$ordinaires);
	}

	@Inject(method = PREPARE, at = @At("HEAD"))
	private void letitan$retenirFraction(int color, CloudStatus status, float bottomY, int range, Vec3 camera,
			long gameTime, float partialTicks, CallbackInfo ci) {
		letitan$fraction = partialTicks;
		var niveau = Minecraft.getInstance().level;
		CloudRenderer.TextureData voulue = niveau != null && niveau.dimension() == Level.OVERWORLD ? letitan$denses : letitan$ordinaires;
		if (voulue != null && texture != voulue && texture != null) {
			texture = voulue;
			needsRebuild = true;
		}
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

	/** Les mêmes nuages, élargis de quelques cases dans tous les sens : une couverture presque continue. */
	@Unique
	private static CloudRenderer.TextureData letitan$epaissis(CloudRenderer.TextureData source) {
		int l = source.width(), h = source.height();
		boolean[] plein = new boolean[l * h];
		for (int i = 0; i < plein.length; i++) {
			plein[i] = source.cells()[i] != 0;
		}
		for (int passe = 0; passe < EPAISSISSEMENT; passe++) {
			boolean[] suivant = plein.clone();
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < l; x++) {
					if (plein[x + y * l]) {
						continue;
					}
					int voisins = 0;
					for (int dy = -1; dy <= 1; dy++) {
						for (int dx = -1; dx <= 1; dx++) {
							if (plein[Math.floorMod(x + dx, l) + Math.floorMod(y + dy, h) * l]) {
								voisins++;
							}
						}
					}
					suivant[x + y * l] = voisins >= 2;
				}
			}
			plein = suivant;
		}
		long[] cellules = new long[l * h];
		int blanc = 0xFFF0F0F0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < l; x++) {
				if (!plein[x + y * l]) {
					continue;
				}
				boolean nord = !plein[x + Math.floorMod(y - 1, h) * l];
				boolean est = !plein[Math.floorMod(x + 1, l) + y * l];
				boolean sud = !plein[x + Math.floorMod(y + 1, h) * l];
				boolean ouest = !plein[Math.floorMod(x - 1, l) + y * l];
				cellules[x + y * l] = (long) blanc << 4 | (nord ? 1 : 0) << 3 | (est ? 1 : 0) << 2 | (sud ? 1 : 0) << 1 | (ouest ? 1 : 0);
			}
		}
		return new CloudRenderer.TextureData(cellules, l, h);
	}
}
