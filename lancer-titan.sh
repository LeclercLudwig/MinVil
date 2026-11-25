#!/usr/bin/env bash
# Lance Minecraft 26.3 avec le mod Le Titan, en version de développement.
# Double-clic sur le raccourci « Le Titan (dev) » du bureau, ou dans un terminal :
#   ./lancer-titan.sh

pause() {
	echo
	read -rp "Appuie sur Entrée pour fermer cette fenêtre." _
}

cd "$(dirname "$(readlink -f "$0")")" || { echo "Dossier du projet introuvable."; pause; exit 1; }

if ! command -v java > /dev/null; then
	echo "Java n'est pas installé. Installe-le avec :"
	echo "  sudo pacman -S jdk25-openjdk"
	pause
	exit 1
fi

echo "Lancement de Minecraft avec Le Titan (version de développement)..."
echo "Le jeu s'ouvre dans une autre fenêtre au bout d'une minute environ."
echo "Pour arrêter : ferme le jeu, cette fenêtre se fermera toute seule."
echo

if ! ./gradlew runClient; then
	echo
	echo "Minecraft s'est arrêté sur une erreur (voir ci-dessus)."
	echo "Le journal du jeu est dans : $PWD/run/logs/latest.log"
	pause
	exit 1
fi
