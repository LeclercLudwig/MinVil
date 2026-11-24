"""Prototype « enquête villageoise » : génération d'un crime cohérent.

Principe : on décide d'abord la vérité (qui était où, qui a un motif, qui a
l'occasion), puis on en dérive les alibis, les témoignages et les rumeurs.

- Le coupable ment sur son lieu, et au moins un témoin peut le contredire.
- Une fausse piste a aussi un motif, mais son alibi est vrai et au moins un
  autre villageois peut le confirmer.
- Un indice physique, laissé sur le lieu du crime, désigne le métier du
  coupable. Au moins un autre villageois exerce ce métier, mais jamais la
  fausse piste : l'indice et les alibis mènent au coupable chacun de leur côté.

Le générateur est déterministe : même seed, même crime.

Lancer les tests :      python3 -I mystery.py
Afficher un cas :       python3 -I mystery.py --seed 0
Jouer :                 python3 -I enquete.py
"""

from __future__ import annotations

import argparse
import random
import sys
from dataclasses import dataclass

# Lieu -> forme « à + article », pour les phrases.
LOCATION_AT = {
    "forge": "à la forge",
    "moulin": "au moulin",
    "puits": "au puits",
    "église": "à l'église",
    "marché": "au marché",
    "taverne": "à la taverne",
    "champs": "aux champs",
    "bergerie": "à la bergerie",
}
LOCATIONS = list(LOCATION_AT)

NAMES = [
    "Aldric", "Berthe", "Clovis", "Douce", "Enguerrand", "Fleur",
    "Gauvain", "Hersende", "Isembert", "Jehanne", "Lothaire", "Mahaut",
]

# Motif -> phrase de rumeur ({who} a le motif, {victim} est la victime).
MOTIVE_TEXT = {
    "dette": "{who} devait de l'argent à {victim}",
    "jalousie": "{who} jalousait {victim}",
    "héritage": "{who} comptait hériter de {victim}",
    "rancune": "{who} en voulait à {victim} depuis longtemps",
    "terre disputée": "{who} se disputait une terre avec {victim}",
}
MOTIVES = list(MOTIVE_TEXT)

# Métier -> objet qu'un villageois de ce métier peut laisser derrière lui.
CLUE_TEXT = {
    "fermier": "des brins de paille",
    "berger": "des touffes de laine",
    "boucher": "un tablier taché de sang",
    "pêcheur": "des écailles de poisson",
    "bibliothécaire": "une page arrachée d'un livre",
    "prêtre": "des gouttes de cire de cierge",
    "tanneur": "une lanière de cuir",
    "forgeron": "un clou de fer",
    "cartographe": "un morceau de carte",
    "maçon": "de la poussière de pierre",
    "flécheur": "une plume d'oie",
}
TRADES = list(CLUE_TEXT)

MIN_NPCS = 5
MAX_NPCS = 8


@dataclass
class Case:
    seed: int
    npcs: list[str]  # ordre alphabétique : ne trahit aucun rôle
    victim: str
    culprit: str
    herring: str  # fausse piste : motif, mais alibi vrai
    crime_location: str
    # Vérité : position réelle de chaque PNJ au moment du crime.
    location_at_crime: dict[str, str]
    # Vérité : motifs, sous la forme {suspect: motif contre la victime}.
    motives: dict[str, str]
    # Déclarations : lieu où chaque PNJ vivant prétend avoir été...
    alibis: dict[str, str]
    # ... et les villageois qu'il dit y avoir vus.
    seen: dict[str, list[str]]
    # Rumeurs : {villageois qui la raconte: [PNJ dont il révèle le motif]}.
    rumors: dict[str, list[str]]
    # Innocents présents au lieu revendiqué par le coupable.
    witnesses: list[str]
    # Public : métier de chaque PNJ.
    trades: dict[str, str]
    # Indice physique trouvé sur le lieu du crime : le métier qu'il désigne.
    clue: str

    @property
    def innocents(self) -> list[str]:
        return [n for n in self.npcs if n not in (self.victim, self.culprit)]

    @property
    def living(self) -> list[str]:
        return [n for n in self.npcs if n != self.victim]


