package fr.letitan.client.mixin;

import fr.letitan.LeTitan;
import fr.letitan.Rythme;
import fr.letitan.client.EtatClient;
import fr.letitan.client.EtatMuetClient;
import fr.letitan.monde.Interieur;
import fr.letitan.voyage.Repere;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sur le dos du Titan, la caméra bouge avec lui : secousse à chaque pas et balancement
 * quand il marche, lente respiration quand il dort ; rien quand il est arrêté.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private float xRot;
	@Shadow
	private float yRot;
	@Shadow
	private float depthFar;

	/** Distance d'affichage minimale : le sol jusqu'à l'horizon, ou le Titan au loin. */
	@Unique
	private static final float LOIN = 6000;
	/** Jusqu'à cette distance du Titan, on sent ses pas depuis le monde d'en bas. */
	@Unique
	private static final double PORTEE_SECOUSSE = 450;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void move(float avant, float haut, float droite);

	@Inject(method = "update", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Camera;depthFar:F",
			opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
	private void letitan$voirLoin(DeltaTracker temps, CallbackInfo ci) {
		depthFar = Math.max(depthFar, LOIN);
	}

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void letitan$bougerAvecLeTitan(float partialTicks, CallbackInfo ci) {
		var niveau = Minecraft.getInstance().level;
		if (niveau == null) {
			return;
		}
		double temps = niveau.getGameTime() + partialTicks;
		// Le choc d'une charge du Titan muet : la vue est violemment secouée, où que l'on soit.
		double choc = EtatMuetClient.secousse();
		if (choc > 0) {
			setRotation(yRot + (float) (choc * 6 * Math.sin(temps * 3.7)), xRot + (float) (choc * 5 * Math.cos(temps * 4.3)));
			move(0, (float) (choc * 0.3 * Math.sin(temps * 5.1)), 0);
		}
		if (niveau.dimension() == Level.OVERWORLD) {
			letitan$secousseAuSol(niveau, temps);
			return;
		}
		if (niveau.dimension() != LeTitan.DOS) {
			return;
		}
		letitan$pres_du_coeur(niveau, temps);
		if (Rythme.eveille(niveau)) {
			if (!EtatClient.marche()) {
				return;
			}
			double secousse = Rythme.secousse(temps);
			double balancement = Rythme.balancement(temps);
			// Secousse : la vue tressaute vers le bas, puis oscille en s'amortissant.
			float vertical = (float) (secousse * 1.6 * Math.cos(temps * 2.1));
			float lateral = (float) (secousse * 0.7 * Math.sin(temps * 1.7) + balancement * 0.35);
			setRotation(yRot + lateral, xRot + vertical);
			move(0, (float) (-secousse * 0.12 + balancement * 0.03), 0);
		} else {
			move(0, (float) (Rythme.respiration(temps) * 0.06), 0);
		}
	}

	/** Près du cœur, chaque battement fait trembler la vue. */
	@Unique
	private void letitan$pres_du_coeur(ClientLevel niveau, double temps) {
		var joueur = Minecraft.getInstance().player;
		if (joueur == null) {
			return;
		}
		double[] c = Interieur.COEUR;
		double distance = Math.sqrt(joueur.distanceToSqr(c[0], c[1], c[2]));
		if (distance > 30) {
			return;
		}
		double force = Rythme.battement(temps, Rythme.eveille(niveau)) * (1 - distance / 30);
		setRotation(yRot, xRot + (float) (force * 1.2 * Math.cos(temps * 2.7)));
		move(0, (float) (force * 0.06), 0);
	}

	/** Dans le monde d'en bas, près du Titan qui marche, le sol tremble à chacun de ses pas. */
	@Unique
	private void letitan$secousseAuSol(ClientLevel niveau, double temps) {
		Repere repere = EtatClient.repere(temps);
		var joueur = Minecraft.getInstance().player;
		if (repere == null || joueur == null || !Rythme.eveille(niveau) || !EtatClient.marche()) {
			return;
		}
		double distance = Math.hypot(joueur.getX() - repere.x, joueur.getZ() - repere.z);
		if (distance > PORTEE_SECOUSSE) {
			return;
		}
		double proche = 1 - distance / PORTEE_SECOUSSE;
		double secousse = Rythme.secousse(temps) * proche * proche;
		float vertical = (float) (secousse * 1.4 * Math.cos(temps * 2.3));
		float lateral = (float) (secousse * 0.6 * Math.sin(temps * 1.9));
		setRotation(yRot + lateral, xRot + vertical);
		move(0, (float) (-secousse * 0.15), 0);
	}
}
