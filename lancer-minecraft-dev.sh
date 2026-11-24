#!/usr/bin/env bash
# Lance Minecraft 26.3 avec le mod MinVil, en version de développement.
# Double-clic sur le raccourci « MinVil (dev) » du bureau, ou dans un terminal :
#   ./lancer-minecraft-dev.sh

pause() {
	echo
	read -rp "Appuie sur Entrée pour fermer cette fenêtre." _
}

cd "$(dirname "$(readlink -f "$0")")/mod" || { echo "Dossier mod/ introuvable."; pause; exit 1; }

if ! command -v java > /dev/null; then
	echo "Java n'est pas installé. Installe-le avec :"
	echo "  sudo pacman -S jdk25-openjdk"
	pause
	exit 1
fi

echo "Lancement de Minecraft avec MinVil (version de développement)..."
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
