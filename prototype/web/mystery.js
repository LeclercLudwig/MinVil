/*
 * Portage JavaScript de prototype/mystery.py (génération du crime).
 *
 * Il reproduit exactement le générateur aléatoire de Python
 * (random.Random : Mersenne Twister, randbelow, choice, randint, sample),
 * donc une même seed donne le même crime que `python3 -I enquete.py <seed>`.
 *
 * Toute modification de generate() dans mystery.py doit être reportée ici,
 * puis vérifiée avec :  node prototype/web/verifier.js
 */
(function (root) {
  "use strict";

  // --- Hasard identique à random.Random de Python --------------------------

  function PyRandom(seed) {
    const N = 624;
    const mt = new Uint32Array(N);
    let mti = N + 1;

    function initGenrand(s) {
      mt[0] = s >>> 0;
      for (mti = 1; mti < N; mti++) {
        const prev = mt[mti - 1] ^ (mt[mti - 1] >>> 30);
        mt[mti] = (Math.imul(1812433253, prev) + mti) >>> 0;
      }
    }

    function initByArray(key) {
      initGenrand(19650218);
      let i = 1, j = 0;
      for (let k = Math.max(N, key.length); k; k--) {
        const prev = mt[i - 1] ^ (mt[i - 1] >>> 30);
        mt[i] = ((mt[i] ^ Math.imul(prev, 1664525)) + key[j] + j) >>> 0;
        i++; j++;
        if (i >= N) { mt[0] = mt[N - 1]; i = 1; }
        if (j >= key.length) j = 0;
      }
      for (let k = N - 1; k; k--) {
        const prev = mt[i - 1] ^ (mt[i - 1] >>> 30);
        mt[i] = ((mt[i] ^ Math.imul(prev, 1566083941)) - i) >>> 0;
        i++;
        if (i >= N) { mt[0] = mt[N - 1]; i = 1; }
      }
      mt[0] = 0x80000000;
    }

    function genrandUint32() {
      let y;
      if (mti >= N) {
        let kk;
        for (kk = 0; kk < N - 397; kk++) {
          y = (mt[kk] & 0x80000000) | (mt[kk + 1] & 0x7fffffff);
          mt[kk] = mt[kk + 397] ^ (y >>> 1) ^ (y & 1 ? 0x9908b0df : 0);
        }
        for (; kk < N - 1; kk++) {
          y = (mt[kk] & 0x80000000) | (mt[kk + 1] & 0x7fffffff);
          mt[kk] = mt[kk + (397 - N)] ^ (y >>> 1) ^ (y & 1 ? 0x9908b0df : 0);
        }
        y = (mt[N - 1] & 0x80000000) | (mt[0] & 0x7fffffff);
        mt[N - 1] = mt[396] ^ (y >>> 1) ^ (y & 1 ? 0x9908b0df : 0);
        mti = 0;
      }
      y = mt[mti++];
      y ^= y >>> 11;
      y ^= (y << 7) & 0x9d2c5680;
      y ^= (y << 15) & 0xefc60000;
      y ^= y >>> 18;
      return y >>> 0;
    }

    // Python découpe |seed| en mots de 32 bits, poids faible d'abord.
    let n = BigInt(seed);
    if (n < 0n) n = -n;
    const key = [];
    while (n > 0n) { key.push(Number(n & 0xffffffffn)); n >>= 32n; }
    if (key.length === 0) key.push(0);
    initByArray(key);

    function getrandbits(k) { // 1 <= k <= 32
      return genrandUint32() >>> (32 - k);
    }
    function randbelow(n) {
      const k = 32 - Math.clz32(n); // n.bit_length()
      let r = getrandbits(k);
      while (r >= n) r = getrandbits(k);
      return r;
    }
    return {
      getrandbits,
      randint(a, b) { return a + randbelow(b - a + 1); },
      choice(seq) {
        if (!seq.length) throw new Error("choice() sur une liste vide");
        return seq[randbelow(seq.length)];
      },
      sample(population, k) {
        // Branche « pool » de random.sample, la seule utilisée ici
        // (population <= 21 éléments).
        const n = population.length;
        let setsize = 21;
        if (k > 5) setsize += 4 ** Math.ceil(Math.log(k * 3) / Math.log(4));
        if (n > setsize) throw new Error("sample() : cas non porté");
        const pool = population.slice();
        const result = [];
        for (let i = 0; i < k; i++) {
          const j = randbelow(n - i);
          result.push(pool[j]);
          pool[j] = pool[n - i - 1];
        }
        return result;
      },
    };
  }

  // --- Données (mêmes listes, même ordre que mystery.py) --------------------

  const LOCATION_AT = {
    "forge": "à la forge",
    "moulin": "au moulin",
    "puits": "au puits",
    "église": "à l'église",
    "marché": "au marché",
    "taverne": "à la taverne",
    "champs": "aux champs",
    "bergerie": "à la bergerie",
  };
  const LOCATIONS = Object.keys(LOCATION_AT);

  const NAMES = [
    "Aldric", "Berthe", "Clovis", "Douce", "Enguerrand", "Fleur",
    "Gauvain", "Hersende", "Isembert", "Jehanne", "Lothaire", "Mahaut",
  ];

  const MOTIVE_TEXT = {
    "dette": "{who} devait de l'argent à {victim}",
    "jalousie": "{who} jalousait {victim}",
    "héritage": "{who} comptait hériter de {victim}",
    "rancune": "{who} en voulait à {victim} depuis longtemps",
    "terre disputée": "{who} se disputait une terre avec {victim}",
  };
  const MOTIVES = Object.keys(MOTIVE_TEXT);

  const CLUE_TEXT = {
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
  };
  const TRADES = Object.keys(CLUE_TEXT);

  const MIN_NPCS = 5;
  const MAX_NPCS = 8;

  // Tri identique à sorted() de Python sur des chaînes (ordre des points de code).
  function pySorted(list) {
    return list.slice().sort((a, b) => (a < b ? -1 : a > b ? 1 : 0));
  }

  // --- Génération : même ordre de tirages que generate() en Python ----------

  function generate(seed) {
    const rng = PyRandom(seed);

    const chosen = rng.sample(NAMES, rng.randint(MIN_NPCS, MAX_NPCS));
    const [victim, culprit, witness, herring, alibiPartner, ...others] = chosen;

    const crimeLocation = rng.choice(LOCATIONS);
    const elsewhere = LOCATIONS.filter((l) => l !== crimeLocation);
    const lie = rng.choice(elsewhere);
    const herringLocation = rng.choice(elsewhere.filter((l) => l !== lie));

    const locationAtCrime = {
      [victim]: crimeLocation,
      [culprit]: crimeLocation,
      [witness]: lie,
      [herring]: herringLocation,
      [alibiPartner]: herringLocation,
    };
    for (const npc of others) locationAtCrime[npc] = rng.choice(elsewhere);

    const motives = {};
    motives[culprit] = rng.choice(MOTIVES);
    motives[herring] = rng.choice(MOTIVES);

    const innocents = [witness, herring, alibiPartner, ...others];
    const alibis = {};
    for (const npc of innocents) alibis[npc] = locationAtCrime[npc];
    alibis[culprit] = lie;
    const seen = {};
    for (const npc of innocents) {
      seen[npc] = pySorted(innocents.filter(
        (o) => o !== npc && locationAtCrime[o] === locationAtCrime[npc]));
    }
    const witnesses = pySorted(innocents.filter((n) => locationAtCrime[n] === lie));

    const rumors = {};
    for (const holder of [culprit, herring]) {
      const teller = rng.choice(innocents.filter((n) => n !== holder));
      (rumors[teller] = rumors[teller] || []).push(holder);
    }

    const clue = rng.choice(TRADES);
    const otherTrades = TRADES.filter((t) => t !== clue);
    const sharer = rng.choice(innocents.filter((n) => n !== herring));
    const trades = {};
    for (const npc of innocents) {
      if (npc !== herring && npc !== sharer) trades[npc] = rng.choice(TRADES);
    }
    trades[culprit] = clue;
    trades[sharer] = clue;
    trades[herring] = rng.choice(otherTrades);
    trades[victim] = rng.choice(otherTrades);

    // Le coupable dit avoir vu un villageois vraiment présent au lieu de
    // son mensonge (tiré en dernier, comme en Python).
    seen[culprit] = [rng.choice(witnesses)];

    const npcs = pySorted(chosen);
    return {
      seed, npcs, victim, culprit, herring,
      crime_location: crimeLocation,
      location_at_crime: locationAtCrime,
      motives, alibis, seen, rumors, witnesses, trades, clue,
      living: npcs.filter((n) => n !== victim),
      innocents: npcs.filter((n) => n !== victim && n !== culprit),
    };
  }

  // --- Textes : mêmes phrases que enquete.py --------------------------------

  function join(names) {
    if (names.length <= 1) return names.join("");
    return names.slice(0, -1).join(", ") + " et " + names[names.length - 1];
  }

  function que(text) {
    const first = text.normalize("NFD")[0].toLowerCase();
    return ("aeiouy".includes(first) ? "qu'" : "que ") + text;
  }

  function motiveText(c, holder) {
    return MOTIVE_TEXT[c.motives[holder]]
      .replace("{who}", holder).replace("{victim}", c.victim);
  }

  function statement(c, npc) {
    const lines = [`J'étais ${LOCATION_AT[c.alibis[npc]]}.`];
    const seen = c.seen[npc];
    lines.push(seen.length ? `J'y ai vu ${join(seen)}.` : "Je n'y ai vu personne.");
    for (const holder of c.rumors[npc] || []) {
      lines.push(`Et puis on raconte ${que(motiveText(c, holder))}.`);
    }
    return lines;
  }

  function explain(c) {
    const lie = LOCATION_AT[c.alibis[c.culprit]];
    const h = c.herring;
    const partners = c.innocents.filter((n) => c.seen[n].includes(h));
    const holders = c.living.filter((n) => c.trades[n] === c.clue);
    const w = c.witnesses;
    return [
      `${c.culprit} prétendait être ${lie} et y avoir vu ${join(c.seen[c.culprit])}, ` +
        `mais ${join(w)} ` +
        `y ${w.length === 1 ? "était" : "étaient"} sans y voir ${c.culprit}. ` +
        `Mobile : ${motiveText(c, c.culprit)}.`,
      `${h} avait aussi un motif, mais ${join(partners)} ` +
        `${partners.length === 1 ? "confirme" : "confirment"} sa présence ` +
        `${LOCATION_AT[c.alibis[h]]}.`,
      `L'indice, ${CLUE_TEXT[c.clue]}, désignait le métier de ${c.clue} : ` +
        `${join(holders)}. Parmi eux, ${c.culprit} était la seule personne avec un motif.`,
    ];
  }

  const api = {
    PyRandom, generate, statement, explain, join,
    LOCATION_AT, CLUE_TEXT, MOTIVE_TEXT,
  };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.Mystery = api;
})(this);
