"""Export d'une enquête en JSON, pour le mod Minecraft.

    python3 -I export.py 42                 # affiche l'enquête de la seed 42
    python3 -I export.py 0 1 2 --dir out/   # écrit out/enquete-0.json, etc.
    python3 -I export.py --test             # vérifie le format sur 500 seeds

Le format est décrit dans FORMAT_JSON.md. Il sépare :
- `public` : tout ce que le joueur peut apprendre en jeu (le mod s'en sert
  pour faire parler les villageois) ;
- `solution` : la vérité, à n'utiliser que pour juger l'accusation et
  l'expliquer à la fin.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import unicodedata

# `python3 -I` n'ajoute pas le dossier du script aux chemins d'import.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from enquete import examine_text, explain, scene_text, statement  # noqa: E402
from mystery import (CLUE_TEXT, CLUE_VAGUE, LOCATION_AT, Case,  # noqa: E402
                     clue_experts, clue_group, generate)

FORMAT = "minvil-enquete"
VERSION = 1

# Métier du prototype -> profession de villageois dans Minecraft.
MINECRAFT_PROFESSION = {
    "fermier": "minecraft:farmer",
    "berger": "minecraft:shepherd",
    "boucher": "minecraft:butcher",
    "pêcheur": "minecraft:fisherman",
    "bibliothécaire": "minecraft:librarian",
    "prêtre": "minecraft:cleric",
    "tanneur": "minecraft:leatherworker",
    "forgeron": "minecraft:toolsmith",
    "cartographe": "minecraft:cartographer",
    "maçon": "minecraft:mason",
    "flécheur": "minecraft:fletcher",
}


def ident(text: str) -> str:
    """Identifiant stable : minuscules, sans accents, `_` pour les espaces."""
    text = unicodedata.normalize("NFD", text.lower())
    text = "".join(c for c in text if unicodedata.category(c) != "Mn")
    return "".join(c if c.isalnum() else "_" for c in text)


def to_json(case: Case) -> dict:
    vid = {n: ident(n) for n in case.npcs}
    experts = set(clue_experts(case))
    fr = case.false_rumor

    villagers = []
    for npc in case.npcs:
        v = {
            "id": vid[npc],
            "name": npc,
            "trade": ident(case.trades[npc]),
            "minecraft_profession": MINECRAFT_PROFESSION[case.trades[npc]],
            "alive": npc != case.victim,
        }
        if npc != case.victim:
            rumors = [{"about": vid[h], "motive": ident(case.motives[h])}
                      for h in case.rumors.get(npc, [])]
            if fr and fr["teller"] == npc:
                rumors.append({"about": vid[fr["target"]], "motive": ident(fr["motive"])})
            v["statement"] = {
                "location": ident(case.alibis[npc]),
                "seen": [vid[n] for n in case.seen[npc]],
                "rumors": rumors,
                # Ce qu'il dit quand on lui montre l'objet trouvé (sinon null).
                "recognizes_clue": ident(case.clue) if npc in experts else None,
            }
        villagers.append(v)

    texts_fr = {
        "scene": scene_text(case),
        "examine": examine_text(case),
        "statements": {
            vid[n]: {
                "without_clue": statement(case, n, False),
                "with_clue": statement(case, n, True),
            }
            for n in case.living
        },
        "locations": {ident(loc): LOCATION_AT[loc] for loc in LOCATION_AT},
        "trades": {ident(t): t for t in sorted({*case.trades.values(), *clue_group(case.clue)})},
    }

    return {
        "format": FORMAT,
        "version": VERSION,
        "seed": case.seed,
        "public": {
            "victim": vid[case.victim],
            "crime_location": ident(case.crime_location),
            "clue": {
                "description": CLUE_VAGUE[case.clue],
                "could_be": [ident(t) for t in clue_group(case.clue)],
            },
            "villagers": villagers,
        },
        "solution": {
            "culprit": vid[case.culprit],
            "red_herring": vid[case.herring],
            "motives": {vid[n]: ident(m) for n, m in case.motives.items()},
            "true_locations": {vid[n]: ident(loc) for n, loc in case.location_at_crime.items()},
            "witnesses": [vid[n] for n in case.witnesses],
            "clue": {"trade": ident(case.clue), "object": CLUE_TEXT[case.clue]},
            "false_rumor": None if not fr else {
                "teller": vid[fr["teller"]],
                "about": vid[fr["target"]],
                "motive": ident(fr["motive"]),
            },
            # Explication de fin de partie (dévoile la solution).
            "explanation": {"fr": explain(case)},
        },
        "texts": {"fr": texts_fr},
    }


# --- Vérification : la partie publique suffit à résoudre l'enquête ---------

def solve_public(public: dict) -> str | None:
    """Résout l'enquête avec la seule partie `public`, comme le ferait un
    joueur qui a tout fouillé et tout interrogé. Renvoie l'id du coupable si
    la piste des alibis et celle de l'indice concordent, sinon None."""
    alive = [v for v in public["villagers"] if v["alive"]]
    st = {v["id"]: v["statement"] for v in alive}
    trade = {v["id"]: v["trade"] for v in public["villagers"]}
    suspects = sorted({r["about"] for s in st.values() for r in s["rumors"]})

    by_alibi = []
    for s in suspects:
        others = [v for v in st if v != s and st[v]["location"] == st[s]["location"]]
        confirmed = any(s in st[v]["seen"] for v in others)
        contradicted = any(s not in st[v]["seen"] for v in others)
        if contradicted and not confirmed:
            by_alibi.append(s)

    recognized = {s["recognizes_clue"] for s in st.values() if s["recognizes_clue"]}
    by_clue = [s for s in suspects if trade[s] in recognized] if len(recognized) == 1 else []

    if len(by_alibi) == 1 and by_alibi == by_clue:
        return by_alibi[0]
    return None


