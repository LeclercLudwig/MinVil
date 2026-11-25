package fr.letitan.voyage;

/**
 * Le lien entre le dos du Titan et le monde d'en bas, à un instant donné.
 *
 * Sur le dos (la dimension du Titan), le Titan est immobile et regarde toujours vers le nord ;
 * dans le monde d'en bas, il est à la position (x, z), tourné selon son cap, et ses pieds
 * reposent sur le sol à la hauteur {@code altitude}. Un point du dos a donc une position
 * précise dans le monde d'en bas, et inversement : c'est ce qui permet de dessiner le vrai
 * sol sous le Titan, de dessiner le Titan vu du sol, et de tomber de l'un dans l'autre.
 */
public final class Repere {
	/** Hauteur du sol sous les pieds, dans le repère du dos. */
	public static final double Y_SOL = -21;

	public final double x, z, cap, altitude;
	private final double sin, cos;

	public Repere(double x, double z, double cap, double altitude) {
		this.x = x;
		this.z = z;
		this.cap = cap;
		this.altitude = altitude;
		this.sin = Math.sin(cap);
		this.cos = Math.cos(cap);
	}

	/** Différence de hauteur entre le monde d'en bas et le dos. */
	public double decalage() {
		return altitude - Y_SOL;
	}

	// --- Du dos vers le monde d'en bas ---------------------------------------

	public double versSolX(double xDos, double zDos) {
		return x + xDos * cos - zDos * sin;
	}

	public double versSolZ(double xDos, double zDos) {
		return z + xDos * sin + zDos * cos;
	}

	public double versSolY(double yDos) {
		return yDos + decalage();
	}

	// --- Du monde d'en bas vers le dos ---------------------------------------

	public double versDosX(double xSol, double zSol) {
		return cos * (xSol - x) + sin * (zSol - z);
	}

	public double versDosZ(double xSol, double zSol) {
		return -sin * (xSol - x) + cos * (zSol - z);
	}

	public double versDosY(double ySol) {
		return ySol - decalage();
	}

	/** Un lacet (en degrés, à la manière de Minecraft) sur le dos, vu dans le monde d'en bas. */
	public float lacetVersSol(float lacetDos) {
		return lacetDos + (float) Math.toDegrees(cap);
	}
}
