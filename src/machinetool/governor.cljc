(ns machinetool.governor
  "Machine Tool Governor -- the independent compliance layer
  that earns the Machine Tool Advisor the right to commit. The LLM has
  no notion of design-rules law, whether a unit's own measured ISO
  230-2 positioning-accuracy deviation actually stays within its own
  recorded spec bounds, whether an accuracy-test-detected defect
  against the unit has actually stayed unresolved, or when an act
  stops being a draft and becomes a real-world robot unit dispatch or
  accuracy-certificate issuance, so this MUST be a separate system
  able to *reject* a proposal and fall back to HOLD -- the machine-
  tool-manufacturer analog of `cloud-itonami-isic-6512`'s
  CasualtyGovernor.

  Six checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated design-rules spec-basis, incomplete evidence, an out-of-
  spec unit, an unresolved accuracy-test defect, or a double dispatch/
  certificate-issuance). The confidence/actuation gate is SOFT: it asks
  a human to look (low confidence / actuation), and the human may
  approve -- but see `machinetool.phase`: for `:stake :actuation/
  dispatch-unit`/`:actuation/issue-accuracy-certificate` (a real
  safety-critical act) NO phase ever allows auto-commit either. Two
  independent layers agree that actuation is always a human call.

    1. Spec-basis                  -- did the requirements proposal cite
                                       an OFFICIAL source (`machinetool.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for `:actuation/dispatch-
                                       unit`/`:actuation/issue-
                                       accuracy-certificate`, has the
                                       unit actually been verified
                                       with a full geometric-accuracy-
                                       test-report/positioning-
                                       accuracy-test-report/machine-
                                       guarding-conformity-record/
                                       material-certification-record
                                       evidence checklist on file?
    3. Unit accuracy out of range  -- for `:actuation/dispatch-
                                       unit`, INDEPENDENTLY
                                       recompute whether the
                                       unit's own measured ISO
                                       230-2 positioning-accuracy
                                       deviation falls outside its
                                       own recorded spec bounds
                                       (`machinetool.registry/
                                       unit-accuracy-out-of-
                                       range?`) -- needs no proposal
                                       inspection or stored-verdict
                                       lookup at all. One of this
                                       fleet's two-sided range check
                                       family (`testlab.governor/
                                       within-tolerance-violations`/
                                       `conservation.governor/body-
                                       condition-out-of-range-
                                       violations`/`water.governor/
                                       contaminant-level-out-of-range-
                                       violations`/`steelworks.
                                       governor`/`turbine.governor`/
                                       `automotive.governor`
                                       established the priors).
    4. Accuracy-test defect
       unresolved                  -- reported by THIS proposal itself
                                       (an `:accuracy-test/screen`
                                       that just found an unresolved
                                       defect), or already on file
                                       for the unit (`:accuracy-
                                       test/screen`/`:actuation/
                                       issue-accuracy-certificate`).
                                       Evaluated UNCONDITIONALLY (not
                                       scoped to a specific op), the
                                       SAME discipline `casualty.
                                       governor/sanctions-
                                       violations`/...(prior
                                       siblings)... established --
                                       exercised in tests/demo via
                                       `:accuracy-test/screen`
                                       DIRECTLY, not via an actuation
                                       op against an unscreened
                                       unit -- see this ns's own
                                       test suite.
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/
                                       dispatch-unit`/`:actuation/
                                       issue-accuracy-certificate`
                                       (REAL safety-critical acts) ->
                                       escalate.

  Two more guards, double-dispatch/double-certificate-issuance
  prevention, are enforced but NOT listed as numbered HARD checks
  above because they need no upstream comparison at all --
  `already-dispatched-violations`/`already-certified-violations`
  refuse to dispatch a unit action/issue an accuracy certificate for
  the SAME unit twice, off dedicated `:unit-dispatched?`/
  `:accuracy-certified?` facts (never a `:status` value) -- the SAME
  'check a dedicated boolean, not status' discipline every prior
  sibling governor's guards establish, informed by `cloud-itonami-
  isic-6492`'s status-lifecycle bug (ADR-2607071320).

  Addendum (superproject equipment-asset-linkage ADR, cloud-itonami-
  isic-2822<->cloud-itonami-isic-2813): a SEVENTH HARD check,
  `dispatch-ref-unverified-violations`, was added alongside a new
  `:issue-maintenance-notice` op -- isic-2822's own copy of the SAME
  op cloud-itonami-isic-2813's `pressureequip.governor` already
  established (no shared code; isic-2822 dispatches machine tools/
  welding cells to isic-2813's own factory floor, the mirror image of
  isic-2813 dispatching pressure-equipment units to cloud-itonami-
  jsic-4721's cold-storage warehouse). For `:issue-maintenance-notice`,
  INDEPENDENTLY verify (never trust the proposal's own echo) that the
  unit this notice names was actually already `:actuation/dispatch-
  unit`-dispatched by THIS actor and that the proposal's claimed
  `:dispatch-ref` (`(:value proposal)`'s `:dispatch-ref`) matches the
  unit's own recorded `:dispatch-number`. `:issue-maintenance-notice`
  also joins `high-stakes` (below): a maintenance/recall notice about
  equipment already in the field always escalates to a human, exactly
  like the two actuation ops.

  Addendum 2 (superproject independent-verification-of-self-issued-
  certificates ADR, cloud-itonami-isic-2822<->cloud-itonami-isic-7120):
  an EIGHTH HARD check, `testlab-engagement-ref-missing-violations`,
  was added for `:actuation/issue-accuracy-certificate` -- this
  actor's own accuracy certificate has been a wholly SELF-issued act
  (this Governor hard-checks this actor's own measured positioning-
  accuracy/evidence/defect status, but never an INDEPENDENT third
  party) ever since R0, the exact structural gap ADR-2607176500's
  disclosure-integrity finding (self-attestation alone is not
  sufficient) names. `:actuation/issue-accuracy-certificate` now
  REQUIRES a `:certification/testlab-engagement-ref` naming a
  completed engagement + issued certification number at the
  independent third-party accredited testing laboratory actor
  `cloud-itonami-isic-7120` (`testlab.store`/`testlab.registry`) --
  the SAME wire shape and discipline cloud-itonami-isic-2813's own
  `pressureequip.governor/testlab-engagement-ref-missing-violations`
  establishes (no shared code, this actor's own independent copy):
  this reference is MANDATORY, its total absence is itself the
  violation. This is an intentional BREAKING change to this op's
  existing call contract (like isic-1075's own `:coordinate-shipment`
  `:handoff`-required change, ADR-2607177600) -- an accuracy-
  certificate proposal that omits the reference now HARD-holds where
  it previously could clear this ns's other checks. Same fleet-
  standalone-convention limitation as every prior handoff-style
  check: this actor cannot call isic-7120's own store directly (no
  shared `:local/root`/API dependency), so it can only verify the
  REFERENCE's own wire-shape completeness (`registry/testlab-
  engagement-ref-fields-present?`), not reach across and confirm the
  referenced engagement/certification actually exists on isic-7120's
  live store."
  (:require [machinetool.facts :as facts]
            [machinetool.registry :as registry]
            [machinetool.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Dispatching a real robot unit action on a machine-tool unit and
  issuing a real ISO 230 accuracy certificate are the two real-world
  actuation events this actor performs -- a two-member set, matching
  every prior dual-actuation sibling's shape."
  #{:actuation/dispatch-unit :actuation/issue-accuracy-certificate
    :issue-maintenance-notice})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:design-rules/verify` (or actuation) proposal with no
  spec-basis citation is a HARD violation -- never invent a
  jurisdiction's design-rules requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:design-rules/verify :actuation/dispatch-unit :actuation/issue-accuracy-certificate} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は設計規則要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:actuation/dispatch-unit`/`:actuation/issue-accuracy-
  certificate`, the jurisdiction's required geometric-accuracy-test-
  report/positioning-accuracy-test-report/machine-guarding-
  conformity-record/material-certification-record evidence must
  actually be satisfied -- do not trust the advisor's self-reported
  confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:actuation/dispatch-unit :actuation/issue-accuracy-certificate} op)
    (let [a (store/unit st subject)
          verification (store/requirements-verification-of st subject)]
      (when-not (and verification
                     (facts/required-evidence-satisfied?
                      (:jurisdiction a) (:checklist verification)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(幾何精度試験報告書/位置決め精度試験報告書/機械ガード適合記録/材料証明記録等)が充足していない状態での提案"}]))))

(defn- unit-accuracy-out-of-range-violations
  "For `:actuation/dispatch-unit`, INDEPENDENTLY recompute whether
  the unit's own ISO 230-2 positioning-accuracy deviation falls
  outside its own recorded spec bounds via `machinetool.registry/
  unit-accuracy-out-of-range?` -- needs no proposal inspection or
  stored-verdict lookup at all, since its inputs are permanent
  ground-truth fields already on the unit."
  [{:keys [op subject]} st]
  (when (= op :actuation/dispatch-unit)
    (let [a (store/unit st subject)]
      (when (registry/unit-accuracy-out-of-range? a)
        [{:rule :unit-accuracy-out-of-range
          :detail (str subject " の実測位置決め精度偏差(" (:positioning-accuracy-deviation-actual a)
                      ")が仕様範囲[" (:positioning-accuracy-deviation-min a) "," (:positioning-accuracy-deviation-max a) "]を逸脱")}]))))

(defn- accuracy-test-defect-unresolved-violations
  "An unresolved accuracy-test-detected defect -- reported by THIS
  proposal (e.g. an `:accuracy-test/screen` that itself just found
  one), or already on file in the store for the unit (`:accuracy-
  test/screen`/`:actuation/issue-accuracy-certificate`) -- is a HARD,
  un-overridable hold. Evaluated UNCONDITIONALLY (not scoped to a
  specific op) so the screening op itself can HARD-hold on its own
  finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        unit-id (when (contains? #{:accuracy-test/screen :actuation/issue-accuracy-certificate} op) subject)
        hit-on-file? (and unit-id (= :unresolved (:verdict (store/accuracy-screen-of st unit-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :accuracy-test-defect-unresolved
        :detail "未解決の精度試験欠陥がある状態での精度証明書発行提案は進められない"}])))

(defn- already-dispatched-violations
  "For `:actuation/dispatch-unit`, refuses to dispatch a unit
  action for the SAME unit twice, off a dedicated `:unit-
  dispatched?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/dispatch-unit)
    (when (store/unit-already-dispatched? st subject)
      [{:rule :already-dispatched
        :detail (str subject " は既に完成機実行済み")}])))

