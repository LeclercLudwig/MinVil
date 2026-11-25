package fr.letitan.client;

import fr.letitan.LeTitan;
import fr.letitan.sol.SolPayload;
import fr.letitan.vie.Accroche;
import fr.letitan.voyage.Repere;
import fr.letitan.voyage.EtatTitanPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/**
 * Côté client : reçoit l'état du voyage et la carte du sol, dessine le vrai sol sous le Titan
 * quand on est sur son dos, et le Titan qui marche quand on est dans le monde d'en bas.
 */
public final class LeTitanClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Pour savoir où sont les pattes du Titan quand on y grimpe.
		Accroche.client = new Accroche.Source() {
			@Override
			public Repere repere(Level niveau, double temps) {
				return EtatClient.repere(temps);
			}

			@Override
			public double veille(Level niveau) {
				return EtatClient.veille();
			}

			@Override
			public double marche(Level niveau) {
				return EtatClient.marcheLissee();
			}
		};
		ClientPlayNetworking.registerGlobalReceiver(EtatTitanPayload.TYPE, (etat, contexte) -> EtatClient.recevoir(etat));
		ClientPlayNetworking.registerGlobalReceiver(SolPayload.TYPE, (carte, contexte) -> SolClient.recevoir(carte));
		ClientPlayConnectionEvents.DISCONNECT.register((reseau, client) -> {
			EtatClient.oublier();
			SolClient.oublier();
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level != null) {
				EtatClient.tick(client.level);
			}
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(contexte -> {
			var niveau = Minecraft.getInstance().level;
			if (niveau == null || !EtatClient.connu()) {
				return;
			}
			if (niveau.dimension() == LeTitan.DOS) {
				RenduSol.dessiner(contexte, niveau);
			} else if (niveau.dimension() == Level.OVERWORLD) {
				RenduTitan.dessiner(contexte, niveau);
			}
		});
	}
}
