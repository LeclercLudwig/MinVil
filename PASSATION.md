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
- `/home/claude/village_mystery/mystery.py` : générateur de graphe de vérité, `check()`, et trois tests.

## Principe de conception
1. On décide d'abord la **vérité** : qui était où, qui a un motif, qui a l'occasion.
2. Les **alibis** et **témoignages** sont dérivés de cette vérité, jamais écrits librement.
3. Le coupable **ment** sur son lieu. Il doit être **démasquable** : au moins un témoin doit pouvoir le contredire.

## État actuel
- `generate()` produit un crime avec victime, coupable, lieu, PNJ, alibis, témoins.
- `check()` vérifie : un seul coupable (motif + occasion), position cohérente, alibis des innocents justes.
- **Test qui échoue** : `test_culprit_lies_and_is_contradicted`, seed 0, aucun témoin.

## Bug connu (à corriger en premier)
Dans `generate()`, un témoin n'est ajouté que si un innocent se trouve **par hasard** au lieu que le coupable prétend avoir fréquenté. Rien ne garantit ce cas. Sans témoin, le joueur ne peut pas démasquer le coupable, donc le scénario est injouable.

**Correctif proposé** : après avoir choisi le mensonge du coupable (`lie`), forcer au moins un innocent à se trouver à `lie` au moment du crime, et l'ajouter comme témoin. Ce PNJ doit aussi avoir une position réelle cohérente dans `location_at_crime`.

## Prochaines étapes
1. Corriger le générateur pour garantir au moins un témoin, puis relancer `python3 -I mystery.py`. Les trois tests doivent passer.
2. Ajouter un **indice physique** (objet laissé sur le lieu du crime) relié au coupable, pour faire converger les pistes.
3. Ajouter un **PNJ de fausse piste** avec motif mais alibi vrai, pour que l'enquête ait de vrais choix.
4. Sérialiser le cas en JSON (PNJ, liens, événements) pour le porter ensuite dans le mod Java.
5. Seulement après : brancher le conseil du village (vote) et la réputation.

## Commandes
```
cd /home/claude/village_mystery
python3 -I mystery.py
```

## Points de vigilance
- Le générateur est déterministe par seed : même seed, même crime. Garder ce comportement pour le débogage et la reproductibilité en jeu.
- Les tests vérifient la cohérence sur 500 seeds. Un bug qui n'apparaît que sur certaines seeds sera détecté par cette boucle.
- Le texte des témoignages n'est pas encore généré. Il viendra après, à partir de la structure de données.
