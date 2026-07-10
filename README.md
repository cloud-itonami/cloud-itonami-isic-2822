# cloud-itonami-isic-2822

Open Business Blueprint for **ISIC Rev.5 2822**: manufacture of
metal-forming machinery and machine tools -- unit intake, ISO 230
geometric/positioning accuracy acceptance testing and accuracy-
certificate issuance for a community machine-tool plant.

This repository publishes a machine-tool-manufacturing actor -- unit
intake, per-jurisdiction design-rules/conformity verification, ISO
230 accuracy-test screening, robot unit-dispatch and accuracy-
certificate finalization -- as an OSS business that any qualified
machine-tool plant can fork, deploy, run, improve and sell, so a
plant keeps its own construction and accuracy-test history instead of
renting a closed MES / quality SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **Machine Tool Advisor ⊣
Machine Tool Governor**.

## Scope note: manufacturing capital equipment, not the metalworking shop that uses it

This repository is scoped to **building** metal-forming machinery and
machine tools themselves (lathes, milling machines, presses --
design-rules verification, accuracy testing, accuracy-certificate
evidence). It is not a metalworking / fabrication-shop vertical that
merely *operates* machine tools to produce other goods. Distinct from:

- `cloud-itonami-isic-2410` — basic iron and steel **manufacturing**
- `cloud-itonami-isic-2511` — structural metal products **manufacturing**
- `cloud-itonami-isic-2811` — engines and turbines **manufacturing**
- `cloud-itonami-isic-2910` — motor vehicles **manufacturing**
- `cloud-itonami-isic-3011` — ships and floating structures **manufacturing**

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here robots (final assembly,
fit-up, geometric/positioning accuracy scan) operate under an actor
that proposes actions and an independent **Machine Tool Governor**
that gates them. The governor never issues an accuracy certificate
itself; `:high`/`:safety-critical` actions (`:actuation/dispatch-
unit`, `:actuation/issue-accuracy-certificate`) require human
sign-off.

## Core contract

```text
unit intake + design-rules verify + accuracy-test screen
  -> Machine Tool Advisor proposal
  -> Machine Tool Governor (HARD holds un-overridable)
  -> phase gate (actuation always escalates)
  -> human approval for high stakes
  -> append-only ledger + draft records
```

## Actuation honesty

Dispatching a final-assembly robot action and issuing an accuracy
certificate produce **unsigned draft records and ledger facts only**.
This actor does not talk to real plant control systems or
conformity-marking portals. Signature and hardware dispatch are the
machine-tool plant's own acts.

## Ops

| Op | Effect |
|---|---|
| `:unit/intake` | normalize unit directory patch (phase 3 may auto-commit when clean) |
| `:design-rules/verify` | per-jurisdiction design/conformity evidence checklist (always human) |
| `:accuracy-test/screen` | ISO 230 geometric/positioning accuracy-test screen (HARD hold if unresolved) |
| `:actuation/dispatch-unit` | draft unit-dispatch record (always human) |
| `:actuation/issue-accuracy-certificate` | draft ISO 230 accuracy-certificate record (always human) |

## Social / regulatory hand-off

```clojure
(require '[machinetool.store :as store]
         '[machinetool.export :as export])

(def db (store/seed-db))
(export/audit-package db)           ;; EDN maps for conformity/regulator hand-off
(export/package->csv-bundle db)     ;; CSV bundle (units/ledger/dispatches/accuracy-certificates)
```

Operator console (static sample): `docs/samples/operator-console.html`.

## Develop

```bash
clojure -M:dev:test
clojure -M:lint
clojure -M:dev:run
```

## License

AGPL-3.0-or-later — see `LICENSE`.

## Operator console (Pages)

After enabling GitHub Pages (Settings → Pages → GitHub Actions), the
static console is at:

https://cloud-itonami.github.io/cloud-itonami-isic-2822/

Local: open `docs/index.html` or `docs/samples/operator-console.html`.

## Export audit package (CLI)

```bash
clojure -M:dev:export
# or: clojure -M:dev:export /tmp/audit-2822
```

Writes CSV files under `out/audit-package/` (or the given directory).
