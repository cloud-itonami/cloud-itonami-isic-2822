# ADR-0001: Machine Tool Advisor ⊣ Machine Tool Governor architecture

- Status: Accepted (2026-07-10)
- Repository: `cloud-itonami-isic-2822` (ISIC Rev.5 `2822`)

## Context

Machine-tool manufacturing (final assembly, ISO 230 geometric/
positioning accuracy testing, design-rules/conformity marking,
accuracy-certificate issuance) needs the same governed-actor pattern
as the rest of the cloud-itonami fleet: an untrusted advisor
proposes; an independent governor may HOLD; high-stakes actuation
never auto-commits.

This vertical continues the classic heavy-industry manufacturing
cluster after `cloud-itonami-isic-2410` (basic iron and steel),
`cloud-itonami-isic-2811` (engines and turbines), `cloud-itonami-isic-
2910` (motor vehicles) and `cloud-itonami-isic-3011` (ships and
floating structures) -- and is the fleet's first **capital-equipment**
manufacturing vertical (the machines that make other machines),
distinct from the transport-equipment sub-cluster (2811/2910/3011).

## Decision

1. Namespaces live under `machinetool.*` with the standard
   facts / registry / store / governor / phase / advisor / operation / sim
   shape.
2. Entity is a machine-tool **unit** (a lathe, milling machine or
   press), not a vehicle, hull block or steel heat.
3. Dual actuation on the same entity:
   - `:actuation/dispatch-unit` (robot final-assembly/shipment dispatch draft)
   - `:actuation/issue-accuracy-certificate` (ISO 230 accuracy-certificate draft)
4. Double-actuation guards use dedicated booleans
   (`:unit-dispatched?`, `:accuracy-certified?`), never a status
   lifecycle (ADR-2607071320 / 6492 lesson).
5. `unit-accuracy-out-of-range?` continues the fleet two-sided range
   check family (after testlab / conservation / water / steelworks /
   turbine / automotive), applied here to a unit's own measured ISO
   230-2 positioning-accuracy deviation against its own recorded
   spec bounds.
6. Accuracy-test defect unresolved is evaluated unconditionally so
   `:accuracy-test/screen` itself can HARD-hold (parksafety
   ADR-2607071922 Decision 5 discipline).
7. Spec-basis catalog seeds JPN (METI/JMTBA/JIS B 6201·B 6336) / USA
   (ANSI/ASME B5 series + OSHA 29 CFR 1910.212) / GBR (HSE/UKCA
   machinery framework) / DEU (BAuA/DIN, EU Machinery Directive
   2006/42/EC context) only; all four reference ISO 230 (machine-tool
   accuracy test code) as the shared international baseline. Missing
   jurisdictions are uncovered, never fabricated.

## Consequences

(+) Machine-tool manufacturing gains a forkable OSS operating stack
with auditable governor holds.
(+) Reuses langgraph + store dual-backend parity without new physics.
(−) No physical plant digital-twin tick in this repo (follow-up domain
data, e.g. giemon-factory style layout, is out of scope here).
(−) Design-conformity-authority coverage is a starting catalog, not
exhaustive.

## Related

- Superproject fleet ADR for this promotion (machine-tool-2822-coverage)
- Sibling architecture: `cloud-itonami-isic-2410` docs/adr/0001,
  `cloud-itonami-isic-2811` docs/adr/0001, `cloud-itonami-isic-2910`
  docs/adr/0001, `cloud-itonami-isic-3011` docs/adr/0001
