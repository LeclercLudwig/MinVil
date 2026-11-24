# Passation : prototype « enquête villageoise »

## Contexte
Projet de mod Minecraft en Java, pour **Fabric sur Minecraft 26.3** (voir « Choix techniques »). On a écarté les fractales. Le périmètre retenu combine :
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
- `generate()` produit un crime avec victime, coupable, fausse piste, lieu, PNJ, métiers, indice physique, alibis, « qui a vu qui », rumeurs et témoins.
- Chaque villageois vivant déclare où il était et qui il y a vu. Les innocents disent vrai ; le coupable donne un faux lieu et dit y avoir vu un villageois qui y était vraiment (un des témoins). Sa déclaration ressemble donc à celle d'un innocent : c'est la déclaration du villageois cité, qui ne le mentionne pas, qui le trahit.
- Deux villageois ont un motif : le coupable et la **fausse piste**. Les motifs ne sont connus que par des **rumeurs**, racontées par d'autres innocents.
- Chaque PNJ a un **métier**, connu du joueur. Un **indice physique** trouvé près du corps désigne le métier du coupable. Au moins un autre villageois partage ce métier, jamais la fausse piste.
- L'indice est **ambigu au premier regard** : en fouillant, le joueur voit une description vague (`CLUE_VAGUE`, par exemple « un bout de parchemin déchiré ») qui vaut pour plusieurs métiers (`clue_group()`), dont toujours celui de la fausse piste. Une fois l'objet trouvé, les innocents du bon métier (`clue_experts()`) le reconnaissent quand on les interroge (« Je reconnais un morceau de carte, on en trouve chez tous les cartographes »).
- **Rumeur fausse** (une enquête sur deux, `false_rumor`) : elle prête un motif à un innocent qui n'en a pas. Rien ne la distingue d'une vraie rumeur. La cible n'est jamais la fausse piste ni quelqu'un du métier désigné par l'indice, et son alibi n'est jamais contredit : les deux pistes la disculpent. N'importe quel villageois peut la raconter, y compris le coupable (environ une fois sur quatre), qui brouille ainsi les pistes.
- Deux pistes indépendantes mènent au coupable : `solve_by_alibi()` (alibi contredit) et `solve_by_clue()` (seul suspect du métier désigné). `solve()` exige qu'elles concordent. Aucune ne regarde la vérité : elles n'utilisent que ce que le joueur peut apprendre.
- `check()` vérifie : un seul coupable (motif + occasion), positions cohérentes, déclarations des innocents justes, mensonge du coupable contredit par au moins un témoin, coupable qui cite exactement un villageois vraiment présent au lieu de son mensonge, alibi de la fausse piste confirmé par au moins un villageois, motifs tous révélés par des rumeurs, au moins deux suspects, indice lié au coupable mais partagé par un autre villageois et pas par la fausse piste, et les deux pistes désignent le bon coupable.
- Huit tests passent (sept sur 500 seeds chacun, un sur les données des indices).
- `enquete.py` : version jouable dans le terminal (examiner, interroger, carnet, accuser). 4 000 parties automatiques jouées sans erreur.
- `export.py` : export d'une enquête en JSON pour le mod (format décrit dans `prototype/FORMAT_JSON.md`, exemples dans `prototype/exemples/`). La partie `public` (ce que le joueur peut apprendre) est séparée de `solution` ; `python3 -I export.py --test` vérifie que la partie publique suffit à trouver le coupable et ne dévoile rien.
- `web/` : page web de test, avec le même jeu (`index.html`). Le générateur y est porté en JavaScript (`mystery.js`), avec le même générateur aléatoire que Python : une même seed donne le même crime. `web/verifier.js` compare les deux versions (20 006 seeds identiques). **Toute modification de `generate()` ou des phrases dans les fichiers Python doit être reportée dans `mystery.js`**, puis vérifiée avec `node prototype/web/verifier.js`.

