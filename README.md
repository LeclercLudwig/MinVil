# MinVil

Projet de mod Minecraft (Fabric, Minecraft 26.3) autour d'une enquête villageoise : un crime entre PNJ, résolu à partir d'indices et d'alibis.

Pour l'instant, le dépôt contient un prototype en Python, dans `prototype/` :
- `mystery.py` : le générateur de crime et ses tests ;
- `enquete.py` : une version jouable dans le terminal ;
- `export.py` : l'export d'une enquête en JSON pour le mod (voir `FORMAT_JSON.md`) ;
- `web/index.html` : la même enquête dans une page web, à ouvrir dans un navigateur.

```
cd prototype
python3 -I enquete.py            # jouer une enquête
python3 -I mystery.py            # lancer les tests
python3 -I mystery.py --seed 0   # afficher toute la vérité d'un cas
```

Le mod Fabric est dans `mod/`. Il est compilé par GitHub Actions : le `.jar` se télécharge depuis l'onglet « Actions » du dépôt (artefact « minvil-mod »).

### Tester le mod en jeu (version de développement)

Il faut un JDK 25 (sur Arch/CachyOS : `sudo pacman -S jdk25-openjdk`). Pas besoin de launcher ni de compte Minecraft :

```
cd mod
./gradlew runClient    # lance Minecraft 26.3 avec le mod, joueur « Enqueteur »
```

Le premier lancement télécharge Minecraft et Fabric (quelques minutes). Le jeu tourne dans `mod/run/` (mondes, options, journaux), ignoré par git.

En jeu : créer un monde en mode Créatif avec les commandes activées, puis `/enquete commencer`. En développement seulement, `/enquete solution` dévoile le coupable pour vérifier une enquête sans la jouer.

Pour l'installer dans un vrai Minecraft : `./gradlew build`, puis copier `mod/build/libs/minvil-0.1.0.jar` dans le dossier `mods/` d'une installation Fabric 26.3 avec Fabric API.

Contexte, choix de conception et prochaines étapes : voir [PASSATION.md](PASSATION.md).
