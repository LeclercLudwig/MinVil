# Format JSON d'une enquête (`minvil-enquete`, version 1)

Ce format fait le lien entre le générateur Python (`mystery.py`) et le mod Minecraft. Une enquête = un fichier JSON en UTF-8.

```
python3 -I export.py 42                 # affiche l'enquête de la seed 42
python3 -I export.py 0 1 2 --dir out/   # écrit out/enquete-0.json, etc.
python3 -I export.py --test             # vérifie le format sur 500 seeds
```

Exemples complets : [`exemples/enquete-0.json`](exemples/enquete-0.json) (avec une rumeur fausse) et [`exemples/enquete-1.json`](exemples/enquete-1.json) (sans).

## Principes

- **Deux parties bien séparées.** `public` contient tout ce que le joueur peut apprendre en jeu. `solution` contient la vérité. Le mod ne lit `solution` qu'au moment de juger une accusation et de l'expliquer.
- **Identifiants stables.** Villageois, lieux, métiers et motifs sont désignés par des identifiants en minuscules, sans accents, avec `_` à la place des espaces (`pecheur`, `eglise`, `terre_disputee`). Ils sont compatibles avec les identifiants de Minecraft.
- **Données d'abord, textes ensuite.** Les champs structurés suffisent pour jouer. `texts.fr` fournit en plus les phrases françaises déjà rédigées, pour qu'une première version du mod puisse les afficher telles quelles.
- **Version.** `format` vaut toujours `"minvil-enquete"`. `version` augmente à chaque changement incompatible ; le mod doit refuser une version qu'il ne connaît pas.

## Structure

```jsonc
{
  "format": "minvil-enquete",
  "version": 1,
  "seed": 0,                       // même seed = même enquête
  "public": { ... },
  "solution": { ... },
  "texts": { "fr": { ... } }
}
```

### `public`

| Champ | Type | Sens |
|---|---|---|
| `victim` | id | Le villageois tué. |
| `crime_location` | id de lieu | Où le corps a été retrouvé. |
| `clue.description` | texte | Ce que le joueur voit en fouillant le lieu du crime (description vague). |
| `clue.could_be` | liste d'ids de métier | Métiers qui auraient pu laisser un objet semblable. Toujours au moins deux. |
| `villagers` | liste | Tous les villageois, victime comprise, triés par nom. |

Chaque villageois :

| Champ | Type | Sens |
|---|---|---|
| `id` | id | Identifiant unique. |
| `name` | texte | Nom affiché. |
| `trade` | id de métier | Métier, visible par le joueur. |
| `minecraft_profession` | id Minecraft | Profession de villageois à utiliser en jeu (`minecraft:cartographer`…). |
| `alive` | booléen | `false` pour la victime. |
| `statement` | objet | Absent pour la victime. Ce que le villageois déclare quand on l'interroge. |

`statement` :

| Champ | Type | Sens |
|---|---|---|
| `location` | id de lieu | Où il dit avoir été pendant la nuit. Le coupable ment. |
| `seen` | liste d'ids | Qui il dit y avoir vu. |
| `rumors` | liste de `{about, motive}` | Rumeurs qu'il raconte : `about` aurait un motif `motive` contre la victime. **Certaines peuvent être fausses** ; rien ne les distingue ici. |
| `recognizes_clue` | id de métier ou `null` | S'il n'est pas `null`, le villageois reconnaît l'objet trouvé et dit de quel métier il vient. **À n'utiliser qu'une fois le lieu du crime fouillé.** |

### `solution`

| Champ | Type | Sens |
|---|---|---|
| `culprit` | id | Le coupable. |
| `red_herring` | id | La fausse piste : un vrai motif, mais un alibi vrai. |
| `motives` | `{id: motif}` | Les vrais motifs (coupable et fausse piste). |
| `true_locations` | `{id: lieu}` | Où chacun était vraiment, victime comprise. |
| `witnesses` | liste d'ids | Villageois présents au lieu que le coupable prétend avoir fréquenté. |
| `clue.trade`, `clue.object` | id, texte | Métier désigné par l'indice et nom exact de l'objet. |
| `false_rumor` | `{teller, about, motive}` ou `null` | La rumeur fausse, s'il y en a une. |
| `explanation.fr` | liste de textes | Explication de fin de partie. Dévoile tout. |

### `texts.fr`

| Champ | Type | Sens |
|---|---|---|
| `scene` | liste de textes | Présentation de l'enquête. |
| `examine` | texte | Message quand le joueur fouille le lieu du crime. |
| `statements` | `{id: {without_clue, with_clue}}` | Ce que dit chaque villageois vivant, avant et après la fouille. Seuls ceux qui reconnaissent l'objet ont une phrase de plus dans `with_clue`. |
| `locations` | `{id: texte}` | Lieux avec leur préposition (`"eglise": "à l'église"`). |
| `trades` | `{id: texte}` | Noms des métiers avec accents (`"pecheur": "pêcheur"`). |

## Ce que le mod doit respecter

1. Ne jamais afficher `solution` avant l'accusation.
2. Utiliser `statements[id].without_clue` tant que le lieu du crime n'a pas été fouillé, puis `with_clue`.
3. Juger l'accusation en comparant l'id accusé à `solution.culprit`.
4. Refuser un fichier dont `format` ou `version` est inconnu.

## Garanties

`python3 -I export.py --test` vérifie, sur 500 seeds :

- la partie `public` suffit à trouver le coupable, par la piste des alibis comme par celle de l'indice ;
- elle ne contient aucun champ de la solution, et `texts` ne dévoile rien ;
- tous les identifiants sont en ASCII et uniques ;
- le fichier survit à un aller-retour JSON sans perte.
