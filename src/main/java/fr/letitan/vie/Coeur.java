package fr.letitan.vie;

import com.mojang.math.Transformation;
import fr.letitan.Rythme;
import fr.letitan.monde.Interieur;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import java.util.List;

/**
 * Le cœur du Titan, au milieu de sa chambre : un énorme muscle rouge qui se contracte à chaque
 * battement (un double coup), lentement quand il dort. On l'entend de loin dans les entrailles ;
 * la nuit, on l'entend battre sous ses pieds sur tout le dos.
 */
final class Coeur {
	private static final String ETIQUETTE = "letitan.coeur";
	/** Ticks entre deux poses (et durée du glissement côté client). */
	private static final int CADENCE = 2;
	/** Gonflement à la contraction. */
	private static final float GONFLEMENT = 0.16f;

	/** Une pièce du cœur : boîte (coin, taille) autour du centre, et son bloc. */
	private record Piece(Vector3f coin, Vector3f taille, BlockState bloc) {
	}

	private static final List<Piece> PIECES = List.of(
			// Le ventricule, gros et rouge vif.
			new Piece(new Vector3f(-5, -6, -4.5f), new Vector3f(10, 10, 9), Blocks.REDSTONE_BLOCK.defaultBlockState()),
			new Piece(new Vector3f(-3, -8, -3), new Vector3f(6, 3, 6), Blocks.REDSTONE_BLOCK.defaultBlockState()),
			// Les oreillettes, plus sombres.
			new Piece(new Vector3f(-6, 3, -3), new Vector3f(5, 4, 5), Blocks.NETHER_WART_BLOCK.defaultBlockState()),
			new Piece(new Vector3f(1, 3, -2), new Vector3f(5, 4, 5), Blocks.NETHER_WART_BLOCK.defaultBlockState()),
			// L'aorte et les veines, bleutées, qui montent vers le plafond.
			new Piece(new Vector3f(-1.5f, 4, -1.5f), new Vector3f(3, 7, 3), Blocks.WARPED_WART_BLOCK.defaultBlockState()),
			new Piece(new Vector3f(3, 6, 0), new Vector3f(2, 4, 2), Blocks.WARPED_WART_BLOCK.defaultBlockState()));

	private static ServerLevel niveau;
	private static final List<Display.BlockDisplay> AFFICHAGES = new ArrayList<>();

	private Coeur() {
	}

	static void tick(ServerLevel dos) {
		if (niveau != dos) {
			naitre(dos);
		}
		long temps = dos.getGameTime();
		boolean eveille = Rythme.eveille(dos);
		if (Rythme.bat(temps, eveille)) {
			battre(dos, eveille);
		}
		if (temps % CADENCE == 0) {
			poser(Rythme.battement(temps + CADENCE, eveille));
		}
		if (temps % 200 == 0) {
			AFFICHAGES.removeIf(Entity::isRemoved);
			if (AFFICHAGES.size() < PIECES.size()) {
				naitre(dos);
			}
		}
	}

	private static void naitre(ServerLevel dos) {
		niveau = dos;
		double[] c = Interieur.COEUR;
		dos.setChunkForced((int) Math.floor(c[0]) >> 4, (int) Math.floor(c[2]) >> 4, true);
		List<Entity> anciens = new ArrayList<>();
		for (Entity e : dos.getAllEntities()) {
			if (e.entityTags().contains(ETIQUETTE)) {
				anciens.add(e);
			}
		}
		anciens.forEach(Entity::discard);
		AFFICHAGES.clear();
		for (Piece p : PIECES) {
			Display.BlockDisplay affichage = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, dos);
			affichage.setPos(c[0], c[1], c[2]);
			affichage.setBlockState(p.bloc());
			affichage.setTransformationInterpolationDuration(CADENCE);
			affichage.setViewRange(2.0f);
			// Il luit faiblement dans le noir des entrailles.
			affichage.setBrightnessOverride(new Brightness(12, 0));
			affichage.addTag(ETIQUETTE);
			dos.addFreshEntity(affichage);
			AFFICHAGES.add(affichage);
		}
		poser(0);
	}

	/** Pose le cœur, contracté de {@code contraction} (0 à 1) : il gonfle autour de son centre. */
	private static void poser(double contraction) {
		float echelle = 1 + GONFLEMENT * (float) contraction;
		for (int i = 0; i < AFFICHAGES.size() && i < PIECES.size(); i++) {
			Piece p = PIECES.get(i);
			Display.BlockDisplay a = AFFICHAGES.get(i);
			a.setTransformation(new Transformation(new Vector3f(p.coin()).mul(echelle), new Quaternionf(),
					new Vector3f(p.taille()).mul(echelle), new Quaternionf()));
			a.setTransformationInterpolationDuration(CADENCE);
			a.setTransformationInterpolationDelay(0);
		}
	}

	/** Un battement : fort près du cœur ; la nuit, sourd sous les pieds de tous ceux qui sont sur le dos. */
	private static void battre(ServerLevel dos, boolean eveille) {
		double[] c = Interieur.COEUR;
		for (ServerPlayer joueur : dos.players()) {
			double distance = Math.sqrt(joueur.distanceToSqr(c[0], c[1], c[2]));
			if (distance < 60) {
				dos.playSound(null, c[0], c[1], c[2], SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 4.0f, 0.5f);
			} else if (!eveille) {
				dos.playSound(null, joueur.getX(), joueur.getY() - 4, joueur.getZ(),
						SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.7f, 0.5f);
			}
		}
	}
}
