# Le Titan

Mod Minecraft (Fabric, Minecraft 26.3, Java 25). Tu vis sur le dos d'une créature colossale : c'est ton monde entier. Le jour, elle marche ; la nuit, elle dort.

## Ce qui le distingue

D'autres mods posent une base sur le dos d'un géant qui marche dans le monde normal (Colossi, Living Decks) : le géant y est une monture ou une base mobile. Ici, **le Titan est le monde** : on naît sur son dos, son corps est le terrain, et il vit (marche, sommeil, puis plus tard maladies, organes à explorer, climat qui change selon où il marche). Le monde normal n'est que le sol, loin en dessous.

## État actuel : jalons 1 à 3, « le dos », « le voyage », « descendre et remonter »

- **Le dos du Titan** est une dimension (`letitan:dos`) : un corps de 560 blocs de long, de la tête à la queue, suspendu au-dessus des nuages. Dos couvert de terre et de forêt, crête de plaques d'os le long de l'échine, peau épaisse (boue tassée) autour de la chair, tête avec cornes et yeux lumineux, quatre pattes qui descendent dans la brume.
- **Les nouveaux joueurs se réveillent sur le dos du Titan**, et y renaissent s'ils n'ont pas de lit.
- **Le jour, il marche** : un pas toutes les 2,5 secondes, grondement, poussière, et la caméra tressaute et se balance (pattes avant plus fortes).
- **La nuit, il dort** : son cœur bat sous tes pieds et la caméra suit sa respiration.
- **Il voyage sur le vrai monde** : il avance sur la carte du monde normal (évite les océans, s'arrête pour boire ou brouter), son dos prend le biome de la région traversée, et la boussole dit où il va.
- **Depuis son dos, on voit le vrai sol** défiler loin en dessous, jusqu'à l'horizon : relief et biomes du monde normal (calculés par son générateur, sans générer les chunks), qui tournent avec le Titan et s'assombrissent la nuit.
- **Depuis le monde normal, on voit le Titan marcher** à sa vraie place : corps couvert de forêt, crête, tête et pattes animées comme sur son dos. Ses pas s'entendent de loin, soulèvent la poussière et font trembler la caméra.
- **Tomber du Titan** fait passer dans le monde normal, juste en dessous, sans écran de chargement : la chute continue, ralentie, jusqu'au sol.
- **Descendre et remonter** : on saute de son flanc pour descendre (chute ralentie jusqu'au sol). Pour remonter, il suffit de toucher une de ses pattes : elle te hisse toute seule jusqu'à son dos, sans écran de chargement. Accroupi, on redescend le long de la patte (et l'on peut s'en éloigner).
- **Ne pas se faire distancer** : quand il s'arrête (2 minutes pour boire ou brouter, ou toute la nuit), on peut descendre récolter. Il prévient 30 secondes avant de repartir, et au sol une boussole indique où il est et ce qu'il fait.
- Commandes (opérateurs) : `/titan` remonte sur le dos, `/titan sol` descend au sol.

## Prochains jalons

1. ~~Le dos du Titan : forme, marche, sommeil, chute.~~
2. ~~Le voyage : le vrai sol défile en dessous, le Titan se voit depuis le sol, le dos change de biome.~~
3. ~~Descendre et remonter : sauter de son flanc, toucher une patte pour être hissé, avant qu'il reparte.~~
4. **Le corps vivant** : organes à explorer à l'intérieur, rivières qui sont ses veines, maladies et parasites à soigner.
5. **Le lien** : le Titan réagit à ce qu'on fait sur son dos (déforestation, soins), et l'on peut influencer sa route.

## Développement

```
./gradlew runClient   # lance Minecraft avec le mod (joueur « Voyageur »), dans run/
./gradlew build       # compile et lance les tests ; le .jar est dans build/libs/
```

Sans terminal : `lancer-titan.sh` fait la même chose que `./gradlew runClient` et garde la fenêtre ouverte en cas d'erreur. On peut le lancer depuis un raccourci de bureau (`Terminal=true` dans un fichier `.desktop`).

Créer un nouveau monde : on apparaît directement sur le Titan.

- `src/main/java/fr/letitan/monde/FormeTitan.java` : la forme du Titan, bloc par bloc (testée seule, sans Minecraft).
- `src/main/java/fr/letitan/monde/GenerateurTitan.java` : le générateur de la dimension.
- `src/main/java/fr/letitan/Rythme.java` : le rythme des pas et du souffle, partagé entre serveur et client.
- `src/main/java/fr/letitan/LeTitan.java` : arrivée, pas, chute, commandes.
- `src/main/java/fr/letitan/voyage/` : la route du Titan (`Navigation`), le lien entre son dos et le monde d'en bas (`Repere`), l'état sauvegardé et envoyé aux joueurs.
- `src/main/java/fr/letitan/sol/CarteSol.java` : la carte du sol autour du Titan, calculée en tâche de fond et envoyée aux joueurs sur son dos.
- `src/main/java/fr/letitan/vie/Accroche.java` : où l'on touche une patte du Titan (qui te hisse) ; `Lianes.java` : les lianes de son flanc.
- `src/client/java/fr/letitan/client/RenduSol.java` et `RenduTitan.java` : le sol vu du dos, et le Titan vu du sol.
- `src/client/java/fr/letitan/client/mixin/` : la caméra qui bouge avec le Titan et voit loin, les nuages qui défilent, le passage d'un monde à l'autre sans écran de chargement.
- `src/main/resources/data/letitan/` : la dimension et son type (nuages sous le Titan, brume chaude).