def generate(seed: int) -> Case:
    rng = random.Random(seed)

    chosen = rng.sample(NAMES, rng.randint(MIN_NPCS, MAX_NPCS))
    if len(chosen) < 5:
        raise ValueError(
            "il faut au moins trois innocents : un témoin, une fausse piste "
            "et quelqu'un pour la disculper"
        )
    victim, culprit, witness, herring, alibi_partner, *others = chosen

    crime_location = rng.choice(LOCATIONS)
    elsewhere = [loc for loc in LOCATIONS if loc != crime_location]
    lie = rng.choice(elsewhere)
    herring_location = rng.choice([loc for loc in elsewhere if loc != lie])

    # 1. Vérité : positions. Seuls la victime et le coupable sont sur le lieu
    #    du crime. Les rôles clés sont placés d'office, ce qui garantit :
    #    - un témoin au lieu revendiqué par le coupable ;
    #    - quelqu'un qui confirme l'alibi de la fausse piste.
    location_at_crime = {
        victim: crime_location,
        culprit: crime_location,
        witness: lie,
        herring: herring_location,
        alibi_partner: herring_location,
    }
    for npc in others:
        location_at_crime[npc] = rng.choice(elsewhere)

    # 2. Vérité : motifs. Le coupable et la fausse piste en ont un.
    motives = {culprit: rng.choice(MOTIVES), herring: rng.choice(MOTIVES)}

    # 3. Dérivés de la vérité.
    innocents = [witness, herring, alibi_partner, *others]
    alibis = {npc: location_at_crime[npc] for npc in innocents}
    alibis[culprit] = lie
    seen = {
        npc: sorted(o for o in innocents
                    if o != npc and location_at_crime[o] == location_at_crime[npc])
        for npc in innocents
    }
    seen[culprit] = []  # le coupable n'ose nommer personne
    witnesses = sorted(n for n in innocents if location_at_crime[n] == lie)

    # 4. Rumeurs : chaque motif est connu d'au moins un autre innocent.
    rumors: dict[str, list[str]] = {}
    for holder in (culprit, herring):
        teller = rng.choice([n for n in innocents if n != holder])
        rumors.setdefault(teller, []).append(holder)

    # 5. Métiers et indice physique. Le coupable laisse un objet de son
    #    métier ; un autre innocent (pas la fausse piste) partage ce métier,
    #    pour que l'indice seul ne suffise pas. La victime et la fausse piste
    #    ont un autre métier.
    clue = rng.choice(TRADES)
    other_trades = [t for t in TRADES if t != clue]
    sharer = rng.choice([n for n in innocents if n != herring])
    trades = {npc: rng.choice(TRADES) for npc in innocents if npc not in (herring, sharer)}
    trades[culprit] = clue
    trades[sharer] = clue
    trades[herring] = rng.choice(other_trades)
    trades[victim] = rng.choice(other_trades)

    return Case(
        seed=seed,
        npcs=sorted(chosen),
        victim=victim,
        culprit=culprit,
        herring=herring,
        crime_location=crime_location,
        location_at_crime=location_at_crime,
        motives=motives,
        alibis=alibis,
        seen=seen,
        rumors=rumors,
        witnesses=witnesses,
        trades=trades,
        clue=clue,
    )


def _suspects(case: Case) -> list[str]:
    """Villageois dont une rumeur révèle le motif."""
    return sorted({h for holders in case.rumors.values() for h in holders})


def solve_by_alibi(case: Case) -> str | None:
    """Piste des alibis, avec ce que le joueur peut apprendre.

    Un alibi est contredit si quelqu'un qui dit avoir été au même endroit n'y
    a pas vu le suspect, et confirmé si quelqu'un l'y a vu. Le coupable est
    l'unique suspect dont l'alibi est contredit et jamais confirmé ; None
    sinon.
    """
    suspects = _suspects(case)
    guilty = []
    for s in suspects:
        others = [v for v in case.living
                  if v != s and case.alibis[v] == case.alibis[s]]
        confirmed = any(s in case.seen[v] for v in others)
        contradicted = any(s not in case.seen[v] for v in others)
        if contradicted and not confirmed:
            guilty.append(s)
    return guilty[0] if len(guilty) == 1 else None


def solve_by_clue(case: Case) -> str | None:
    """Piste de l'indice : l'unique suspect qui exerce le métier désigné."""
    guilty = [s for s in _suspects(case) if case.trades[s] == case.clue]
    return guilty[0] if len(guilty) == 1 else None


