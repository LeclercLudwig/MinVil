package fr.letitan;

import fr.letitan.monde.FormeTitan;
import fr.letitan.monde.GenerateurTitan;
import fr.letitan.silence.Carcasses;
import fr.letitan.silence.MuetPayload;
import fr.letitan.silence.Muets;
import fr.letitan.silence.SecoussePayload;
import fr.letitan.silence.TitanMuet;
import fr.letitan.silence.Ombre;
import fr.letitan.silence.Recit;
import fr.letitan.silence.Silence;
import fr.letitan.sol.CarteSol;
import fr.letitan.sol.SolPayload;
import fr.letitan.vie.Accroche;
import fr.letitan.vie.AnimationTitan.Patte;
import fr.letitan.vie.CorpsVivant;
import fr.letitan.vie.Entrailles;
import fr.letitan.vie.Maladies;
import fr.letitan.vie.Squelette;
import fr.letitan.voyage.EtatTitanPayload;
import fr.letitan.voyage.Repere;
import fr.letitan.voyage.VoyageServeur;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Le Titan : tu vis sur le dos d'une créature colossale.
 *
 * Le dos du Titan est une dimension (« letitan:dos ») ; sa tête de dragon et ses pattes
 * sont animées (voir {@link CorpsVivant}). Les nouveaux joueurs y apparaissent. Le jour,
 * le Titan marche (pas, secousses, poussière, pattes qui avancent) ; la nuit, il dort
 * (tête basse, yeux fermés, battements de cœur). Tomber du Titan fait atterrir au sol,
 * dans le monde normal.
 *
 * Commandes (opérateurs) : /titan pour remonter sur le dos, /titan sol pour descendre.
 */
public final class LeTitan implements ModInitializer {
	public static final String MOD_ID = "letitan";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final ResourceKey<Level> DOS = ResourceKey.create(Registries.DIMENSION, id("dos"));

	/** Étiquette posée sur un joueur déjà arrivé une première fois sur le Titan. */
	private static final String ARRIVE = "letitan.arrive";
	/** En dessous, on est tombé du Titan (le bas du ventre est plus haut) : on passe dans le monde d'en bas. */
	private static final int Y_CHUTE = 30;
	/** On est déposé sur le dos à cette distance sous la hanche, en haut de la patte (avant de buter sur le ventre). */
	private static final int HAUT_DE_PATTE = 16;
	/** En grimpant à une patte depuis le monde d'en bas, on passe sur le Titan à cette hauteur (repère du dos). */
	private static final int Y_MONTEE = 45;
	/** On passe dans le monde d'en bas au plus tard à cette hauteur au-dessus de son sol. */
	private static final double MARGE_SOL = 20;
	/** Jusqu'à cette distance du Titan, on entend ses pas depuis le monde d'en bas. */
	private static final double PORTEE_PAS = 600;
	/** Point d'arrivée sur le dos, à côté de la crête d'os. */
	private static final int ARRIVEE_X = 24, ARRIVEE_Z = 0;

	public static Identifier id(String chemin) {
		return Identifier.fromNamespaceAndPath(MOD_ID, chemin);
	}

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("titan"), GenerateurTitan.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(EtatTitanPayload.TYPE, EtatTitanPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SolPayload.TYPE, SolPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MuetPayload.TYPE, MuetPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SecoussePayload.TYPE, SecoussePayload.CODEC);

