package fr.minvil;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Point d'entrée du mod MinVil.
 *
 * Commandes :
 *   /enquete                     aide
 *   /enquete commencer [numéro]  fait apparaître les villageois d'une enquête
 *   /enquete fouiller            fouille le lieu du crime (révèle l'indice)
 *   /enquete carnet              relit les déclarations recueillies
 *   /enquete accuser <nom>       désigne le coupable
 *   /enquete terminer            renvoie les villageois de l'enquête
 *
 * Clic droit sur un villageois de l'enquête : il fait sa déclaration.
 *
 * Prototype : une seule enquête à la fois sur le serveur, gardée en mémoire
 * (elle ne survit pas à un redémarrage). Les villageois portent aussi
 * l'étiquette « minvil_enquete » : /kill @e[tag=minvil_enquete] les retire.
 */
public final class MinVil implements ModInitializer {
	public static final String MOD_ID = "minvil";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Étiquette posée sur tous les villageois d'une enquête. */
	private static final String TAG = "minvil_enquete";
	private static final double RAYON = 4.0;

	private static Enquete enquete;
	/** Villageois apparus pour l'enquête : UUID de l'entité -> id dans l'enquête. */
	private static final Map<UUID, String> villageoisIds = new HashMap<>();
	/** Anti-doublon : le serveur peut recevoir deux interactions pour un même clic. */
	private static final Map<UUID, Long> dernierClic = new HashMap<>();

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, contexte, selection) -> enregistrer(dispatcher));
		UseEntityCallback.EVENT.register((joueur, niveau, main, entite, impact) -> interroger(joueur, niveau.isClientSide(), main, entite));
		// Les joueurs ne peuvent pas frapper les villageois de l'enquête.
		AttackEntityCallback.EVENT.register((joueur, niveau, main, entite, impact) ->
				!niveau.isClientSide() && villageoisIds.containsKey(entite.getUUID())
						? InteractionResult.FAIL : InteractionResult.PASS);
		LOGGER.info("MinVil prêt : {} enquêtes embarquées.", Enquete.NOMBRE);
	}

	// --- Commandes -----------------------------------------------------------

	private static void enregistrer(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("enquete")
				.executes(ctx -> aide(ctx.getSource()))
				.then(Commands.literal("commencer")
						.executes(ctx -> commencer(ctx.getSource(),
								ctx.getSource().getLevel().getRandom().nextInt(Enquete.NOMBRE)))
						.then(Commands.argument("numero", IntegerArgumentType.integer(0, Enquete.NOMBRE - 1))
								.executes(ctx -> commencer(ctx.getSource(),
										IntegerArgumentType.getInteger(ctx, "numero")))))
				.then(Commands.literal("fouiller")
						.executes(ctx -> fouiller(ctx.getSource())))
				.then(Commands.literal("carnet")
						.executes(ctx -> carnet(ctx.getSource())))
				.then(Commands.literal("accuser")
						.then(Commands.argument("nom", StringArgumentType.word())
								.suggests((ctx, suggestions) -> SharedSuggestionProvider.suggest(
										enquete == null ? List.<String>of()
												: enquete.vivants().stream().map(Enquete.Villageois::nom).toList(),
										suggestions))
								.executes(ctx -> accuser(ctx.getSource(),
										StringArgumentType.getString(ctx, "nom")))))
				.then(Commands.literal("terminer")
						.executes(ctx -> terminer(ctx.getSource()))));
	}

	private static int aide(CommandSourceStack source) {
		dire(source, titre("MinVil : enquête villageoise"));
		dire(source, ligne("/enquete commencer [numéro] : lance une enquête (0 à " + (Enquete.NOMBRE - 1) + ")"));
		dire(source, ligne("Clic droit sur un villageois : l'interroger"));
		dire(source, ligne("/enquete fouiller : fouiller le lieu du crime"));
		dire(source, ligne("/enquete carnet : relire tes notes"));
		dire(source, ligne("/enquete accuser <nom> : désigner le coupable"));
		dire(source, ligne("/enquete terminer : renvoyer les villageois"));
		return 1;
	}

	private static int commencer(CommandSourceStack source, int numero) {
		Enquete nouvelle;
		try {
			nouvelle = Enquete.charger(numero);
		} catch (IOException e) {
			LOGGER.error("Impossible de charger l'enquête {}", numero, e);
			source.sendFailure(Component.literal("Impossible de charger l'enquête " + numero + " : " + e.getMessage()));
			return 0;
		}
		renvoyerVillageois(source.getServer());
		enquete = nouvelle;
		faireApparaitre(source.getLevel(), source.getPosition(), nouvelle);

		dire(source, titre("Enquête n° " + numero + " : la mort de " + nouvelle.nomVictime()));
		for (String phrase : nouvelle.scene()) {
			dire(source, ligne(phrase));
		}
		dire(source, Component.literal("Clic droit sur un villageois pour l'interroger. "
				+ "/enquete fouiller, /enquete carnet, /enquete accuser <nom>.").withStyle(ChatFormatting.GRAY));
		return 1;
	}

	private static int fouiller(CommandSourceStack source) {
		if (!enqueteEnCours(source)) {
			return 0;
		}
		enquete.trouverIndice();
		dire(source, Component.literal(enquete.texteFouille()).withStyle(ChatFormatting.YELLOW));
		return 1;
	}

	private static int carnet(CommandSourceStack source) {
		if (enquete == null) {
			source.sendFailure(Component.literal("Aucune enquête en cours. Lance /enquete commencer."));
			return 0;
		}
		dire(source, titre("Carnet de l'enquête n° " + enquete.seed()));
		if (enquete.indiceTrouve()) {
			dire(source, ligne("Près du corps : " + enquete.descriptionIndice() + "."));
		}
		if (enquete.interroges().isEmpty() && !enquete.indiceTrouve()) {
			dire(source, ligne("Ton carnet est vide."));
		}
		for (Enquete.Villageois v : enquete.vivants()) {
			if (enquete.interroges().contains(v.id())) {
				dire(source, declaration(v));
			}
		}
		return 1;
	}

	private static int accuser(CommandSourceStack source, String nom) {
		if (!enqueteEnCours(source)) {
			return 0;
		}
		var accuse = enquete.trouver(nom);
		if (accuse.isEmpty()) {
			source.sendFailure(Component.literal("Personne ne s'appelle « " + nom + " » dans cette enquête."));
			return 0;
		}
		boolean juste = enquete.estCoupable(accuse.get().id());
		enquete.terminer();
		if (juste) {
			dire(source, Component.literal("Bien vu : " + accuse.get().nom() + " avoue le crime.")
					.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
		} else {
			dire(source, Component.literal("Raté : " + accuse.get().nom() + " n'y est pour rien. Le coupable était "
					+ enquete.nomCoupable() + ".").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		}
		for (String phrase : enquete.explication()) {
			dire(source, ligne(phrase));
		}
		dire(source, Component.literal("Villageois interrogés : " + enquete.interroges().size() + " sur "
				+ enquete.vivants().size() + ". /enquete terminer pour renvoyer les villageois.")
				.withStyle(ChatFormatting.GRAY));
		return juste ? 1 : 0;
	}

	private static int terminer(CommandSourceStack source) {
		int renvoyes = renvoyerVillageois(source.getServer());
		enquete = null;
		dire(source, ligne("Enquête terminée : " + renvoyes + " villageois renvoyés."));
		return 1;
	}

	private static boolean enqueteEnCours(CommandSourceStack source) {
		if (enquete == null) {
			source.sendFailure(Component.literal("Aucune enquête en cours. Lance /enquete commencer."));
			return false;
		}
		if (enquete.terminee()) {
			source.sendFailure(Component.literal("Cette enquête est close. Lance /enquete commencer pour une nouvelle."));
			return false;
		}
		return true;
	}

	// --- Villageois ----------------------------------------------------------

	private static void faireApparaitre(ServerLevel niveau, Vec3 centre, Enquete e) {
		List<Enquete.Villageois> vivants = e.vivants();
		int n = vivants.size();
		var professions = niveau.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION);
		var typeVillageois = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:villager"));
		for (int i = 0; i < n; i++) {
			Enquete.Villageois v = vivants.get(i);
			double angle = 2 * Math.PI * i / n;
			double x = centre.x + RAYON * Math.cos(angle);
			double z = centre.z + RAYON * Math.sin(angle);
			// Tourné vers le centre du cercle.
			float lacet = (float) Math.toDegrees(Math.atan2(centre.z - z, centre.x - x)) - 90.0f;

			if (!(typeVillageois.create(niveau, EntitySpawnReason.COMMAND) instanceof Villager villageois)) {
				LOGGER.error("Impossible de créer le villageois {}", v.id());
				continue;
			}
			villageois.snapTo(x, centre.y, z, lacet, 0.0f);
			villageois.setYHeadRot(lacet);
			villageois.setCustomName(Component.literal(v.nom()));
			villageois.setCustomNameVisible(true);
			villageois.setNoAi(true);
			villageois.setPersistenceRequired();
			var cle = ResourceKey.create(Registries.VILLAGER_PROFESSION, Identifier.parse(v.professionMinecraft()));
			professions.get(cle).ifPresent(profession ->
					villageois.setVillagerData(villageois.getVillagerData().withProfession(profession)));
			villageois.addTag(TAG);
			villageoisIds.put(villageois.getUUID(), v.id());
			niveau.addFreshEntity(villageois);
		}
	}

	/** Retire les villageois de l'enquête précédente. Renvoie leur nombre. */
	private static int renvoyerVillageois(MinecraftServer serveur) {
		int total = 0;
		for (ServerLevel niveau : serveur.getAllLevels()) {
			List<Entity> aRetirer = new ArrayList<>();
			for (Entity entite : niveau.getAllEntities()) {
				if (villageoisIds.containsKey(entite.getUUID())) {
					aRetirer.add(entite);
				}
			}
			for (Entity entite : aRetirer) {
				entite.discard();
				total++;
			}
		}
		villageoisIds.clear();
		return total;
	}

	private static InteractionResult interroger(Player joueur, boolean coteClient, InteractionHand main, Entity entite) {
		String id = coteClient ? null : villageoisIds.get(entite.getUUID());
		if (id == null) {
			return InteractionResult.PASS;
		}
		// Bloque toujours l'écran de commerce, mais ne répond qu'une fois par clic.
		long tick = entite.level().getGameTime();
		Long precedent = dernierClic.put(joueur.getUUID(), tick);
		if (main != InteractionHand.MAIN_HAND || (precedent != null && precedent == tick)) {
			return InteractionResult.SUCCESS;
		}
		if (enquete == null) {
			joueur.sendSystemMessage(Component.literal("Ce villageois n'a plus rien à dire.")
					.withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		enquete.vivants().stream().filter(v -> v.id().equals(id)).findFirst().ifPresent(v -> {
			enquete.noterInterrogatoire(v.id());
			joueur.sendSystemMessage(declaration(v));
		});
		return InteractionResult.SUCCESS;
	}

	// --- Messages ------------------------------------------------------------

	private static Component declaration(Enquete.Villageois v) {
		return Component.literal(v.nom() + " (" + v.metier() + ") : ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal("« " + String.join(" ", enquete.declaration(v.id())) + " »")
						.withStyle(ChatFormatting.WHITE));
	}

	private static Component titre(String texte) {
		return Component.literal(texte).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
	}

	private static Component ligne(String texte) {
		return Component.literal(texte);
	}

	private static void dire(CommandSourceStack source, Component message) {
		source.sendSuccess(() -> message, false);
	}
}
