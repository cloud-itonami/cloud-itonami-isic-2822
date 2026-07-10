(ns machinetool.registry
  "Pure-function unit-dispatch + accuracy-certificate record
  construction -- an append-only machine-tool-manufacturer book-of-
  record draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a unit-dispatch or
  accuracy-certificate reference number -- every manufacturer/
  jurisdiction assigns its own reference format. This namespace does
  NOT invent one; it builds a jurisdiction-scoped sequence number and
  validates the record's required fields, the same honest, non-
  fabricating discipline `machinetool.facts` uses.

  `unit-accuracy-out-of-range?` continues this fleet's two-sided
  range check family (`testlab.registry/within-tolerance?` /
  `conservation.registry/body-condition-out-of-range?` /
  `water.registry/contaminant-level-out-of-range?` /
  `steelworks.registry/heat-chemistry-out-of-range?` /
  `turbine.registry/unit-tolerance-out-of-range?` /
  `automotive.registry/vehicle-emissions-out-of-range?` established
  the priors), applying the SAME lo/hi bounds-comparison shape to a
  machine-tool unit's own measured ISO 230-2 positioning-accuracy
  deviation against the unit's own recorded spec bounds.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real fab/final-assembly control system. It builds the
  RECORD a manufacturer would keep, not the act of dispatching the
  robot unit action or issuing the accuracy certificate itself (that
  is `machinetool.operation`'s `:actuation/dispatch-unit`/
  `:actuation/issue-accuracy-certificate`, always human-gated -- see
  README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  manufacturer's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn unit-accuracy-out-of-range?
  "Does `unit`'s own `:positioning-accuracy-deviation-actual` (ISO
  230-2 positioning-accuracy deviation) fall outside its own
  `[:positioning-accuracy-deviation-min :positioning-accuracy-
  deviation-max]` recorded spec-bounds? A pure ground-truth check
  against the unit's own permanent fields -- no upstream comparison
  needed. One of this fleet's two-sided range check family (see ns
  docstring)."
  [{:keys [positioning-accuracy-deviation-actual positioning-accuracy-deviation-min positioning-accuracy-deviation-max]}]
  (and (number? positioning-accuracy-deviation-actual) (number? positioning-accuracy-deviation-min) (number? positioning-accuracy-deviation-max)
       (or (< positioning-accuracy-deviation-actual positioning-accuracy-deviation-min)
           (> positioning-accuracy-deviation-actual positioning-accuracy-deviation-max))))

(defn register-unit-dispatch
  "Validate + construct the UNIT-DISPATCH registration DRAFT -- the
  manufacturer's own act of dispatching a real robot final-assembly/
  shipment action to complete a machine-tool unit. Pure function --
  does not touch any real fab/final-assembly control system; it
  builds the RECORD a manufacturer would keep. `machinetool.governor`
  independently re-verifies the unit's own positioning-accuracy
  sufficiency against its own spec bounds, and a double-dispatch for
  the same unit, before this is ever allowed to commit."
  [unit-id jurisdiction sequence]
  (when-not (and unit-id (not= unit-id ""))
    (throw (ex-info "unit-dispatch: unit_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "unit-dispatch: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "unit-dispatch: sequence must be >= 0" {})))
  (let [dispatch-number (str (str/upper-case jurisdiction) "-MTL-" (zero-pad sequence 6))
        record {"record_id" dispatch-number
                "kind" "unit-dispatch-draft"
                "unit_id" unit-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "dispatch_number" dispatch-number
     "certificate" (unsigned-certificate "UnitDispatch" dispatch-number dispatch-number)}))

(defn register-accuracy-certificate
  "Validate + construct the ACCURACY-CERTIFICATE registration DRAFT --
  the manufacturer's own act of issuing a real ISO 230 accuracy
  acceptance-test certificate documenting a unit's geometric/
  positioning accuracy. Pure function -- does not touch any real
  fab/final-assembly control system; it builds the RECORD a
  manufacturer would keep. `machinetool.governor` independently
  re-verifies the unit's own accuracy-test defect resolution status,
  and a double-issuance for the same unit, before this is ever
  allowed to commit."
  [unit-id jurisdiction sequence]
  (when-not (and unit-id (not= unit-id ""))
    (throw (ex-info "accuracy-certificate: unit_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "accuracy-certificate: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "accuracy-certificate: sequence must be >= 0" {})))
  (let [evidence-number (str (str/upper-case jurisdiction) "-ACC-" (zero-pad sequence 6))
        record {"record_id" evidence-number
                "kind" "accuracy-certificate-draft"
                "unit_id" unit-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "evidence_number" evidence-number
     "certificate" (unsigned-certificate "AccuracyCertificate" evidence-number evidence-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
