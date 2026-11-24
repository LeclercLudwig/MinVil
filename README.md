# MinVil

Projet de mod Minecraft (Fabric, Minecraft 26.1) autour d'une enquête villageoise : un crime entre PNJ, résolu à partir d'indices et d'alibis.

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

Contexte, choix de conception et prochaines étapes : voir [PASSATION.md](PASSATION.md).