		Accroche.serveur = new Accroche.Source() {
			@Override
			public Repere repere(Level niveau, double temps) {
				return niveau instanceof ServerLevel serveur ? VoyageServeur.etat(serveur.getServer()).repere() : null;
			}

			@Override
			public double veille(Level niveau) {
				return CorpsVivant.veille();
			}

			@Override
			public double marche(Level niveau) {
				return CorpsVivant.marche();
			}
		};
		ServerLifecycleEvents.SERVER_STARTED.register(serveur -> VoyageServeur.reinitialiser());
		ServerLifecycleEvents.SERVER_STOPPED.register(serveur -> {
			CarteSol.arreter();
			Silence.oublier();
		});
		registerSilence();
		ServerPlayerEvents.JOIN.register(joueur -> {
			VoyageServeur.accueillir(joueur);
			if (joueur.entityTags().contains(ARRIVE)) {
				return;
			}
			joueur.addTag(ARRIVE);
			monterSurLeTitan(joueur);
			joueur.sendSystemMessage(Component.literal("Tu te réveilles sur le dos du Titan. Non loin, une cabane en ruine : quelqu'un vivait ici avant toi.")
					.withStyle(ChatFormatting.GOLD));
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((ancien, joueur, finDuJeu) -> {
			// Sans lit, on renaît sur le Titan plutôt qu'au sol.
			if (joueur.getRespawnConfig() == null) {
				monterSurLeTitan(joueur);
			}
			Ombre.effacer(joueur);
			VoyageServeur.accueillir(joueur);
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((joueur, depart, arrivee) ->
				VoyageServeur.accueillir(joueur));
		ServerChunkEvents.CHUNK_LOAD.register((niveau, chunk, nouveau) -> {
			if (niveau.dimension() == DOS) {
				VoyageServeur.chunkCharge(niveau, chunk);
			} else if (niveau.dimension() == Level.OVERWORLD) {
				Carcasses.morceauCharge(niveau, chunk.getPos());
			}
		});
		ServerTickEvents.END_LEVEL_TICK.register(niveau -> {
			if (niveau.dimension() == DOS) {
				VoyageServeur.tick(niveau);
				tickTitan(niveau);
				CorpsVivant.tick(niveau);
				Entrailles.tick(niveau);
				Ombre.tickDos(niveau);
				Recit.tickDos(niveau);
				TitanMuet.tick(niveau);
				Muets.tickDos(niveau);
			} else if (niveau.dimension() == Level.OVERWORLD) {
				Silence.tickSol(niveau);
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, contexte, selection) ->
				dispatcher.register(Commands.literal("titan")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(ctx -> {
							monterSurLeTitan(ctx.getSource().getPlayerOrException());
							return 1;
						})
						.then(Commands.literal("sol").executes(ctx -> {
							descendreAuSol(ctx.getSource().getPlayerOrException(), false);
							return 1;
						}))
						// Pour essayer : rendre le Titan malade, ou le faire manger.
						.then(Commands.literal("infection").executes(ctx -> {
							ServerLevel dos = ctx.getSource().getServer().getLevel(DOS);
							Maladies.infecterAuHasard(dos);
							return 1;
						}))
						.then(Commands.literal("muet").executes(ctx -> {
							TitanMuet.appeler(ctx.getSource().getServer().getLevel(DOS));
							return 1;
						}))
						.then(Commands.literal("repas").executes(ctx -> {
							Entrailles.repas(ctx.getSource().getServer().getLevel(DOS));
							return 1;
						}))));
		PlayerBlockBreakEvents.AFTER.register((niveau, joueur, pos, etat, entite) -> {
			if (niveau instanceof ServerLevel dos && dos.dimension() == DOS && joueur instanceof ServerPlayer j) {
				Maladies.blocCasse(dos, j, pos, etat);
			}
		});
		LOGGER.info("Le Titan s'éveille.");
	}

	/** Le Silence : terres mortes, Muets qui chassent au bruit, Pierres-Échos. */
	private static void registerSilence() {
		ServerChunkEvents.CHUNK_GENERATE.register((niveau, chunk) -> {
			if (niveau.dimension() == Level.OVERWORLD) {
				Silence.nouveauMorceau(chunk);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entite, niveau) -> {
			if (niveau.dimension() == Level.OVERWORLD) {
				Muets.charge(entite);
			}
		});
		UseBlockCallback.EVENT.register((joueur, niveau, main, cible) -> Recit.toucher(joueur, niveau, cible));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victime, source, brut, inflige, bloque) -> {
			if (victime instanceof ServerPlayer joueur && Muets.estUnMuet(source.getEntity())) {
				Muets.touche(joueur);
			}
			if (source.getEntity() instanceof ServerPlayer joueur) {
				Muets.bruit(joueur);
			}
		});
		PlayerBlockBreakEvents.AFTER.register((niveau, joueur, pos, etat, entite) -> {
			if (joueur instanceof ServerPlayer j && niveau.dimension() == Level.OVERWORLD) {
				Muets.bruit(j);
			}
		});
	}

	// --- Le Titan vit --------------------------------------------------------

	private static void tickTitan(ServerLevel niveau) {
		long temps = niveau.getGameTime();
		if (Rythme.eveille(niveau)) {
			if (Rythme.pas(temps) && VoyageServeur.enMarche(niveau)) {
				pas(niveau, Rythme.force(temps));
			}
		}
		// Une copie : faire passer un joueur dans le monde d'en bas le retire de cette liste.
		for (ServerPlayer joueur : List.copyOf(niveau.players())) {
			if (tombeSurLeSol(niveau, joueur)) {
				descendreAuSol(joueur, true);
			}
		}
		// Dans le monde d'en bas, ceux qui grimpent à une patte arrivent sous le ventre du Titan.
		Repere repere = VoyageServeur.etat(niveau.getServer()).repere();
		for (ServerPlayer joueur : List.copyOf(niveau.getServer().overworld().players())) {
			if (Accroche.aUnePatte(joueur) == null) {
				continue;
			}
			if (repere.versDosY(joueur.getY()) > Y_MONTEE) {
				monterParUnePatte(joueur, niveau, repere);
			} else if (temps % 20 == 0) {
				joueur.sendOverlayMessage(Component.literal(joueur.isShiftKeyDown()
						? "Tu redescends le long de la patte... (relâche pour remonter)"
						: "La patte du Titan te hisse vers son dos... (accroupis-toi pour redescendre)")
						.withStyle(ChatFormatting.GOLD));
			}
		}
		// En haut d'une patte, sous le ventre : le Titan te dépose sur son dos.
		for (ServerPlayer joueur : List.copyOf(niveau.players())) {
			Patte patte = Accroche.aUnePatte(joueur);
			if (patte != null && !joueur.isShiftKeyDown() && joueur.getY() > Squelette.ancrePatte(patte).y - HAUT_DE_PATTE) {
				deposerSurLeDos(joueur, niveau, patte);
			}
		}
	}

	/**
	 * Le joueur est-il tombé sous le Titan, assez près du sol d'en bas (qu'on voit dessiné sous
	 * le dos) pour passer dans le vrai monde avant de le traverser ?
	 */
	private static boolean tombeSurLeSol(ServerLevel dos, ServerPlayer joueur) {
		if (joueur.getY() < Y_CHUTE) {
			return true;
		}
		if (joueur.getY() > FormeTitan.Y_CENTRE || joueur.onGround() || joueur.getDeltaMovement().y >= 0) {
			return false;
		}
		Repere repere = VoyageServeur.etat(dos.getServer()).repere();
		double xs = repere.versSolX(joueur.getX(), joueur.getZ()), zs = repere.versSolZ(joueur.getX(), joueur.getZ());
		double sol = repere.versDosY(CarteSol.de(dos.getServer()).hauteur(xs, zs));
		return joueur.getY() < sol + MARGE_SOL;
	}

	/** Une patte du Titan touche le sol : un grondement, et la poussière se soulève. */
	private static void pas(ServerLevel niveau, float force) {
		pasVuDuSol(niveau, force);
		var poussiere = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState());
		for (ServerPlayer joueur : niveau.players()) {
			niveau.playSound(null, joueur.getX(), joueur.getY() - 8, joueur.getZ(),
					SoundEvents.WARDEN_STEP, SoundSource.AMBIENT, 2.5f * force, 0.4f);
			niveau.playSound(null, joueur.getX(), joueur.getY() - 8, joueur.getZ(),
					SoundEvents.RAVAGER_STEP, SoundSource.AMBIENT, 1.5f * force, 0.5f);
			if (joueur.onGround()) {
				niveau.sendParticles(poussiere, joueur.getX(), joueur.getY() + 0.1, joueur.getZ(),
						(int) (25 * force), 3.0, 0.1, 3.0, 0.05);
			}
		}
	}

	/**
	 * Dans le monde d'en bas, la patte qui se pose fait trembler le sol : on l'entend de loin,
	 * et la poussière se soulève sous le pied.
	 */
	private static void pasVuDuSol(ServerLevel dos, float force) {
		ServerLevel sol = dos.getServer().overworld();
		if (sol.players().isEmpty()) {
			return;
		}
		long temps = dos.getGameTime();
		int numero = (int) Math.floorMod(Math.floorDiv(temps, Rythme.PERIODE), 4L);
		Patte patte = Patte.values()[0];
		for (Patte p : Patte.values()) {
			if (p.ordre == numero) {
				patte = p;
			}
		}
		Repere repere = VoyageServeur.etat(dos.getServer()).repere();
		Vector3f hanche = Squelette.ancrePatte(patte);
		// Le pied se pose en avant de la hanche.
		double x = repere.versSolX(hanche.x, hanche.z - 20), z = repere.versSolZ(hanche.x, hanche.z - 20);
		double y = repere.altitude;
		var poussiere = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState());
		for (ServerPlayer joueur : sol.players()) {
			double distance = Math.hypot(joueur.getX() - x, joueur.getZ() - z);
			if (distance > PORTEE_PAS) {
				continue;
			}
			// Le son vient du pied, mais on le fait jouer près du joueur pour qu'il porte loin.
			double proche = Math.min(1, 48 / Math.max(1, distance));
			double sx = joueur.getX() + (x - joueur.getX()) * proche, sz = joueur.getZ() + (z - joueur.getZ()) * proche;
			float volume = (float) (force * Math.max(0.3, 1 - distance / PORTEE_PAS) * 4);
			sol.playSound(null, sx, joueur.getY(), sz, SoundEvents.WARDEN_STEP, SoundSource.AMBIENT, volume, 0.35f);
			sol.playSound(null, sx, joueur.getY(), sz, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, volume * 0.3f, 0.4f);
		}
		sol.sendParticles(poussiere, x, y + 1, z, (int) (120 * force), 10.0, 1.0, 10.0, 0.15);
		sol.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 2, z, (int) (12 * force), 8.0, 1.0, 8.0, 0.02);
	}

