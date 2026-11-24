"""Version jouable dans le terminal de l'enquête villageoise.

    python3 -I enquete.py          # crime tiré au hasard
    python3 -I enquete.py 42       # rejouer le crime de la seed 42

Fouille le lieu du crime, interroge les villageois, recoupe leurs
déclarations, puis accuse quelqu'un.
"""

from __future__ import annotations

import os
import random
import sys
import unicodedata

# `python3 -I` n'ajoute pas le dossier du script aux chemins d'import.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from mystery import (CLUE_TEXT, CLUE_VAGUE, LOCATION_AT, MOTIVE_TEXT, Case,
                     clue_experts, generate)

HELP = (
    "Tape un numéro ou un nom pour interroger, « examiner » pour fouiller le "
    "lieu du crime, « carnet » pour relire tes notes, « accuser » pour "
    "désigner le coupable, « quitter » pour abandonner."
)


def normalize(text: str) -> str:
    text = unicodedata.normalize("NFD", text.strip().lower())
    return "".join(c for c in text if unicodedata.category(c) != "Mn")


def join(names: list[str]) -> str:
    if len(names) <= 1:
        return "".join(names)
    return ", ".join(names[:-1]) + " et " + names[-1]


def que(name: str) -> str:
    return "qu'" + name if normalize(name)[0] in "aeiouy" else "que " + name


def statement(case: Case, npc: str, clue_found: bool = False) -> list[str]:
    """Ce que dit un villageois quand on l'interroge.

    Une fois l'indice trouvé, on le lui montre : s'il est du métier, il le
    reconnaît.
    """
    place = LOCATION_AT[case.alibis[npc]]
    seen = case.seen[npc]
    lines = [f"J'étais {place}."]
    lines.append(f"J'y ai vu {join(seen)}." if seen else "Je n'y ai vu personne.")
    for holder in case.rumors.get(npc, []):
        motive = MOTIVE_TEXT[case.motives[holder]].format(who=holder, victim=case.victim)
        lines.append(f"Et puis on raconte {que(motive)}.")
    if clue_found and npc in clue_experts(case):
        lines.append(f"L'objet trouvé près du corps ? Je reconnais "
                     f"{CLUE_TEXT[case.clue]}, on en trouve chez tous les "
                     f"{case.clue}s.")
    return lines


def ask(prompt: str) -> str | None:
    try:
        return input(prompt)
    except EOFError:
        print()
        return None


def find_npc(case: Case, answer: str, choices: list[str]) -> str | None:
    answer = normalize(answer)
    if answer.isdigit() and 1 <= int(answer) <= len(choices):
        return choices[int(answer) - 1]
    for npc in case.npcs:
        if normalize(npc) == answer:
            return npc
    return None


def explain(case: Case) -> list[str]:
    lie = LOCATION_AT[case.alibis[case.culprit]]
    h = case.herring
    partners = [n for n in case.innocents if h in case.seen[n]]
    holders = [n for n in case.living if case.trades[n] == case.clue]
    experts = clue_experts(case)
    recognized = "l'a" if len(experts) == 1 else "l'ont"
    motive = MOTIVE_TEXT[case.motives[case.culprit]].format(who=case.culprit, victim=case.victim)
    return [
        f"{case.culprit} prétendait être {lie} et y avoir vu "
        f"{join(case.seen[case.culprit])}, mais {join(case.witnesses)} "
        f"y {'était' if len(case.witnesses) == 1 else 'étaient'} sans y voir "
        f"{case.culprit}. Mobile : {motive}.",
        f"{h} avait aussi un motif, mais {join(partners)} "
        f"{'confirme' if len(partners) == 1 else 'confirment'} sa présence "
        f"{LOCATION_AT[case.alibis[h]]}.",
        f"L'objet trouvé près du corps, {CLUE_VAGUE[case.clue]}, était "
        f"{CLUE_TEXT[case.clue]}, comme {join(experts)} "
        f"{recognized} reconnu. Il désignait le "
        f"métier de {case.clue} : {join(holders)}. Parmi eux, {case.culprit} "
        f"était la seule personne avec un motif.",
    ]


def play(seed: int) -> int:
    case = generate(seed)
    living = case.living
    notes: dict[str, list[str]] = {}
    clue_found = False
    crime_place = LOCATION_AT[case.crime_location]
    clue_line = f"Près du corps, {crime_place} : {CLUE_VAGUE[case.clue]}."

    print(f"\n=== Enquête n° {seed} ===\n")
    print(f"Ce matin, on a retrouvé le corps de {case.victim} "
          f"{LOCATION_AT[case.crime_location]}. Le crime a eu lieu dans la nuit.")
    print("Les villageois ont tous une version de la soirée. À toi de trouver qui ment.\n")
    print(HELP)

    while True:
        print()
        for i, npc in enumerate(living, 1):
            mark = "  (interrogé)" if npc in notes else ""
            print(f"  {i}. {npc}, {case.trades[npc]}{mark}")
        answer = ask("> ")
        if answer is None:
            return 1
        cmd = normalize(answer)

        if cmd in ("q", "quitter"):
            print(f"Enquête abandonnée. Le coupable était {case.culprit}.")
            return 1

        if cmd in ("c", "carnet"):
            if not notes and not clue_found:
                print("Ton carnet est vide.")
            if clue_found:
                print(clue_line)
            for npc, lines in notes.items():
                print(f"{npc} : {' '.join(lines)}")
            continue

        if cmd in ("a", "accuser"):
            target = ask("Qui accuses-tu ? ")
            if target is None:
                return 1
            accused = find_npc(case, target, living)
            if accused is None:
                print("Personne de ce nom au village.")
                continue
            print()
            if accused == case.culprit:
                print(f"Bien vu ! {accused} avoue le crime.")
            else:
                print(f"Raté : {accused} n'y est pour rien. Le coupable était {case.culprit}.")
            for line in explain(case):
                print(line)
            print(f"\nVillageois interrogés : {len(notes)} sur {len(living)}.")
            return 0 if accused == case.culprit else 2

        if cmd in ("e", "examiner", "fouiller"):
            clue_found = True
            print(f"Tu fouilles le lieu du crime, {crime_place}. Près du corps, "
                  f"tu trouves {CLUE_VAGUE[case.clue]}. Difficile de dire d'où "
                  f"ça vient : montre-le aux villageois en les interrogeant, "
                  f"quelqu'un du métier saura le reconnaître.")
            continue

        if cmd in ("aide", "?", "h"):
            print(HELP)
            continue

        npc = find_npc(case, answer, living)
        if npc is None:
            print("Commande inconnue. " + HELP)
            continue
        notes[npc] = statement(case, npc, clue_found)
        print(f"{npc} : « {' '.join(notes[npc])} »")


def main(argv: list[str]) -> int:
    if len(argv) > 1:
        try:
            seed = int(argv[1])
        except ValueError:
            print(f"Seed invalide : {argv[1]!r} (il faut un nombre entier).")
            return 64
    else:
        seed = random.randrange(1_000_000)
    return play(seed)


if __name__ == "__main__":
    sys.exit(main(sys.argv))
