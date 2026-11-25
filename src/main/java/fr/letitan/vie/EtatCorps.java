package fr.letitan.vie;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

import static fr.letitan.LeTitan.id;

/**
 * La santé du Titan, sauvegardée avec le monde : sa santé (0 à 100), les foyers d'infection
 * logés dans ses organes, les restes de ses derniers repas dans l'estomac, et la version de
 * l'aménagement de son intérieur déjà appliquée à ce monde.
 */
public final class EtatCorps extends SavedData {

	/** Un foyer d'infection : un kyste dans la paroi d'un organe, qui grossit avec le temps. */
	public static final class Foyer {
		public static final Codec<Foyer> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("organe").forGetter(f -> f.organe),
				Codec.INT.fieldOf("x").forGetter(f -> f.x),
				Codec.INT.fieldOf("y").forGetter(f -> f.y),
				Codec.INT.fieldOf("z").forGetter(f -> f.z),
				Codec.DOUBLE.fieldOf("force").forGetter(f -> f.force)
		).apply(i, Foyer::new));

		public final String organe;
		public final int x, y, z;
		/** De 0 (naissant) à 1 (installé) : il s'étend et lâche plus de parasites. */
		public double force;

		public Foyer(String organe, int x, int y, int z, double force) {
			this.organe = organe;
			this.x = x;
			this.y = y;
			this.z = z;
			this.force = force;
		}
	}

	public static final Codec<EtatCorps> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.optionalFieldOf("sante", 100.0).forGetter(e -> e.sante),
			Foyer.CODEC.listOf().optionalFieldOf("foyers", List.of()).forGetter(e -> e.foyers),
			Codec.LONG.listOf().optionalFieldOf("repas", List.of()).forGetter(e -> e.repas),
			Codec.INT.optionalFieldOf("amenagement", 0).forGetter(e -> e.amenagement)
	).apply(i, EtatCorps::new));

	public static final SavedDataType<EtatCorps> TYPE =
			new SavedDataType<>(id("corps"), EtatCorps::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	public double sante = 100;
	public final List<Foyer> foyers = new ArrayList<>();
	/** Positions (BlockPos compactées) des tonneaux de restes, du plus ancien au plus récent. */
	public final List<Long> repas = new ArrayList<>();
	public int amenagement;

	public EtatCorps() {
	}

	private EtatCorps(double sante, List<Foyer> foyers, List<Long> repas, int amenagement) {
		this.sante = sante;
		this.foyers.addAll(foyers);
		this.repas.addAll(repas);
		this.amenagement = amenagement;
	}

	/** Allure de marche selon la santé : ralenti quand il est malade, immobile quand il n'en peut plus. */
	public double allure() {
		return sante < Maladies.SANTE_IMMOBILE ? 0 : 0.5 + 0.5 * sante / 100;
	}
}
