package fr.minvil;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Une enquête chargée depuis un fichier JSON produit par prototype/export.py
 * (format « minvil-enquete », version 1, décrit dans prototype/FORMAT_JSON.md),
 * et l'avancement du joueur dans cette enquête.
 *
 * La partie « solution » du JSON n'est lue que pour juger une accusation.
 */
public final class Enquete {
	public static final String FORMAT = "minvil-enquete";
	public static final int VERSION = 1;
	/** Nombre d'enquêtes embarquées dans le mod (seeds 0 à NOMBRE - 1). */
	public static final int NOMBRE = 100;

	private final JsonObject racine;
	private boolean indiceTrouve;
	private boolean terminee;
	private final Set<String> interroges = new LinkedHashSet<>();

	private Enquete(JsonObject racine) {
		this.racine = racine;
	}

	/** Charge l'enquête embarquée de cette seed. */
	public static Enquete charger(int seed) throws IOException {
		String chemin = "/minvil/enquetes/enquete-" + seed + ".json";
		try (InputStream in = Enquete.class.getResourceAsStream(chemin)) {
			if (in == null) {
				throw new IOException("Enquête introuvable : " + chemin);
			}
			return lire(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
	}

	/** Lit une enquête depuis un flux JSON et vérifie son format. */
	public static Enquete lire(java.io.Reader lecteur) throws IOException {
		JsonObject racine;
		try {
			racine = JsonParser.parseReader(lecteur).getAsJsonObject();
		} catch (RuntimeException e) {
			throw new IOException("JSON d'enquête illisible", e);
		}
		String format = texte(racine, "format");
		int version = racine.has("version") ? racine.get("version").getAsInt() : -1;
		if (!FORMAT.equals(format) || version != VERSION) {
			throw new IOException("Format d'enquête non pris en charge : " + format + " version " + version);
		}
		return new Enquete(racine);
	}

	// --- Lecture du JSON -----------------------------------------------------

	public int seed() {
		return racine.get("seed").getAsInt();
	}

	private JsonObject publique() {
		return racine.getAsJsonObject("public");
	}

	private JsonObject solution() {
		return racine.getAsJsonObject("solution");
	}

	private JsonObject textes() {
		return racine.getAsJsonObject("texts").getAsJsonObject("fr");
	}

	/** Villageois vivants, dans l'ordre du fichier (alphabétique). */
	public List<Villageois> vivants() {
		List<Villageois> liste = new ArrayList<>();
		for (JsonElement e : publique().getAsJsonArray("villagers")) {
			JsonObject v = e.getAsJsonObject();
			if (v.get("alive").getAsBoolean()) {
				liste.add(new Villageois(texte(v, "id"), texte(v, "name"),
						texte(textes().getAsJsonObject("trades"), texte(v, "trade")),
						texte(v, "minecraft_profession")));
			}
		}
		return liste;
	}

	public String nomVictime() {
		String id = texte(publique(), "victim");
		for (JsonElement e : publique().getAsJsonArray("villagers")) {
			JsonObject v = e.getAsJsonObject();
			if (id.equals(texte(v, "id"))) {
				return texte(v, "name");
			}
		}
		return id;
	}

	public List<String> scene() {
		return lignes(textes().getAsJsonArray("scene"));
	}

	/** Ce que dit ce villageois, selon que le lieu du crime a été fouillé. */
	public List<String> declaration(String id) {
		JsonObject phrases = textes().getAsJsonObject("statements").getAsJsonObject(id);
		return lignes(phrases.getAsJsonArray(indiceTrouve ? "with_clue" : "without_clue"));
	}

	public String texteFouille() {
		return texte(textes(), "examine");
	}

	public String descriptionIndice() {
		return texte(publique().getAsJsonObject("clue"), "description");
	}

	/** Explication de fin de partie : à n'afficher qu'après l'accusation. */
	public List<String> explication() {
		return lignes(solution().getAsJsonObject("explanation").getAsJsonArray("fr"));
	}

	/** Vrai si l'accusé (id) est le coupable. */
	public boolean estCoupable(String id) {
		return texte(solution(), "culprit").equals(id);
	}

	public String nomCoupable() {
		String id = texte(solution(), "culprit");
		return vivants().stream().filter(v -> v.id().equals(id)).map(Villageois::nom)
				.findFirst().orElse(id);
	}

	/** Retrouve un villageois vivant d'après ce que tape le joueur (nom ou id). */
	public Optional<Villageois> trouver(String saisie) {
		String cle = normaliser(saisie);
		return vivants().stream()
				.filter(v -> normaliser(v.nom()).equals(cle) || v.id().equals(cle))
				.findFirst();
	}

	// --- Avancement ------------------------------------------------------------

	public boolean indiceTrouve() {
		return indiceTrouve;
	}

	public void trouverIndice() {
		indiceTrouve = true;
	}

	public boolean terminee() {
		return terminee;
	}

	public void terminer() {
		terminee = true;
	}

	public void noterInterrogatoire(String id) {
		interroges.add(id);
	}

	/** Ids des villageois déjà interrogés, dans l'ordre. */
	public List<String> interroges() {
		return List.copyOf(interroges);
	}

	// --- Outils --------------------------------------------------------------------

	public record Villageois(String id, String nom, String metier, String professionMinecraft) {
	}

	static String normaliser(String texte) {
		String sansAccents = Normalizer.normalize(texte.trim(), Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "");
		return sansAccents.toLowerCase(Locale.ROOT);
	}

	private static String texte(JsonObject objet, String cle) {
		JsonElement e = objet.get(cle);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}

	private static List<String> lignes(JsonArray tableau) {
		List<String> liste = new ArrayList<>();
		for (JsonElement e : tableau) {
			liste.add(e.getAsString());
		}
		return liste;
	}
}