def check_export(data: dict) -> list[str]:
    errors = []
    if data.get("format") != FORMAT or data.get("version") != VERSION:
        errors.append("format ou version inattendus")
    pub, sol = data["public"], data["solution"]
    ids = [v["id"] for v in pub["villagers"]]
    if len(set(ids)) != len(ids):
        errors.append("identifiants de villageois en double")
    for v in pub["villagers"]:
        if not all(c.isascii() and (c.isalnum() or c == "_") for c in v["id"] + v["trade"]):
            errors.append(f"identifiant non ASCII : {v['id']} / {v['trade']}")
    # La partie publique ne doit rien contenir de la solution en clair.
    public_text = json.dumps(pub, ensure_ascii=False)
    for word in ("culprit", "red_herring", "true_locations", "witnesses", "false_rumor"):
        if word in public_text:
            errors.append(f"la partie publique contient « {word} »")
    texts_text = json.dumps(data["texts"], ensure_ascii=False)
    if "explanation" in texts_text or "brouiller les pistes" in texts_text:
        errors.append("les textes généraux dévoilent la solution")
    if solve_public(pub) != sol["culprit"]:
        errors.append("la partie publique ne suffit pas à trouver le coupable")
    # Le texte doit pouvoir faire un aller-retour JSON sans perte.
    if json.loads(json.dumps(data, ensure_ascii=False)) != data:
        errors.append("l'export ne survit pas à un aller-retour JSON")
    return errors


def run_tests(seeds=range(500)) -> int:
    failures = 0
    for seed in seeds:
        errors = check_export(to_json(generate(seed)))
        if errors:
            failures += 1
            if failures <= 5:
                print(f"seed {seed} : {errors}")
    print(f"{len(seeds) - failures}/{len(seeds)} exports valides")
    return 1 if failures else 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Exporte des enquêtes en JSON")
    parser.add_argument("seeds", nargs="*", type=int, help="seeds à exporter")
    parser.add_argument("--dir", help="dossier où écrire un fichier par enquête")
    parser.add_argument("--test", action="store_true", help="vérifie le format sur 500 seeds")
    args = parser.parse_args()

    if args.test:
        return run_tests()
    if not args.seeds:
        parser.error("indique au moins une seed, ou --test")

    for seed in args.seeds:
        text = json.dumps(to_json(generate(seed)), ensure_ascii=False, indent=2) + "\n"
        if args.dir:
            os.makedirs(args.dir, exist_ok=True)
            path = os.path.join(args.dir, f"enquete-{seed}.json")
            with open(path, "w", encoding="utf-8") as f:
                f.write(text)
            print(path)
        else:
            sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
