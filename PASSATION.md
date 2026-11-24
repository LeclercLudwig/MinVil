# Passation : prototype « enquête villageoise »

## Contexte
Projet de mod Minecraft (Java, Fabric ou NeoForge à terme). On a écarté les fractales. Le périmètre retenu combine :
- **Enquête** : un crime entre PNJ, résolu à partir d'indices et d'alibis.
- **Réputation et liens sociaux** : les rumeurs et les relations entre PNJ.
- **Généalogie et conseil** : lignées, votes, conséquences.
- **Économie, saisons et mémoire** : couches à ajouter plus tard.
- **Idées à intégrer (villages)** :
  - *Corps de métiers et apprentissage* : secrets de métier transmis du maître à l'apprenti ; si le maître meurt sans héritier, le savoir disparaît et peut devenir un objectif de quête. Se branche sur le graphe social (lien maître/apprenti).
  - *Fêtes et rituels* : coutumes locales (interdits, offrandes) ; respecter gagne la confiance, violer une coutume déclenche une dispute. Événements déclencheurs de liens et de rumeurs.
  - *Routes et voyageurs* : marchands, pèlerins, mercenaires qui apportent nouvelles, contrats et rumeurs venues de l'extérieur ; embuscades possibles.
  - *Cartographie ancienne (coffres avec cartes)* : **non évaluée**, l'idée n'a été lue qu'en partie. À clarifier avant d'intégrer.
  - Priorité suggérée : routes et voyageurs (source externe de rumeurs), puis fêtes, puis métiers. Ces trois idées passent après la boucle enquête.

Stratégie : construire **une seule boucle complète** avant d'élargir. Le prototype actuel ne couvre que la brique « génération de crime cohérent ».

## Fichiers
- `prototype/mystery.py` : générateur de graphe de vérité, `check()`, et trois tests (500 seeds chacun).

## Principe de conception
1. On décide d'abord la **vérité** : qui était où, qui a un motif, qui a l'occasion.
2. Les **alibis** et **témoignages** sont dérivés de cette vérité, jamais écrits librement.
3. Le coupable **ment** sur son lieu. Il doit être **démasquable** : au moins un témoin doit pouvoir le contredire.

## État actuel
- `generate()` produit un crime avec victime, coupable, lieu, PNJ, alibis, témoins.
- `check()` vérifie : un seul coupable (motif + occasion), position cohérente, alibis des innocents justes, mensonge du coupable contredit par au moins un témoin, témoins dérivés de la vérité.
- Les trois tests passent : `test_case_is_consistent`, `test_generation_is_deterministic`, `test_culprit_lies_and_is_contradicted`.
- `python3 -I mystery.py --seed N` affiche un cas lisible.

## Bug corrigé : absence de témoin
Le premier prototype n'ajoutait un témoin que si un innocent se trouvait **par hasard** au lieu revendiqué par le coupable. Le prototype a été réécrit (l'original n'avait pas été versionné) ; sans correctif, 243 seeds sur 500 n'avaient aucun témoin.

**Correctif appliqué** (`_ensure_witness`) : après le choix du mensonge (`lie`), si aucun innocent n'est à `lie`, un innocent tiré au sort y est déplacé. Garanties :
- le PNJ déplacé est toujours un innocent, jamais la victime ni le coupable ;
- `lie` est toujours différent du lieu du crime, donc le témoin n'acquiert pas d'occasion ;
- les témoins sont recalculés depuis `location_at_crime`, donc la position réelle reste cohérente ;
- le générateur refuse un cas sans aucun innocent.

Note : la réécriture ne tire pas les nombres aléatoires dans le même ordre que l'original, donc une seed donnée ne produit pas le même crime qu'avant.

## Prochaines étapes
1. ~~Garantir au moins un témoin.~~ Fait.
2. Ajouter un **indice physique** (objet laissé sur le lieu du crime) relié au coupable, pour faire converger les pistes.
3. Ajouter un **PNJ de fausse piste** avec motif mais alibi vrai, pour que l'enquête ait de vrais choix.
4. Sérialiser le cas en JSON (PNJ, liens, événements) pour le porter ensuite dans le mod Java.
5. Seulement après : brancher le conseil du village (vote) et la réputation.

## Commandes
```
cd prototype
python3 -I mystery.py            # lance les tests
python3 -I mystery.py --seed 0   # affiche le cas de la seed 0
```

## Points de vigilance
- Le générateur est déterministe par seed : même seed, même crime. Garder ce comportement pour le débogage et la reproductibilité en jeu.
- Les tests vérifient la cohérence sur 500 seeds. Un bug qui n'apparaît que sur certaines seeds sera détecté par cette boucle.
- Le texte des témoignages n'est pas encore généré. Il viendra après, à partir de la structure de données.
