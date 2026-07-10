# Business Model: Manufacture of Metal-Forming Machinery and Machine Tools

## Classification
- Repository: `cloud-itonami-isic-2822`
- ISIC Rev.5: `2822` — manufacture of metal-forming machinery and machine tools — unit fabrication, ISO 230 accuracy testing and accuracy-certificate evidence
- Social impact: industrial-safety, supply-resilience, industrial-jobs

## Customer
- independent machine-tool manufacturers needing auditable design-rules and production records
- contract plants producing beds, spindles, controllers and axes for multiple OEM machine-tool brands
- plant operators needing verifiable build and accuracy-test history for produced machine-tool units
- market regulators (CE/UKCA marking authorities, OSHA-adjacent guarding inspectors) needing verifiable design-conformity and accuracy-test evidence
- programs that cannot accept closed, unauditable manufacturing-execution platforms

## Offer
- design-rules and jurisdiction-scope version management
- robotics-assisted final assembly, fit-up and geometric/positioning-accuracy inspection records
- unit ISO 230-2 positioning-accuracy-deviation and accuracy-test chain-of-custody history
- accuracy-certificate drafts and disclosure records
- role-based access and immutable audit ledger
- CSV/EDN audit package export for inspectors

## Revenue
- self-host setup fee
- managed hosting subscription per plant / production line
- support retainer with SLA
- final-assembly/accuracy-test robot integration and maintenance

## Trust Controls
- out-of-spec units are blocked; an accuracy certificate is mandatory for release paths; unit history is immutable
- a robot action the governor refuses is never dispatched to hardware
- every dispatch, hold, approval and disclosure path is auditable
- sensitive design and production data stays outside Git
- a fabricated design-rules citation, incomplete evidence, an
  out-of-spec unit positioning-accuracy deviation, or an unresolved
  accuracy-test defect -- each forces a hold, not an override
- accuracy-certificate issuance is logged and escalated, and cannot be
  finalized twice for the same unit