def solve(case: Case) -> str | None:
    """Résout l'enquête si les deux pistes désignent la même personne."""
    by_alibi = solve_by_alibi(case)
    return by_alibi if by_alibi == solve_by_clue(case) else None


def check(case: Case) -> list[str]:
    """Vérifie la cohérence d'un cas. Renvoie la liste des erreurs."""
    errors = []
    pos = case.location_at_crime

    # Distribution des rôles.
    if len({case.victim, case.culprit, case.herring}) != 3:
        errors.append("victime, coupable et fausse piste ne sont pas distincts")
    if set(pos) != set(case.npcs):
        errors.append("tous les PNJ n'ont pas une position au moment du crime")
        return errors

    # Position cohérente.
    if pos[case.victim] != case.crime_location:
        errors.append("la victime n'est pas sur le lieu du crime")
    if pos[case.culprit] != case.crime_location:
        errors.append("le coupable n'est pas sur le lieu du crime")

    # Un seul coupable : motif + occasion.
    suspects = [
        npc for npc in case.npcs
        if npc != case.victim
        and npc in case.motives
        and pos[npc] == case.crime_location
    ]
    if suspects != [case.culprit]:
        errors.append(f"suspects avec motif et occasion : {suspects}")

    # Déclarations des innocents justes.
    for npc in case.innocents:
        if case.alibis.get(npc) != pos[npc]:
            errors.append(f"alibi faux pour l'innocent {npc}")
        truly_seen = sorted(o for o in case.innocents
                            if o != npc and pos[o] == pos[npc])
        if case.seen.get(npc) != truly_seen:
            errors.append(f"l'innocent {npc} ment sur qui il a vu")

    # Le coupable ment, et ce mensonge est contredit par au moins un témoin.
    lie = case.alibis.get(case.culprit)
    if lie is None or lie == pos[case.culprit]:
        errors.append("le coupable ne ment pas sur son lieu")
    expected_witnesses = sorted(n for n in case.innocents if pos[n] == lie)
    if case.witnesses != expected_witnesses:
        errors.append("les témoins ne découlent pas de la vérité")
    if not expected_witnesses:
        errors.append("aucun témoin ne peut contredire le coupable")

    # Fausse piste : motif, alibi vrai, confirmé par quelqu'un.
    if case.herring not in case.motives:
        errors.append("la fausse piste n'a pas de motif")
    if pos[case.herring] == case.crime_location:
        errors.append("la fausse piste a l'occasion")
    if not any(case.herring in case.seen[n] for n in case.innocents):
        errors.append("personne ne confirme l'alibi de la fausse piste")

    # Rumeurs : chaque motif est révélé par quelqu'un d'autre.
    revealed = {h for holders in case.rumors.values() for h in holders}
    if revealed != set(case.motives):
        errors.append("les rumeurs ne révèlent pas exactement les motifs")
    if any(teller in holders for teller, holders in case.rumors.items()):
        errors.append("un villageois révèle son propre motif")

    # Indice physique : désigne le métier du coupable, partagé par un autre
    # villageois, mais pas par la fausse piste.
    if set(case.trades) != set(case.npcs):
        errors.append("tous les PNJ n'ont pas de métier")
    elif case.trades[case.culprit] != case.clue:
        errors.append("l'indice ne désigne pas le métier du coupable")
    else:
        holders = [n for n in case.living if case.trades[n] == case.clue]
        if len(holders) < 2:
            errors.append("l'indice désigne directement le coupable")
        if case.trades[case.herring] == case.clue:
            errors.append("la fausse piste a le métier désigné par l'indice")

    # L'enquête a de vrais choix et une seule solution.
    if len(revealed) < 2:
        errors.append("un seul suspect : l'enquête est triviale")
    if solve_by_alibi(case) != case.culprit:
        errors.append("les alibis ne désignent pas le coupable")
    if set(case.trades) == set(case.npcs) and solve_by_clue(case) != case.culprit:
        errors.append("l'indice ne désigne pas le coupable")

    return errors


# --- Tests -----------------------------------------------------------------

SEEDS = range(500)


def test_case_is_consistent():
    for seed in SEEDS:
        errors = check(generate(seed))
        assert not errors, f"seed {seed} : {errors}"


