"""Prototype « enquête villageoise » : génération d'un crime cohérent.

Principe : on décide d'abord la vérité (qui était où, qui a un motif, qui a
l'occasion), puis on en dérive les alibis et les témoignages. Le coupable ment
sur son lieu, et au moins un témoin doit pouvoir le contredire.

Le générateur est déterministe : même seed, même crime.

Lancer les tests :      python3 -I mystery.py
Afficher un cas :       python3 -I mystery.py --seed 0
"""

from __future__ import annotations

import random
import sys
from dataclasses import dataclass, field

LOCATIONS = [
    "forge", "moulin", "puits", "église",
    "marché", "taverne", "champs", "bergerie",
]

NAMES = [
    "Aldric", "Berthe", "Clovis", "Douce", "Enguerrand", "Fleur",
    "Gauvain", "Hersende", "Isembert", "Jehanne", "Lothaire", "Mahaut",
]

MOTIVES = ["dette", "jalousie", "héritage", "rancune", "terre disputée"]

MIN_NPCS = 5
MAX_NPCS = 8


@dataclass
class Case:
    seed: int
    npcs: list[str]
    victim: str
    culprit: str
    crime_location: str
    # Vérité : position réelle de chaque PNJ au moment du crime.
    location_at_crime: dict[str, str]
    # Vérité : motifs, sous la forme {suspect: motif contre la victime}.
    motives: dict[str, str]
    # Déclarations : lieu où chaque PNJ vivant prétend avoir été.
    alibis: dict[str, str]
    # Innocents qui se trouvaient au lieu revendiqué par le coupable et
    # peuvent donc affirmer qu'il n'y était pas.
    witnesses: list[str] = field(default_factory=list)

    @property
    def innocents(self) -> list[str]:
        return [n for n in self.npcs if n not in (self.victim, self.culprit)]


def generate(seed: int) -> Case:
    rng = random.Random(seed)

    npcs = rng.sample(NAMES, rng.randint(MIN_NPCS, MAX_NPCS))
    victim, culprit, *innocents = npcs
    if not innocents:
        raise ValueError("il faut au moins un innocent pour servir de témoin")

    crime_location = rng.choice(LOCATIONS)
    elsewhere = [loc for loc in LOCATIONS if loc != crime_location]

    # 1. Vérité : positions. Seuls la victime et le coupable sont sur le lieu
    #    du crime ; les innocents sont ailleurs (aucun n'a l'occasion).
    location_at_crime = {victim: crime_location, culprit: crime_location}
    for npc in innocents:
        location_at_crime[npc] = rng.choice(elsewhere)

    # 2. Vérité : motif. Seul le coupable en a un (la fausse piste viendra
    #    plus tard).
    motives = {culprit: rng.choice(MOTIVES)}

    # 3. Mensonge du coupable : un lieu différent du lieu du crime.
    lie = rng.choice(elsewhere)

    # 4. Correctif : garantir qu'au moins un innocent se trouvait réellement
    #    au lieu revendiqué par le coupable.
    _ensure_witness(rng, innocents, location_at_crime, lie)

    # 5. Dérivés de la vérité : alibis et témoins.
    alibis = {npc: location_at_crime[npc] for npc in innocents}
    alibis[culprit] = lie
    witnesses = [npc for npc in innocents if location_at_crime[npc] == lie]

    return Case(
        seed=seed,
        npcs=npcs,
        victim=victim,
        culprit=culprit,
        crime_location=crime_location,
        location_at_crime=location_at_crime,
        motives=motives,
        alibis=alibis,
        witnesses=witnesses,
    )


def _ensure_witness(rng, innocents, location_at_crime, lie):
    """Si aucun innocent n'est au lieu du mensonge, en déplacer un.

    Le PNJ choisi est un innocent (jamais la victime ni le coupable), et `lie`
    est différent du lieu du crime : sa position réelle reste donc cohérente
    et il n'acquiert pas d'occasion.
    """
    if any(location_at_crime[npc] == lie for npc in innocents):
        return
    location_at_crime[rng.choice(innocents)] = lie


def check(case: Case) -> list[str]:
    """Vérifie la cohérence d'un cas. Renvoie la liste des erreurs."""
    errors = []
    pos = case.location_at_crime

    # Distribution des rôles.
    if case.victim == case.culprit:
        errors.append("la victime est aussi le coupable")
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

    # Alibis des innocents justes.
    for npc in case.innocents:
        if case.alibis.get(npc) != pos[npc]:
            errors.append(f"alibi faux pour l'innocent {npc}")

    # Le coupable ment, et ce mensonge est contredit par au moins un témoin.
    lie = case.alibis.get(case.culprit)
    if lie is None or lie == pos[case.culprit]:
        errors.append("le coupable ne ment pas sur son lieu")
    expected_witnesses = [n for n in case.innocents if pos[n] == lie]
    if case.witnesses != expected_witnesses:
        errors.append("les témoins ne découlent pas de la vérité")
    if not expected_witnesses:
        errors.append("aucun témoin ne peut contredire le coupable")

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


TESTS = [
    test_case_is_consistent,
    test_generation_is_deterministic,
    test_culprit_lies_and_is_contradicted,
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
    lines = [
        f"Seed {case.seed}",
        f"Victime : {case.victim}, tuée à : {case.crime_location}",
        f"Coupable : {case.culprit} (motif : {case.motives[case.culprit]})",
        "",
        "PNJ           réel        déclaré",
    ]
    for npc in case.npcs:
        claimed = case.alibis.get(npc, "-")
        lines.append(f"{npc:<13} {case.location_at_crime[npc]:<11} {claimed}")
    lines.append("")
    lines.append(f"Témoins contre {case.culprit} : {', '.join(case.witnesses)}")
    return "\n".join(lines)


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "--seed":
        print(describe(generate(int(sys.argv[2]))))
    else:
        sys.exit(run_tests())
