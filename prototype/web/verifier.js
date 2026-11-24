/*
 * Vérifie que mystery.js produit exactement les mêmes crimes et les mêmes
 * phrases que la version Python (mystery.py + enquete.py).
 *
 *     node prototype/web/verifier.js          # 5 000 seeds
 *     node prototype/web/verifier.js 20000    # autre nombre de seeds
 */
"use strict";

const path = require("path");
const { spawnSync } = require("child_process");
const M = require("./mystery.js");

const count = Number(process.argv[2] || 5000);
const extra = ["-1", "-987654", "4294967295", "4294967296", "1099511627776", "1000000000000000000000000000000"];
const seeds = [...Array.from({ length: count }, (_, i) => String(i)), ...extra];

const prototypeDir = path.join(__dirname, "..");
const py = `
import dataclasses, json, sys
sys.path.insert(0, ${JSON.stringify(prototypeDir)})
import mystery, enquete
out = []
for s in json.loads(sys.stdin.read()):
    c = mystery.generate(int(s))
    d = dataclasses.asdict(c)
    d["seed"] = s
    d["statements"] = {n: enquete.statement(c, n) for n in c.living}
    d["explain"] = enquete.explain(c)
    out.append(d)
print(json.dumps(out, ensure_ascii=False))
`;
const res = spawnSync("python3", ["-I", "-c", py], {
  input: JSON.stringify(seeds), encoding: "utf-8", maxBuffer: 1 << 30,
});
if (res.status !== 0) {
  console.error(res.stderr);
  process.exit(2);
}
const expected = JSON.parse(res.stdout);

const FIELDS = ["npcs", "victim", "culprit", "herring", "crime_location",
  "location_at_crime", "motives", "alibis", "seen", "rumors", "witnesses",
  "trades", "clue"];

// Compare en ignorant l'ordre des clés des objets, mais pas celui des listes.
function canon(v) {
  if (Array.isArray(v)) return v.map(canon);
  if (v && typeof v === "object") {
    return Object.fromEntries(Object.keys(v).sort().map((k) => [k, canon(v[k])]));
  }
  return v;
}
const same = (a, b) => JSON.stringify(canon(a)) === JSON.stringify(canon(b));

let failures = 0;
for (const want of expected) {
  const got = M.generate(want.seed);
  const diffs = FIELDS.filter((f) => !same(got[f], want[f]));
  for (const n of got.living) {
    if (!same(M.statement(got, n), want.statements[n])) diffs.push(`statement(${n})`);
  }
  if (!same(M.explain(got), want.explain)) diffs.push("explain");
  if (diffs.length) {
    failures++;
    if (failures <= 5) console.log(`seed ${want.seed} : différences sur ${diffs.join(", ")}`);
  }
}
console.log(`${expected.length - failures}/${expected.length} seeds identiques entre Python et JavaScript`);
process.exit(failures ? 1 : 0);
