# Passation : prototype « enquête villageoise »

## Contexte
Projet de mod Minecraft en Java, pour **Fabric sur Minecraft 26.1** (voir « Choix techniques »). On a écarté les fractales. Le périmètre retenu combine :
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
- `generate()` produit un crime avec victime, coupable, fausse piste, lieu, PNJ, alibis, « qui a vu qui », rumeurs et témoins.
- Chaque villageois vivant déclare où il était et qui il y a vu. Les innocents disent vrai ; le coupable donne un faux lieu et dit n'y avoir vu personne.
- Deux villageois ont un motif : le coupable et la **fausse piste**. Les motifs ne sont connus que par des **rumeurs**, racontées par d'autres innocents.
- `solve()` résout l'enquête avec les seules informations accessibles au joueur (déclarations et rumeurs), sans regarder la vérité.
- `check()` vérifie : un seul coupable (motif + occasion), positions cohérentes, déclarations des innocents justes, mensonge du coupable contredit par au moins un témoin, alibi de la fausse piste confirmé par au moins un villageois, motifs tous révélés par des rumeurs, au moins deux suspects, et `solve()` désigne le bon coupable.
- Cinq tests passent, sur 500 seeds chacun.
- `enquete.py` : version jouable dans le terminal (interroger, carnet, accuser). 4 000 parties automatiques jouées sans erreur.

## Garanties du générateur
Les rôles sont placés d'office, plutôt que d'espérer que le hasard tombe bien (c'était le bug du premier prototype : 243 seeds sur 500 sans témoin) :
- un **témoin** est placé au lieu revendiqué par le coupable ;
- la **fausse piste** et un **partenaire d'alibi** sont placés ensemble, dans un lieu différent du crime et du mensonge ;
- les autres innocents sont placés au hasard, hors du lieu du crime ;
- il faut donc au moins trois innocents (5 PNJ minimum), sinon `generate()` lève une erreur.

Raisonnement attendu du joueur : parmi les suspects révélés par les rumeurs, le coupable est celui dont l'alibi est contredit (des villageois étaient à l'endroit annoncé et ne l'y ont pas vu) ; l'alibi de la fausse piste est confirmé.

Note : chaque réécriture change l'ordre des tirages aléatoires, donc une seed ne produit pas le même crime d'une version à l'autre.

## Prochaines étapes
1. ~~Garantir au moins un témoin.~~ Fait.
2. ~~Ajouter un PNJ de fausse piste avec motif mais alibi vrai.~~ Fait.
3. ~~Version jouable dans le terminal.~~ Fait : y jouer plusieurs parties pour juger si l'enquête est intéressante.
4. Ajouter un **indice physique** (objet laissé sur le lieu du crime) relié au coupable, pour faire converger les pistes.
5. Sérialiser le cas en JSON (PNJ, liens, événements) pour le porter ensuite dans le mod Java.
6. Seulement après : brancher le conseil du village (vote) et la réputation.

## Commandes
```
cd prototype
python3 -I mystery.py            # lance les tests
python3 -I mystery.py --seed 0   # affiche toute la vérité de la seed 0
python3 -I enquete.py            # joue une enquête tirée au hasard
python3 -I enquete.py 42         # rejoue l'enquête de la seed 42
```

## Choix techniques (mod)
- **Chargeur de mods : Fabric.** Plus léger que NeoForge et suffisant pour un prototype ; on pourra viser NeoForge plus tard si le besoin apparaît.
- **Version : Minecraft 26.1** (dernier correctif 26.1.x au moment de créer le projet ; vérifier qu'il n'existe pas une version plus récente prise en charge par Fabric).
  - Nouvelle numérotation de Mojang par année : 26.1 succède à 1.21.x.
  - Code de Minecraft publié sans obfuscation depuis 26.1 : on travaille avec les vrais noms de classes.
  - Échanges des villageois configurables par fichiers de données : utile pour l'économie plus tard.
  - **Java 25 obligatoire** pour compiler et lancer.
- **Compilation dans l'environnement cloud de Claude :** pas encore possible. Le réseau y bloque `maven.fabricmc.net`, `meta.fabricmc.net` et les serveurs de Mojang (`piston-meta.mojang.com`, etc.), et seul Java 21 y est installé. Il faudra autoriser ces hôtes dans les réglages réseau de l'environnement, ou compiler sur ta machine.

## Points de vigilance
- Le générateur est déterministe par seed : même seed, même crime. Garder ce comportement pour le débogage et la reproductibilité en jeu.
- Les tests vérifient la cohérence sur 500 seeds. Un bug qui n'apparaît que sur certaines seeds sera détecté par cette boucle.
- Le texte des témoignages est généré dans `enquete.py`, à partir de la structure de données. Les phrases évitent d'accorder au masculin ou au féminin : le genre des PNJ n'est pas modélisé.