	// --- Monter et descendre -------------------------------------------------

	private static void monterSurLeTitan(ServerPlayer joueur) {
		ServerLevel dos = joueur.level().getServer().getLevel(DOS);
		if (dos == null) {
			LOGGER.error("La dimension du Titan n'est pas chargée.");
			return;
		}
		int y = FormeTitan.plusHautBloc(ARRIVEE_X, ARRIVEE_Z, 250) + 1;
		// Lacet 180 : le regard tourné vers la tête, au nord.
		joueur.teleport(new TeleportTransition(dos, new Vec3(ARRIVEE_X + 0.5, y, ARRIVEE_Z + 0.5),
				Vec3.ZERO, 180.0f, 0.0f, TeleportTransition.DO_NOTHING));
	}

	/** Au sommet de la patte, on passe sur le dos, juste au-dessus, côté de la patte. */
	private static void deposerSurLeDos(ServerPlayer joueur, ServerLevel dos, Patte patte) {
		Vector3f hanche = Squelette.ancrePatte(patte);
		int z = (int) Math.floor(hanche.z);
		int x = (int) Math.round(patte.cote * 0.7 * FormeTitan.demiLargeur(z));
		int y = dos.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		joueur.teleportTo(dos, x + 0.5, y, z + 0.5, java.util.Set.of(), joueur.getYRot(), joueur.getXRot(), false);
		joueur.setDeltaMovement(Vec3.ZERO);
		joueur.sendOverlayMessage(Component.literal("Tu te hisses sur le dos du Titan.").withStyle(ChatFormatting.GOLD));
	}

