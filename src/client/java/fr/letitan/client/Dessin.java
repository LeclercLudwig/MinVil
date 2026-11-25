package fr.letitan.client;

import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import fr.letitan.LeTitan;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Vector3fc;

/**
 * Outils communs pour dessiner de grandes formes en couleurs unies (le sol lointain, le Titan
 * vu du sol) : un rendu opaque, sans texture, et le voile de l'air qui pâlit ce qui est loin.
 */
final class Dessin {
	/** Opaque, avec profondeur, couleurs par sommet. */
	static final RenderPipeline PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(LeTitan.id("pipeline/formes"))
					.withColorTargetState(ColorTargetState.DEFAULT)
					.withDepthStencilState(DepthStencilState.DEFAULT)
					.withCull(false)
					.build());
	static final RenderType FORMES = RenderType.create("letitan_formes", RenderSetup.builder(PIPELINE).createRenderSetup());

	/** Lumière venue d'en haut, un peu de côté (normalisée). */
	private static final float LX = 0.35f, LY = 0.86f, LZ = 0.37f;

	private Dessin() {
	}

	/** Éclairage d'une face selon sa normale (pas forcément normalisée). */
	static float eclairage(float nx, float ny, float nz) {
		float n = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (n == 0) {
			return 1;
		}
		float d = (nx * LX + ny * LY + nz * LZ) / n;
		return 0.55f + 0.45f * Math.max(0, d);
	}

	/**
	 * Couleur finale : la couleur de base, éclairée, assombrie la nuit, puis voilée vers la
	 * couleur du ciel avec la distance.
	 */
	static int couleur(int rgb, float eclairage, float jour, float distance, Vector3fc ciel, float portee) {
		float r = ((rgb >> 16) & 255) / 255f * eclairage * jour;
		float g = ((rgb >> 8) & 255) / 255f * eclairage * jour;
		float b = (rgb & 255) / 255f * eclairage * jour;
		// Presque rien de près, puis l'air épaissit : le bord du sol dessiné se fond dans le ciel.
		float d = distance / portee;
		float voile = 1 - (float) Math.exp(-d * d);
		r += (ciel.x() - r) * voile;
		g += (ciel.y() - g) * voile;
		b += (ciel.z() - b) * voile;
		return 0xFF000000 | (canal(r) << 16) | (canal(g) << 8) | canal(b);
	}

	private static int canal(float v) {
		return Math.max(0, Math.min(255, (int) (v * 255)));
	}

	static int fois(int rgb, float f) {
		return (canal(((rgb >> 16) & 255) / 255f * f) << 16) | (canal(((rgb >> 8) & 255) / 255f * f) << 8)
				| canal((rgb & 255) / 255f * f);
	}

	static int melange(int a, int b, float t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return (r << 16) | (g << 8) | bl;
	}
}