## Garanties du générateur
Les rôles sont placés d'office, plutôt que d'espérer que le hasard tombe bien (c'était le bug du premier prototype : 243 seeds sur 500 sans témoin) :
- un **témoin** est placé au lieu revendiqué par le coupable ;
- la **fausse piste** et un **partenaire d'alibi** sont placés ensemble, dans un lieu différent du crime et du mensonge ;
- les autres innocents sont placés au hasard, hors du lieu du crime ;
- il faut donc au moins trois innocents (5 PNJ minimum), sinon `generate()` lève une erreur.

Raisonnement attendu du joueur, deux pistes au choix parmi les suspects révélés par les rumeurs :
- **alibis** : le coupable est celui dont l'alibi est contredit (des villageois étaient à l'endroit annoncé et ne l'y ont pas vu) ; l'alibi de la fausse piste est confirmé ;
- **indice** : fouiller le lieu du crime, faire reconnaître l'objet par un villageois du métier, puis repérer le seul suspect qui exerce ce métier. Sans la reconnaissance, l'objet laisse deux suspects : le coupable et la fausse piste.

Les métiers, puis le villageois cité par le coupable, sont tirés en dernier : pour une seed donnée, ces ajouts n'ont pas changé les positions, motifs ni rumeurs. La rumeur fausse est tirée tout à la fin : elle n'a rien changé au reste. (Le passage à l'indice ambigu a changé le métier de la fausse piste, donc parfois celui de la victime et le témoin cité par le coupable.)

Note : chaque réécriture change l'ordre des tirages aléatoires, donc une seed ne produit pas le même crime d'une version à l'autre.

## Prochaines étapes
1. ~~Garantir au moins un témoin.~~ Fait.
2. ~~Ajouter un PNJ de fausse piste avec motif mais alibi vrai.~~ Fait.
3. ~~Version jouable dans le terminal.~~ Fait : y jouer plusieurs parties pour juger si l'enquête est intéressante.
4. ~~Ajouter un indice physique relié au coupable.~~ Fait.
5. ~~Équilibrer la difficulté.~~ Fait (coupable crédible, indice ambigu, rumeurs fausses) ; à confirmer en jouant.
6. ~~Sérialiser le cas en JSON.~~ Fait : `export.py`, format `minvil-enquete` version 1.
7. ~~Premier mod Fabric.~~ Fait : `mod/`, compilé par GitHub Actions. **Prochaine étape : le tester en jeu** (voir « Mod Fabric »).
8. Seulement après : brancher le conseil du village (vote) et la réputation.

## Commandes
```
cd prototype
python3 -I mystery.py            # lance les tests
python3 -I mystery.py --seed 0   # affiche toute la vérité de la seed 0
python3 -I enquete.py            # joue une enquête tirée au hasard
python3 -I enquete.py 42         # rejoue l'enquête de la seed 42
python3 -I export.py 42          # exporte l'enquête de la seed 42 en JSON
python3 -I export.py --test      # vérifie le format JSON
node web/verifier.js             # vérifie que la page web donne les mêmes crimes
```
La page web s'ouvre directement dans un navigateur : `prototype/web/index.html` (`#enquete-42` à la fin de l'adresse pour une seed précise).

## Choix techniques (mod)
- **Chargeur de mods : Fabric.** Plus léger que NeoForge et suffisant pour un prototype.
- **Version : Minecraft 26.3**, la version stable actuelle (novembre 2026). Un premier choix de 26.1 reposait sur une information périmée.
  - Fabric Loader 0.20.1, Fabric API 0.167.0+26.3, Loom 1.19 (plugin `net.fabricmc.fabric-loom`, sans mappings : le code de Minecraft n'est plus obfusqué), Gradle 9.8.2.
  - **Java 25** obligatoire pour compiler et lancer.
  - Noms officiels de Mojang. Changements rencontrés par rapport à 1.21 : `Villager` est dans `net.minecraft.world.entity.npc.villager` ; `EntityType.VILLAGER` est remplacé par `EntityTypes.VILLAGER` (le mod passe par le registre `minecraft:villager`) ; `Entity.getTags()`, `setInvulnerable()` et `Player.displayClientMessage()` n'existent plus (`sendSystemMessage` les remplace pour les messages).
- **Compilation : GitHub Actions** (`.github/workflows/mod.yml`). L'environnement cloud de Claude n'a pas accès aux serveurs de Fabric et de Mojang. À chaque envoi sur `main` ou une branche `ccr-*`, GitHub lance les tests Python, vérifie que les enquêtes embarquées sont à jour, compile le mod, lance les tests JUnit et publie le `.jar` (artefact « minvil-mod »). En cas d'échec, une étape de diagnostic affiche les vrais noms des classes et méthodes de Minecraft.

## Mod Fabric (`mod/`)
- `Enquete.java` lit le format JSON `minvil-enquete` ; `MinVil.java` est le point d'entrée (commandes, clic droit).
- **100 enquêtes embarquées** (seeds 0 à 99), générées par `python3 -I prototype/export.py $(seq 0 99) --dir mod/src/main/resources/minvil/enquetes`. À régénérer après toute modification du générateur, sinon la compilation sur GitHub échoue.
- **Commandes :** `/enquete` (aide), `/enquete commencer [numéro]`, `/enquete fouiller`, `/enquete carnet`, `/enquete accuser <nom>` (noms proposés), `/enquete terminer`. **Clic droit** sur un villageois de l'enquête : sa déclaration dans le chat, sans ouvrir l'écran de commerce.
- Les villageois apparaissent en cercle (rayon 4 blocs) autour du joueur, tournés vers lui, immobiles, nommés, avec la profession Minecraft de leur métier, et protégés des coups des joueurs. Ils portent l'étiquette `minvil_enquete` (`/kill @e[tag=minvil_enquete]` les retire en secours).
- **Installation pour tester :** dans un profil Minecraft 26.3 avec Fabric Loader 0.20.1, mettre dans le dossier `mods` le `.jar` de MinVil (artefact GitHub, à dézipper) et Fabric API 0.167.0+26.3.
- **Limites connues (prototype) :** une seule enquête à la fois sur le serveur, gardée en mémoire (perdue au redémarrage, villageois alors orphelins) ; les monstres peuvent encore blesser les villageois ; pas de victime ni d'objet dans le monde (tout passe par le chat) ; terrain non vérifié à l'apparition ; **jamais testé en jeu à ce stade**.

## Points de vigilance
- Le générateur est déterministe par seed : même seed, même crime. Garder ce comportement pour le débogage et la reproductibilité en jeu.
- Les tests vérifient la cohérence sur 500 seeds. Un bug qui n'apparaît que sur certaines seeds sera détecté par cette boucle.
- Le texte des témoignages est généré dans `enquete.py`, à partir de la structure de données. Les phrases évitent d'accorder au masculin ou au féminin : le genre des PNJ n'est pas modélisé.
- **Difficulté encore faible.** La piste de l'indice est très courte : examiner le lieu, trouver les deux rumeurs, et le coupable est le seul suspect du bon métier (3 à 4 actions). ~~Le coupable était le seul à dire « je n'y ai vu personne ».~~ Corrigé : il cite désormais un vrai témoin. Il reste un petit indice de forme : il ne cite jamais qu'une personne, même quand plusieurs villageois étaient sur place. ~~Indice trop direct.~~ Corrigé : l'objet est ambigu et doit être reconnu par un villageois du métier. ~~Rumeurs toujours vraies.~~ Corrigé : une enquête sur deux contient une rumeur fausse. Prochaine étape : jouer pour juger la difficulté.
- Les métiers sont proches des professions des villageois de Minecraft, pour faciliter le portage dans le mod.