	/**
	 * Passe du monde d'en bas au dos du Titan, en continuant de grimper à sa patte : même place
	 * par rapport au Titan, même élan, même regard.
	 */
	private static void monterParUnePatte(ServerPlayer joueur, ServerLevel dos, Repere repere) {
		double x = repere.versDosX(joueur.getX(), joueur.getZ()), z = repere.versDosZ(joueur.getX(), joueur.getZ());
		double y = repere.versDosY(joueur.getY());
		Vec3 v = joueur.getDeltaMovement();
		double sin = Math.sin(repere.cap), cos = Math.cos(repere.cap);
		Vec3 vitesse = new Vec3(v.x * cos + v.z * sin, v.y, -v.x * sin + v.z * cos);
		float lacet = joueur.getYRot() - (float) Math.toDegrees(repere.cap);
		joueur.teleport(new TeleportTransition(dos, new Vec3(x, y, z), vitesse, lacet, joueur.getXRot(),
				TeleportTransition.DO_NOTHING));
		joueur.sendOverlayMessage(Component.literal("Tu te hisses le long de la patte du Titan...")
				.withStyle(ChatFormatting.GOLD));
	}

	/**
	 * Descend au sol, dans le monde d'en bas, juste sous l'endroit où l'on était sur le Titan :
	 * le Titan voyage, donc l'endroit dépend de sa position et de son cap.
	 *
	 * En tombant ({@code chute}), on passe dans le monde d'en bas exactement à la même place
	 * par rapport au sol, avec la même vitesse : la chute continue, sans coupure.
	 */
	private static void descendreAuSol(ServerPlayer joueur, boolean chute) {
		MinecraftServer serveur = joueur.level().getServer();
		ServerLevel sol = serveur.overworld();
		boolean surLeDos = joueur.level().dimension() == DOS;
		Repere repere = VoyageServeur.etat(serveur).repere();
		double x = surLeDos ? repere.versSolX(joueur.getX(), joueur.getZ()) : joueur.getX();
		double z = surLeDos ? repere.versSolZ(joueur.getX(), joueur.getZ()) : joueur.getZ();
		// Le regard aussi tourne avec le cap : face à la tête du Titan sur son dos, face à sa route en bas.
		float lacet = surLeDos ? repere.lacetVersSol(joueur.getYRot()) : joueur.getYRot();
		double y;
		Vec3 vitesse = Vec3.ZERO;
		if (chute && surLeDos) {
			// Même hauteur au-dessus du sol que sous le Titan, mais jamais dans le relief.
			y = Math.max(repere.versSolY(joueur.getY()), CarteSol.de(serveur).hauteur(x, z) + 2);
			Vec3 v = joueur.getDeltaMovement();
			double sin = Math.sin(repere.cap), cos = Math.cos(repere.cap);
			vitesse = new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
			if (Accroche.aUnePatte(joueur) != null) {
				// On descend le long d'une patte : on continue de grimper, en bas.
				joueur.sendOverlayMessage(Component.literal("Tu descends le long de la patte du Titan...")
						.withStyle(ChatFormatting.GOLD));
			} else {
				joueur.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 30));
				joueur.sendSystemMessage(Component.literal("Tu glisses du flanc du Titan et tombes vers le sol...")
						.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
		} else if (chute) {
			y = 300;
			joueur.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 60));
		} else {
			int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
			sol.getChunk(bx >> 4, bz >> 4);
			y = sol.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) + 1;
		}
		joueur.teleport(new TeleportTransition(sol, new Vec3(x, y, z), vitesse,
				lacet, joueur.getXRot(), TeleportTransition.DO_NOTHING));
	}
}