def test_generation_is_deterministic():
    for seed in SEEDS:
        assert generate(seed) == generate(seed), f"seed {seed} non déterministe"


def test_culprit_lies_and_is_contradicted():
    for seed in SEEDS:
        case = generate(seed)
        lie = case.alibis[case.culprit]
        assert lie != case.crime_location, f"seed {seed} : mensonge = lieu du crime"
        assert case.witnesses, f"seed {seed} : aucun témoin"
        for w in case.witnesses:
            assert w not in (case.victim, case.culprit), f"seed {seed} : témoin {w}"
            assert case.location_at_crime[w] == lie, f"seed {seed} : témoin {w} absent"
            assert case.culprit not in case.seen[w], f"seed {seed} : {w} a vu le coupable"


def test_red_herring_has_confirmed_alibi():
    for seed in SEEDS:
        case = generate(seed)
        h = case.herring
        assert h in case.motives, f"seed {seed} : fausse piste sans motif"
        assert case.alibis[h] == case.location_at_crime[h], f"seed {seed} : alibi faux"
        assert case.alibis[h] != case.crime_location, f"seed {seed} : fausse piste sur place"
        partners = [n for n in case.innocents if h in case.seen[n]]
        assert partners, f"seed {seed} : alibi de la fausse piste non confirmé"


def test_case_is_solvable_with_testimonies():
    for seed in SEEDS:
        case = generate(seed)
        assert len(case.motives) >= 2, f"seed {seed} : un seul suspect"
        assert solve(case) == case.culprit, f"seed {seed} : solution incorrecte"


def test_clue_points_to_culprit():
    for seed in SEEDS:
        case = generate(seed)
        holders = [n for n in case.living if case.trades[n] == case.clue]
        assert case.culprit in holders, f"seed {seed} : indice sans lien avec le coupable"
        assert len(holders) >= 2, f"seed {seed} : l'indice suffit à lui seul"
        assert case.herring not in holders, f"seed {seed} : l'indice vise la fausse piste"
        assert solve_by_clue(case) == case.culprit, f"seed {seed} : piste de l'indice"
        assert solve_by_alibi(case) == case.culprit, f"seed {seed} : piste des alibis"


TESTS = [
    test_case_is_consistent,
    test_generation_is_deterministic,
    test_culprit_lies_and_is_contradicted,
    test_red_herring_has_confirmed_alibi,
    test_case_is_solvable_with_testimonies,
    test_clue_points_to_culprit,
]


def run_tests() -> int:
    failures = 0
    for test in TESTS:
        try:
            test()
        except AssertionError as exc:
            failures += 1
            print(f"ÉCHEC  {test.__name__} : {exc}")
        else:
            print(f"OK     {test.__name__}")
    print(f"\n{len(TESTS) - failures}/{len(TESTS)} tests réussis")
    return 1 if failures else 0


def describe(case: Case) -> str:
    """Vue de débogage : affiche toute la vérité d'un cas."""
    lines = [
        f"Seed {case.seed}",
        f"Victime : {case.victim} (corps retrouvé {LOCATION_AT[case.crime_location]})",
        f"Coupable : {case.culprit} (motif : {case.motives[case.culprit]})",
        f"Fausse piste : {case.herring} (motif : {case.motives[case.herring]})",
        f"Indice : {CLUE_TEXT[case.clue]} (métier : {case.clue})",
        "",
        "PNJ           métier          réel        déclaré     a vu",
    ]
    for npc in case.npcs:
        claimed = case.alibis.get(npc, "-")
        seen = ", ".join(case.seen.get(npc, [])) or "-"
        lines.append(f"{npc:<13} {case.trades[npc]:<15} "
                     f"{case.location_at_crime[npc]:<11} {claimed:<11} {seen}")
    lines.append("")
    lines.append(f"Témoins contre {case.culprit} : {', '.join(case.witnesses)}")
    for teller, holders in case.rumors.items():
        lines.append(f"{teller} révèle le motif de : {', '.join(holders)}")
    return "\n".join(lines)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Générateur d'enquête villageoise")
    parser.add_argument("--seed", type=int, help="affiche le cas de cette seed au lieu de lancer les tests")
    args = parser.parse_args()
    if args.seed is not None:
        print(describe(generate(args.seed)))
    else:
        sys.exit(run_tests())
