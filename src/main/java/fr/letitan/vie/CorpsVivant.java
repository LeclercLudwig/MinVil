package fr.letitan.vie;

import com.mojang.math.Transformation;
import fr.letitan.LeTitan;
import fr.letitan.Rythme;
import fr.letitan.vie.AnimationTitan.Patte;
import fr.letitan.vie.Boite.Bloc;
import fr.letitan.vie.ModeleTitan.Piece;
import fr.letitan.vie.Squelette.Pose;
import fr.letitan.voyage.VoyageServeur;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * La tête et les pattes du Titan, faites d'entités d'affichage de blocs (block displays)
 * que le serveur repose toutes les {@link #CADENCE} ticks. Le client glisse d'une pose
 * à la suivante (interpolation), ce qui donne un mouvement fluide.
 *
 * Les entités ne sont pas gardées d'une partie à l'autre : au démarrage, on retire
 * celles qui restaient et on recrée le corps.
 */
public final class CorpsVivant {
	/** Étiquette de toutes les entités du corps animé. */
	private static final String ETIQUETTE = "letitan.corps";
	/** Ticks entre deux poses (et durée du glissement entre elles côté client). */
	private static final int CADENCE = 4;
	/** Vitesse du passage veille/sommeil ou marche/arrêt, par mise à jour (environ 20 secondes en tout). */
	private static final double TRANSITION = 0.02;

	/** Un bloc affiché : à quelle pièce il appartient et quelle tuile il montre. */
	private record Element(Display.BlockDisplay affichage, Piece piece, Patte patte, Boite tuile) {
	}

	private static CorpsVivant actuel;

	private final ServerLevel niveau;
	private final List<Element> elements = new ArrayList<>();
	private final Set<UUID> nos = new HashSet<>();
	private double veille;
	private double marche;
	private boolean yeuxOuverts = true;

	private CorpsVivant(ServerLevel niveau) {
		this.niveau = niveau;
		this.veille = Rythme.eveille(niveau) ? 1 : 0;
		this.marche = VoyageServeur.etat(niveau.getServer()).enMarche() ? 1 : 0;
	}

	/** Allure actuelle du Titan, de 0 (endormi) à 1 (éveillé). */
	public static double veille() {
		return actuel == null ? 1 : actuel.veille;
	}

	/** Allure actuelle du Titan, de 0 (arrêté) à 1 (en marche). */
	public static double marche() {
		return actuel == null ? 1 : actuel.marche;
	}

	/** Appelé à chaque tick de la dimension du Titan. */
	public static void tick(ServerLevel niveau) {
		if (actuel == null || actuel.niveau != niveau) {
			actuel = new CorpsVivant(niveau);
			actuel.naitre();
		}
		actuel.vivre();
	}

	// --- Création ------------------------------------------------------------

	private void naitre() {
		// Les ancres restent chargées : le corps bouge même quand on est loin.
		garderCharge(Squelette.ancreTete());
		for (Patte patte : Patte.values()) {
			garderCharge(Squelette.ancrePatte(patte));
		}
		retirerLesAnciens();
		Lianes.pousser(niveau);
		long temps = niveau.getGameTime();
		creer(Piece.TETE, null, Squelette.ancreTete());
		creer(Piece.MACHOIRE, null, Squelette.ancreTete());
		for (Patte patte : Patte.values()) {
			for (Piece piece : new Piece[] {Piece.CUISSE, Piece.JAMBE, Piece.PIED}) {
				creer(piece, patte, Squelette.ancrePatte(patte));
			}
		}
		poser(temps, 0);
		LeTitan.LOGGER.info("Le corps du Titan prend vie : {} blocs animés.", elements.size());
	}

	private void garderCharge(Vector3f position) {
		niveau.setChunkForced((int) Math.floor(position.x) >> 4, (int) Math.floor(position.z) >> 4, true);
	}

	private void creer(Piece piece, Patte patte, Vector3f ancre) {
		for (Boite boite : ModeleTitan.boites(piece)) {
			for (Boite tuile : boite.tuiles(ModeleTitan.TUILE)) {
				Display.BlockDisplay affichage = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, niveau);
				affichage.setPos(ancre.x, ancre.y, ancre.z);
				affichage.setBlockState(bloc(tuile.bloc(), true));
				affichage.setTransformationInterpolationDuration(CADENCE);
				affichage.setViewRange(8.0f);
				// Éclairé comme en plein air : l'ancre est parfois enfoncée dans le corps, dans le noir.
				affichage.setBrightnessOverride(new Brightness(tuile.bloc() == Bloc.OEIL ? 15 : 0, 15));
				affichage.addTag(ETIQUETTE);
				niveau.addFreshEntity(affichage);
				elements.add(new Element(affichage, piece, patte, tuile));
				nos.add(affichage.getUUID());
			}
		}
	}

	/** Retire les entités du corps restées d'une partie précédente. */
	private void retirerLesAnciens() {
		List<Entity> anciens = new ArrayList<>();
		for (Entity entite : niveau.getAllEntities()) {
			if (entite.entityTags().contains(ETIQUETTE) && !nos.contains(entite.getUUID())) {
				anciens.add(entite);
			}
		}
		anciens.forEach(Entity::discard);
	}

	private static BlockState bloc(Bloc bloc, boolean yeuxOuverts) {
		return switch (bloc) {
			case PEAU -> Blocks.PACKED_MUD.defaultBlockState();
			case ECAILLE -> Blocks.MUD_BRICKS.defaultBlockState();
			case OS -> Blocks.BONE_BLOCK.defaultBlockState();
			case OEIL -> yeuxOuverts ? Blocks.SHROOMLIGHT.defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState();
			case NARINE -> Blocks.BLACKSTONE.defaultBlockState();
			case GUEULE -> Blocks.NETHER_WART_BLOCK.defaultBlockState();
		};
	}

	// --- Vie -----------------------------------------------------------------

	private void vivre() {
		long temps = niveau.getGameTime();
		if (temps % 100 == 0) {
			// Des entités d'une partie précédente peuvent finir de charger après le démarrage.
			retirerLesAnciens();
		}
		if (Rythme.eveille(niveau) && marche > 0.5 && AnimationTitan.debutRugissement(temps)) {
			Vector3f tete = Squelette.ancreTete();
			niveau.playSound(null, tete.x, tete.y, tete.z - 60, SoundEvents.ENDER_DRAGON_GROWL,
					SoundSource.HOSTILE, 12.0f, 0.5f);
		}
		if (temps % CADENCE != 0) {
			return;
		}
		veille = approcher(veille, Rythme.eveille(niveau) ? 1 : 0);
		marche = approcher(marche, VoyageServeur.enMarche(niveau) || !Rythme.eveille(niveau) ? 1 : 0);
		// On vise la pose de la fin du glissement : le client arrive pile à l'heure.
		poser(temps + CADENCE, CADENCE);
	}

	private static double approcher(double valeur, double cible) {
		return valeur + Math.max(-TRANSITION, Math.min(TRANSITION, cible - valeur));
	}

	private void poser(double temps, int duree) {
		Pose tete = Squelette.tete(temps, veille, marche);
		Pose machoire = Squelette.machoire(tete, temps, veille, marche);
		Pose[][] pattes = new Pose[Patte.values().length][];
		for (Patte patte : Patte.values()) {
			pattes[patte.ordinal()] = Squelette.patte(patte, temps, veille, marche);
		}
		boolean ouverts = AnimationTitan.yeuxOuverts(temps, veille);
		for (Element e : elements) {
			Pose pose = switch (e.piece()) {
				case TETE -> tete;
				case MACHOIRE -> machoire;
				case CUISSE -> pattes[e.patte().ordinal()][0];
				case JAMBE -> pattes[e.patte().ordinal()][1];
				case PIED -> pattes[e.patte().ordinal()][2];
			};
			Boite t = e.tuile();
			e.affichage().setTransformation(new Transformation(pose.coin(t), new Quaternionf(pose.rotation()),
					new Vector3f(t.largeur(), t.hauteur(), t.profondeur()), new Quaternionf()));
			e.affichage().setTransformationInterpolationDuration(duree);
			e.affichage().setTransformationInterpolationDelay(0);
			if (t.bloc() == Bloc.OEIL && ouverts != yeuxOuverts) {
				e.affichage().setBlockState(bloc(Bloc.OEIL, ouverts));
			}
		}
		yeuxOuverts = ouverts;
	}
}