(defn- already-certified-violations
  "For `:actuation/issue-accuracy-certificate`, refuses to issue an
  accuracy certificate for the SAME unit twice, off a dedicated
  `:accuracy-certified?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/issue-accuracy-certificate)
    (when (store/unit-already-certified? st subject)
      [{:rule :already-certified
        :detail (str subject " は既に精度証明書発行済み")}])))

(defn- dispatch-ref-unverified-violations
  "For `:issue-maintenance-notice`, INDEPENDENTLY verify that the unit
  named by `subject` was actually already `:actuation/dispatch-unit`-
  dispatched by THIS actor, and that the proposal's claimed
  `:dispatch-ref` (`(:value proposal)`'s `:dispatch-ref`) matches the
  unit's own recorded `:dispatch-number` -- never trust the proposal's
  own echo of a prior record, the SAME anti-fabrication discipline
  cloud-itonami-isic-2813's own `pressureequip.governor/dispatch-ref-
  unverified-violations` establishes (no shared code, this actor's own
  independent copy). A unit that was never dispatched (or a
  `:dispatch-ref` that doesn't match its own recorded dispatch-number)
  HARD-holds; there is no override."
  [{:keys [op subject]} proposal st]
  (when (= op :issue-maintenance-notice)
    (let [a (store/unit st subject)
          claimed (:dispatch-ref (:value proposal))]
      (when-not (and (:unit-dispatched? a) (some? claimed) (= claimed (:dispatch-number a)))
        [{:rule :dispatch-ref-unverified
          :detail (str subject " の :dispatch-ref (" claimed
                      ") が実際の完成機実行記録(dispatch-number=" (:dispatch-number a)
                      ", unit-dispatched?=" (boolean (:unit-dispatched? a))
                      ")と一致しない -- 未実行または架空のdispatch-ref参照")}]))))

(defn- testlab-engagement-ref-missing-violations
  "For `:actuation/issue-accuracy-certificate`, the proposal's
  `:value` must carry a complete `:certification/testlab-engagement-
  ref` (`registry/testlab-engagement-ref-fields-present?`) -- a
  reference into the independent third-party accredited testing
  laboratory actor `cloud-itonami-isic-7120`'s own completed
  engagement + issued certification number. Unlike every other HARD
  check in this ns, which re-verifies THIS actor's own ground-truth
  fields, this one exists because self-attestation alone is not
  enough: an accuracy certificate issued purely by the manufacturer
  that built the machine tool, with no independent verification, is
  the structural gap ADR-2607176500's disclosure-integrity finding
  warns against (see ns docstring Addendum 2). A MISSING reference is
  refused exactly like an INCOMPLETE one -- absence itself is the
  violation, not just fabrication once present."
  [{:keys [op]} proposal]
  (when (= op :actuation/issue-accuracy-certificate)
    (when-not (registry/testlab-engagement-ref-fields-present?
               (:certification/testlab-engagement-ref (:value proposal)))
      [{:rule :testlab-engagement-ref-missing
        :detail "第三者検定機関(cloud-itonami-isic-7120)のengagement/certification参照(:certification/testlab-engagement-ref)が無い、または不完全 -- 自己発行のみでの精度証明書発行は許可されない"}])))

(defn check
  "Censors a Machine Tool Advisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}.

  Includes `dispatch-ref-unverified-violations` -- a SEVENTH hard
  check added alongside `:issue-maintenance-notice` (see ns docstring
  Addendum), purely additive: it only ever fires for that op. Also
  includes `testlab-engagement-ref-missing-violations` -- an EIGHTH
  hard check (see ns docstring Addendum 2), a BREAKING change for
  `:actuation/issue-accuracy-certificate`: unlike every other op-
  scoped check above, this one fires on a MISSING field, not only a
  fabricated/incomplete one."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (unit-accuracy-out-of-range-violations request st)
                           (accuracy-test-defect-unresolved-violations request proposal st)
                           (already-dispatched-violations request st)
                           (already-certified-violations request st)
                           (dispatch-ref-unverified-violations request proposal st)
                           (testlab-engagement-ref-missing-violations request proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
