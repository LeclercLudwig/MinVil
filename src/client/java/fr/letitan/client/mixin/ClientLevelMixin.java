package fr.letitan.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.attribute.AmbientParticle;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Le monde d'en bas après la fin du monde : un ciel couleur de cendre, une brume épaisse, des
 * nuages bas, sombres et denses, une lumière du jour blafarde, pas d'étoiles, de la cendre
 * qui tombe, une végétation ternie, et plus de musique : c'est le Silence. Le dos du Titan,
 * lui, garde son ciel.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	@Unique
	private static final Vector3fc CENDRE = new Vector3f(0.16f, 0.16f, 0.18f), BRUME = new Vector3f(0.13f, 0.13f, 0.14f);

	@Inject(method = "addEnvironmentAttributeLayers", at = @At("RETURN"))
	private void letitan$finDuMonde(EnvironmentAttributeSystem.Builder couches,
			CallbackInfoReturnable<EnvironmentAttributeSystem.Builder> cir) {
		if (((ClientLevel) (Object) this).dimension() != Level.OVERWORLD) {
			return;
		}
		couches.addTimeBasedLayer(EnvironmentAttributes.SKY_COLOR, (ciel, tick) -> letitan$vers(ciel, CENDRE, 0.78f));
		couches.addTimeBasedLayer(EnvironmentAttributes.FOG_COLOR, (brume, tick) -> letitan$vers(brume, BRUME, 0.75f));
		couches.addTimeBasedLayer(EnvironmentAttributes.SUNRISE_SUNSET_COLOR,
				(aube, tick) -> new Vector4f(aube.x() * 0.5f, aube.y() * 0.35f, aube.z() * 0.3f, aube.w() * 0.5f));
		// Des nuages de suie, épais et bas.
		couches.addTimeBasedLayer(EnvironmentAttributes.CLOUD_COLOR, (nuage, tick) -> new Vector4f(0.11f, 0.11f, 0.12f, 0.97f));
		couches.addTimeBasedLayer(EnvironmentAttributes.CLOUD_HEIGHT, (hauteur, tick) -> 150.33f);
		// Le jour peine à percer.
		couches.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_COLOR, (lumiere, tick) -> letitan$vers(lumiere, new Vector3f(0.62f, 0.6f, 0.58f), 0.5f));
		couches.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_FACTOR, (facteur, tick) -> facteur * 0.62f);
		couches.addTimeBasedLayer(EnvironmentAttributes.STAR_BRIGHTNESS, (etoiles, tick) -> 0f);
		couches.addTimeBasedLayer(EnvironmentAttributes.FOG_END_DISTANCE, (fin, tick) -> Math.min(fin, 170f));
		couches.addTimeBasedLayer(EnvironmentAttributes.SKY_FOG_END_DISTANCE, (fin, tick) -> Math.min(fin, 220f));
		// La cendre de ce qui a brûlé tombe encore.
		couches.addTimeBasedLayer(EnvironmentAttributes.AMBIENT_PARTICLES, (particules, tick) -> {
			List<AmbientParticle> avecCendre = new ArrayList<>(particules);
			avecCendre.add(new AmbientParticle(ParticleTypes.WHITE_ASH, 0.004f));
			avecCendre.add(new AmbientParticle(ParticleTypes.ASH, 0.003f));
			return avecCendre;
		});
		// Plus de musique : le Silence.
		couches.addTimeBasedLayer(EnvironmentAttributes.MUSIC_VOLUME, (volume, tick) -> 0f);
	}

	/** L'herbe et les feuilles, ternies et jaunies : une végétation qui survit à peine. */
	@Inject(method = "calculateBlockTint", at = @At("RETURN"), cancellable = true)
	private void letitan$vegetationTerne(BlockPos pos, ColorResolver resolveur, CallbackInfoReturnable<Integer> cir) {
		if (((ClientLevel) (Object) this).dimension() != Level.OVERWORLD || resolveur == BiomeColors.WATER_COLOR_RESOLVER) {
			return;
		}
		int c = cir.getReturnValueI();
		float r = ((c >> 16) & 255), g = ((c >> 8) & 255), b = (c & 255);
		// Vers un brun-gris d'herbe sèche, à 55 %.
		float t = 0.55f;
		int nr = (int) (r + (112 - r) * t), ng = (int) (g + (104 - g) * t), nb = (int) (b + (78 - b) * t);
		cir.setReturnValue((c & 0xFF000000) | (nr << 16) | (ng << 8) | nb);
	}

	@Unique
	private static Vector3fc letitan$vers(Vector3fc depuis, Vector3fc vers, float part) {
		return new Vector3f(depuis).lerp(vers, part);
	}
}
