(ns machinetool.store
  "SSoT for the machine-tool-manufacturing actor, behind a `Store`
  protocol so the backend is a swap, not a rewrite -- the same seam
  every prior `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/machinetool/store_contract_test.clj), which is the whole
  point: the actor, the Machine Tool Governor and the audit ledger
  never know which SSoT they run on.

  Like every other dual-actuation sibling before it, this actor has
  TWO actuation events (dispatching a unit action, issuing an
  accuracy certificate) acting on the SAME entity (a unit), each with
  its OWN history collection, sequence counter and dedicated
  double-actuation-guard boolean (`:unit-dispatched?`/
  `:accuracy-certified?`, never a `:status` value) -- the same
  discipline every prior sibling governor's guards establish, informed
  by `cloud-itonami-isic-6492`'s status-lifecycle bug
  (ADR-2607071320).

  The ledger stays append-only on every backend: 'which unit was
  screened for an unresolved ISO 230 accuracy-test defect, which unit
  action was dispatched, which accuracy certificate was issued, on
  what jurisdictional basis, approved by whom' is always a query over
  an immutable log -- the audit trail a community trusting a
  machine-tool manufacturer needs, and the evidence a manufacturer
  needs if a dispatch or accuracy-certificate decision is later
  disputed.

  ── Additive: testlab-engagement-ref (superproject independent-
  verification-of-self-issued-certificates ADR) ──

  A unit whose accuracy certificate has been issued now also carries
  `:testlab-engagement-ref` -- the MANDATORY `:certification/testlab-
  engagement-ref` the proposal supplied, re-verified complete by
  `machinetool.governor/testlab-engagement-ref-missing-violations`
  before commit is ever reached, and persisted onto the unit record
  here so the independent third-party accredited testing laboratory
  actor (`cloud-itonami-isic-7120`) engagement/certification that
  justified this issuance stays queryable, not just checked-then-
  discarded. See `machinetool.governor` ns docstring Addendum 2."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [machinetool.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (unit [s id])
  (all-units [s])
  (accuracy-screen-of [s unit-id] "committed ISO 230 accuracy-test screening verdict for a unit, or nil")
  (requirements-verification-of [s unit-id] "committed design-rules requirements verification, or nil")
  (ledger [s])
  (dispatch-history [s] "the append-only unit-dispatch history (machinetool.registry drafts)")
  (evidence-history [s] "the append-only accuracy-certificate history (machinetool.registry drafts)")
  (maintenance-notice-history [s] "the append-only maintenance/recall-notice history (machinetool.registry drafts) -- a unit may appear more than once, unlike dispatch/evidence")
  (next-dispatch-sequence [s jurisdiction] "next dispatch-number sequence for a jurisdiction")
  (next-evidence-sequence [s jurisdiction] "next evidence-number sequence for a jurisdiction")
  (next-maintenance-notice-sequence [s jurisdiction] "next maintenance-notice-number sequence for a jurisdiction")
  (unit-already-dispatched? [s unit-id] "has this unit's action already been dispatched?")
  (unit-already-certified? [s unit-id] "has this unit's accuracy certificate already been issued?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-units [s units] "replace/seed the unit directory (map id->unit)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained unit set covering both actuation
  lifecycles (dispatching a unit action, issuing an accuracy
  certificate) so the actor + tests run offline."
  []
  {:units
   {"unit-1" {:id "unit-1" :unit-name "Sakura CNC Lathe SL-04"
                  :positioning-accuracy-deviation-actual 0.05 :positioning-accuracy-deviation-min -0.10 :positioning-accuracy-deviation-max 0.10
                  :accuracy-test-defect-unresolved? false
                  :unit-dispatched? false :accuracy-certified? false
                  :jurisdiction "JPN" :status :intake}
    "unit-2" {:id "unit-2" :unit-name "Atlantis Vertical Machining Center VM-12"
                  :positioning-accuracy-deviation-actual 0.05 :positioning-accuracy-deviation-min -0.10 :positioning-accuracy-deviation-max 0.10
                  :accuracy-test-defect-unresolved? false
                  :unit-dispatched? false :accuracy-certified? false
                  :jurisdiction "ATL" :status :intake}
    "unit-3" {:id "unit-3" :unit-name "鈴木精密フライス盤 SF-07"
                  :positioning-accuracy-deviation-actual 0.35 :positioning-accuracy-deviation-min -0.10 :positioning-accuracy-deviation-max 0.10
                  :accuracy-test-defect-unresolved? false
                  :unit-dispatched? false :accuracy-certified? false
                  :jurisdiction "JPN" :status :intake}
    "unit-4" {:id "unit-4" :unit-name "田中油圧プレス機 TP-03"
                  :positioning-accuracy-deviation-actual 0.05 :positioning-accuracy-deviation-min -0.10 :positioning-accuracy-deviation-max 0.10
                  :accuracy-test-defect-unresolved? true
                  :unit-dispatched? false :accuracy-certified? false
                  :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- dispatch-unit!
  "Backend-agnostic `:unit/mark-dispatched` -- looks up the
  unit via the protocol and drafts the unit-dispatch record,
  and returns {:result .. :unit-patch ..} for the caller to
  persist."
  [s unit-id]
  (let [a (unit s unit-id)
        seq-n (next-dispatch-sequence s (:jurisdiction a))
        result (registry/register-unit-dispatch unit-id (:jurisdiction a) seq-n)]
    {:result result
     :unit-patch {:unit-dispatched? true
                      :dispatch-number (get result "dispatch_number")}}))

(defn- issue-accuracy-certificate!
  "Backend-agnostic `:unit/mark-certified` -- looks up the
  unit via the protocol and drafts the accuracy-certificate
  record, and returns {:result .. :unit-patch ..} for the caller
  to persist. `ref` (when present -- the proposal's own
  `:certification/testlab-engagement-ref`, ALREADY re-verified
  complete by `machinetool.governor/testlab-engagement-ref-missing-
  violations` before commit is ever reached) is folded into the
  unit-patch so the independent third-party engagement/certification
  reference that justified this issuance stays on the unit's own
  record, not just checked-then-discarded."
  [s unit-id & [ref]]
  (let [a (unit s unit-id)
        seq-n (next-evidence-sequence s (:jurisdiction a))
        result (registry/register-accuracy-certificate unit-id (:jurisdiction a) seq-n)]
    {:result result
     :unit-patch (cond-> {:accuracy-certified? true
                          :evidence-number (get result "evidence_number")}
                   ref (assoc :testlab-engagement-ref ref))}))

(defn- issue-maintenance-notice!
  "Backend-agnostic `:maintenance-notice/issue` -- looks up the unit
  via the protocol and drafts the maintenance/recall-notice record.
  Returns {:result ..} for the caller to persist -- unlike
  `dispatch-unit!`/`issue-accuracy-certificate!`, there is no
  `:unit-patch`: a unit may receive many maintenance notices over its
  life, so there is no dedicated single-shot `:unit-*?` boolean to
  flip."
  [s unit-id dispatch-ref]
  (let [a (unit s unit-id)
        seq-n (next-maintenance-notice-sequence s (:jurisdiction a))]
    {:result (registry/register-maintenance-notice unit-id dispatch-ref (:jurisdiction a) seq-n)}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (unit [_ id] (get-in @a [:units id]))
  (all-units [_] (sort-by :id (vals (:units @a))))
  (accuracy-screen-of [_ id] (get-in @a [:accuracy-screens id]))
  (requirements-verification-of [_ unit-id] (get-in @a [:verifications unit-id]))
  (ledger [_] (:ledger @a))
  (dispatch-history [_] (:dispatches @a))
  (evidence-history [_] (:evidences @a))
  (maintenance-notice-history [_] (:maintenance-notices @a))
  (next-dispatch-sequence [_ jurisdiction] (get-in @a [:dispatch-sequences jurisdiction] 0))
  (next-evidence-sequence [_ jurisdiction] (get-in @a [:evidence-sequences jurisdiction] 0))
  (next-maintenance-notice-sequence [_ jurisdiction] (get-in @a [:maintenance-notice-sequences jurisdiction] 0))
  (unit-already-dispatched? [_ unit-id] (boolean (get-in @a [:units unit-id :unit-dispatched?])))
  (unit-already-certified? [_ unit-id] (boolean (get-in @a [:units unit-id :accuracy-certified?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :unit/upsert
      (swap! a update-in [:units (:id value)] merge value)

      :verification/set
      (swap! a assoc-in [:verifications (first path)] payload)

      :accuracy-test-screen/set
      (swap! a assoc-in [:accuracy-screens (first path)] payload)

      :unit/mark-dispatched
      (let [unit-id (first path)
            {:keys [result unit-patch]} (dispatch-unit! s unit-id)
            jurisdiction (:jurisdiction (unit s unit-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:dispatch-sequences jurisdiction] (fnil inc 0))
                       (update-in [:units unit-id] merge unit-patch)
                       (update :dispatches registry/append result))))
        result)

      :unit/mark-certified
      (let [unit-id (first path)
            ref (:certification/testlab-engagement-ref value)
            {:keys [result unit-patch]} (issue-accuracy-certificate! s unit-id ref)
            jurisdiction (:jurisdiction (unit s unit-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:evidence-sequences jurisdiction] (fnil inc 0))
                       (update-in [:units unit-id] merge unit-patch)
                       (update :evidences registry/append result))))
        result)

      :maintenance-notice/issue
      (let [unit-id (first path)
            {:keys [result]} (issue-maintenance-notice! s unit-id (:dispatch-ref value))
            jurisdiction (:jurisdiction (unit s unit-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:maintenance-notice-sequences jurisdiction] (fnil inc 0))
                       (update :maintenance-notices registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-units [s units] (when (seq units) (swap! a assoc :units units)) s))

(defn seed-db
  "A MemStore seeded with the demo unit set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :verifications {} :accuracy-screens {} :ledger [] :dispatch-sequences {}
                           :dispatches [] :evidence-sequences {} :evidences []
                           :maintenance-notice-sequences {} :maintenance-notices []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (verification/accuracy-screen payloads, ledger
  facts, dispatch/evidence records) are stored as EDN strings so
  `langchain.db` doesn't expand them into sub-entities -- the same
  convention every sibling actor's store uses."
  {:unit/id                            {:db/unique :db.unique/identity}
   :verification/unit-id               {:db/unique :db.unique/identity}
   :accuracy-screen/unit-id            {:db/unique :db.unique/identity}
   :ledger/seq                         {:db/unique :db.unique/identity}
   :dispatch/seq                       {:db/unique :db.unique/identity}
   :evidence/seq                       {:db/unique :db.unique/identity}
   :maintenance-notice/seq             {:db/unique :db.unique/identity}
   :dispatch-sequence/jurisdiction     {:db/unique :db.unique/identity}
   :evidence-sequence/jurisdiction     {:db/unique :db.unique/identity}
   :maintenance-notice-sequence/jurisdiction {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- unit->tx [{:keys [id unit-name positioning-accuracy-deviation-actual positioning-accuracy-deviation-min positioning-accuracy-deviation-max
                          accuracy-test-defect-unresolved?
                          unit-dispatched? accuracy-certified?
                          jurisdiction status dispatch-number evidence-number
                          testlab-engagement-ref]}]
  (cond-> {:unit/id id}
    unit-name                                   (assoc :unit/unit-name unit-name)
    positioning-accuracy-deviation-actual        (assoc :unit/positioning-accuracy-deviation-actual positioning-accuracy-deviation-actual)
    positioning-accuracy-deviation-min           (assoc :unit/positioning-accuracy-deviation-min positioning-accuracy-deviation-min)
    positioning-accuracy-deviation-max           (assoc :unit/positioning-accuracy-deviation-max positioning-accuracy-deviation-max)
    (some? accuracy-test-defect-unresolved?)     (assoc :unit/accuracy-test-defect-unresolved? accuracy-test-defect-unresolved?)
    (some? unit-dispatched?)                     (assoc :unit/unit-dispatched? unit-dispatched?)
    (some? accuracy-certified?)                  (assoc :unit/accuracy-certified? accuracy-certified?)
    jurisdiction                                 (assoc :unit/jurisdiction jurisdiction)
    status                                       (assoc :unit/status status)
    dispatch-number                              (assoc :unit/dispatch-number dispatch-number)
    evidence-number                              (assoc :unit/evidence-number evidence-number)
    testlab-engagement-ref                       (assoc :unit/testlab-engagement-ref (enc testlab-engagement-ref))))

(def ^:private unit-pull
  [:unit/id :unit/unit-name :unit/positioning-accuracy-deviation-actual
   :unit/positioning-accuracy-deviation-min :unit/positioning-accuracy-deviation-max
   :unit/accuracy-test-defect-unresolved? :unit/unit-dispatched? :unit/accuracy-certified?
   :unit/jurisdiction :unit/status :unit/dispatch-number :unit/evidence-number
   :unit/testlab-engagement-ref])

(defn- pull->unit [m]
  (when (:unit/id m)
    {:id (:unit/id m) :unit-name (:unit/unit-name m)
     :positioning-accuracy-deviation-actual (:unit/positioning-accuracy-deviation-actual m)
     :positioning-accuracy-deviation-min (:unit/positioning-accuracy-deviation-min m)
     :positioning-accuracy-deviation-max (:unit/positioning-accuracy-deviation-max m)
     :accuracy-test-defect-unresolved? (boolean (:unit/accuracy-test-defect-unresolved? m))
     :unit-dispatched? (boolean (:unit/unit-dispatched? m))
     :accuracy-certified? (boolean (:unit/accuracy-certified? m))
     :jurisdiction (:unit/jurisdiction m) :status (:unit/status m)
     :dispatch-number (:unit/dispatch-number m) :evidence-number (:unit/evidence-number m)
     :testlab-engagement-ref (dec* (:unit/testlab-engagement-ref m))}))

(defrecord DatomicStore [conn]
  Store
  (unit [_ id]
    (pull->unit (d/pull (d/db conn) unit-pull [:unit/id id])))
  (all-units [_]
    (->> (d/q '[:find [?id ...] :where [?e :unit/id ?id]] (d/db conn))
         (map #(pull->unit (d/pull (d/db conn) unit-pull [:unit/id %])))
         (sort-by :id)))
  (accuracy-screen-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?aid
                :where [?k :accuracy-screen/unit-id ?aid] [?k :accuracy-screen/payload ?p]]
              (d/db conn) id)))
  (requirements-verification-of [_ unit-id]
    (dec* (d/q '[:find ?p . :in $ ?aid
                :where [?a :verification/unit-id ?aid] [?a :verification/payload ?p]]
              (d/db conn) unit-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (dispatch-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :dispatch/seq ?s] [?e :dispatch/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (evidence-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :evidence/seq ?s] [?e :evidence/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (maintenance-notice-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :maintenance-notice/seq ?s] [?e :maintenance-notice/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-dispatch-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :dispatch-sequence/jurisdiction ?j] [?e :dispatch-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-evidence-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :evidence-sequence/jurisdiction ?j] [?e :evidence-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-maintenance-notice-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :maintenance-notice-sequence/jurisdiction ?j] [?e :maintenance-notice-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (unit-already-dispatched? [s unit-id]
    (boolean (:unit-dispatched? (unit s unit-id))))
  (unit-already-certified? [s unit-id]
    (boolean (:accuracy-certified? (unit s unit-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :unit/upsert
      (d/transact! conn [(unit->tx value)])

      :verification/set
      (d/transact! conn [{:verification/unit-id (first path) :verification/payload (enc payload)}])

      :accuracy-test-screen/set
      (d/transact! conn [{:accuracy-screen/unit-id (first path) :accuracy-screen/payload (enc payload)}])

      :unit/mark-dispatched
      (let [unit-id (first path)
            {:keys [result unit-patch]} (dispatch-unit! s unit-id)
            jurisdiction (:jurisdiction (unit s unit-id))
            next-n (inc (next-dispatch-sequence s jurisdiction))]
        (d/transact! conn
                     [(unit->tx (assoc unit-patch :id unit-id))
                      {:dispatch-sequence/jurisdiction jurisdiction :dispatch-sequence/next next-n}
                      {:dispatch/seq (count (dispatch-history s)) :dispatch/record (enc (get result "record"))}])
        result)

      :unit/mark-certified
      (let [unit-id (first path)
            ref (:certification/testlab-engagement-ref value)
            {:keys [result unit-patch]} (issue-accuracy-certificate! s unit-id ref)
            jurisdiction (:jurisdiction (unit s unit-id))
            next-n (inc (next-evidence-sequence s jurisdiction))]
        (d/transact! conn
                     [(unit->tx (assoc unit-patch :id unit-id))
                      {:evidence-sequence/jurisdiction jurisdiction :evidence-sequence/next next-n}
                      {:evidence/seq (count (evidence-history s)) :evidence/record (enc (get result "record"))}])
        result)

      :maintenance-notice/issue
      (let [unit-id (first path)
            {:keys [result]} (issue-maintenance-notice! s unit-id (:dispatch-ref value))
            jurisdiction (:jurisdiction (unit s unit-id))
            next-n (inc (next-maintenance-notice-sequence s jurisdiction))]
        (d/transact! conn
                     [{:maintenance-notice-sequence/jurisdiction jurisdiction :maintenance-notice-sequence/next next-n}
                      {:maintenance-notice/seq (count (maintenance-notice-history s)) :maintenance-notice/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-units [s units]
    (when (seq units) (d/transact! conn (mapv unit->tx (vals units)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:units ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [units]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-units s units))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo unit set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
