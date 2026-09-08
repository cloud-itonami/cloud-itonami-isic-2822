(ns machinetool.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for `cloud-itonami-isic-2822`: this
  repo shipped a demo page with NO generator behind it (added by the
  repo's very first commit, `771ec0a`, and never regenerated since),
  so nothing tied the page's contents to the actor's real behaviour.
  This namespace replaces that page with output that is DRIVEN, at
  build time, by the real actor stack:

      machinetool.operation  (langgraph StateGraph, interrupt-before
                              :request-approval)
        -> machinetool.machinetooladvisor  (contained advisor node)
        -> machinetool.governor            (independent censor)
        -> machinetool.phase               (rollout gate)
        -> machinetool.store               (SSoT + append-only ledger)

  Every unit id, jurisdiction, accuracy figure, violation rule,
  violation detail string, dispatch/evidence/notice reference number
  and ledger fact on the rendered page is read back out of that run.
  Nothing on the page is typed by hand except the section prose and
  the column headers. The one table that describes fixed contract
  rather than run output (`Action gate`) is DERIVED from
  `machinetool.phase/phases` + `machinetool.governor/high-stakes`
  rather than hand-described, so it cannot drift away from the code
  the way the old page did.

  Build-time invariants (`-main` throws instead of writing the file):

    1. the run must produce at least one HARD governor hold -- a
       ledger `:governor-hold` fact carrying a NON-EMPTY `:violations`
       vector. A hold produced purely by the rollout phase gate
       (`machinetool.phase/gate` -> `:phase-disabled` /
       `:phase-approval`) also lands as a `:governor-hold` fact but
       with EMPTY `:violations`, so a naive `(count holds)` would be
       satisfied by a page on which the governor never actually
       refused anything. The two are counted, and rendered, apart.
    2. the run must produce at least one COMMIT, so the page cannot
       degenerate into an all-refusals demo that never shows the
       approved path.

  Determinism: the scenario runs against a freshly seeded
  `machinetool.store/seed-db` and the page contains no timestamps, no
  random values and no absolute paths -- two runs of this namespace
  produce byte-identical files. Verified by rendering into two
  scratch directories and diffing.

  Usage: `clojure -M:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [kotoba.lang.text :as str]
            [machinetool.facts :as facts]
            [machinetool.governor :as governor]
            [machinetool.phase :as phase]
            [machinetool.store :as store]
            [machinetool.operation :as op]
            [langgraph.graph :as g]))

;; ----------------------------- the run -----------------------------

(def ^:private operator
  "The human machine-tool manufacturing engineer this scenario runs as,
  at the highest rollout phase (3, supervised-auto)."
  {:actor-id "op-1" :actor-role :machine-tool-engineer :phase 3})

(def ^:private junior-operator
  "The SAME actor at rollout phase 1 (assisted-intake). Used once, to
  show a hold that the ROLLOUT GATE produced rather than the governor
  -- `machinetool.phase/gate` refuses a write op that phase 1 has not
  enabled yet, and the resulting ledger fact carries an EMPTY
  `:violations` vector. See this ns's docstring, invariant 1."
  {:actor-id "op-1" :actor-role :machine-tool-engineer :phase 1})

(def ^:private testlab-engagement-ref
  "A complete reference into the independent third-party accredited
  testing laboratory actor `cloud-itonami-isic-7120`, the shape
  `machinetool.governor/testlab-engagement-ref-missing-violations`
  requires on every `:actuation/issue-accuracy-certificate`. Supplied
  by the CALLER (this scenario), never invented by the advisor."
  {:testlab-engagement-ref/id "engagement-1"
   :testlab-engagement-ref/source-actor "cloud-itonami-isic-7120"
   :testlab-engagement-ref/certification-number "JPN-CERT-000000"})

(defn- step!
  "Runs ONE operation through the real actor and records what actually
  happened. When `:approve-by` is supplied AND the graph actually
  interrupted (`interrupt-before #{:request-approval}`), resumes the
  SAME thread with that approval -- exactly as
  `machinetool.export-run`/`machinetool.sim` do.

  The returned step map keeps the run's own `:record` (the commit
  descriptor the `:decide`/`:request-approval` node produced) so that
  approver attribution below can be traced per-RUN, by thread-id,
  instead of joining records back to approvals on `[op subject]` --
  that join is not unique in general and would let a record inherit
  an unrelated earlier approver."
  [!steps actor {:keys [tid request context approve-by note]}]
  (let [ctx (or context operator)
        r1 (g/run* actor {:request request :context ctx} {:thread-id tid})
        interrupted? (= :interrupted (:status r1))
        r2 (when (and approve-by interrupted?)
             (g/run* actor {:approval {:status :approved :by approve-by}}
                     {:thread-id tid :resume? true}))
        final (or r2 r1)]
    (swap! !steps conj
           {:thread-id    tid
            :op           (:op request)
            :subject      (:subject request)
            :phase        (:phase ctx)
            :note         note
            :interrupted? interrupted?
            :approved-by  (when r2 approve-by)
            :status       (:status final)
            :disposition  (get-in final [:state :disposition])
            :record       (get-in final [:state :record])})
    final))

(defn run-demo!
  "Drives a freshly seeded store through a scenario that reaches every
  disposition this actor can produce, and every one of
  `machinetool.governor`'s eight HARD checks.

  Approved lifecycle (unit-1, JPN, accuracy 0.05 inside its own
  [-0.10,0.10] spec bounds, no unresolved accuracy-test defect):
  intake auto-commits (the only op in phase 3's `:auto` set), then a
  design-rules requirements verification, an ISO 230 accuracy-test
  screening, a unit dispatch, an accuracy-certificate issuance and a
  maintenance/recall notice each escalate to the human and are
  approved.

  HARD governor refusals -- none of which ever reach a human:
    :no-spec-basis                  unit-2's own jurisdiction (ATL) is
                                    genuinely absent from
                                    `machinetool.facts/catalog`, so
                                    the advisor cites nothing. (No
                                    `:no-spec?` injection flag is
                                    used -- this repo's seed data
                                    already contains the case.)
    :evidence-incomplete            dispatching unit-2 with no
                                    jurisdictional evidence checklist
                                    satisfiable at all.
    :unit-accuracy-out-of-range     unit-3's own measured deviation
                                    (0.35) is outside its own recorded
                                    spec bounds, recomputed
                                    independently of the proposal.
    :accuracy-test-defect-unresolved
                                    unit-4's screening detects its own
                                    unresolved defect and HARD-holds
                                    on its own finding.
    :testlab-engagement-ref-missing an accuracy certificate for unit-3
                                    with no independent third-party
                                    testing-laboratory reference.
    :dispatch-ref-unverified        a maintenance notice naming unit-2,
                                    which this actor never dispatched.
    :already-dispatched             dispatching unit-1 a second time.
    :already-certified              certifying unit-1 a second time.

  Rollout-gate hold (NOT a governor refusal -- empty `:violations`):
    a design-rules verification attempted at phase 1, which has not
    enabled that write op yet.

  Returns {:db store :steps [..]} -- every value rendered below comes
  out of these two."
  []
  (let [db     (store/seed-db)
        actor  (op/build db)
        !steps (atom [])
        step   (fn [m] (step! !steps actor m))]

    ;; ---- unit-1: the full approved lifecycle ----
    (step {:tid "u1-intake" :note "phase-3 auto-commit (only op in the :auto set)"
           :request {:op :unit/intake :subject "unit-1"
                     :patch {:id "unit-1" :unit-name "Sakura CNC Lathe SL-04"}}})
    (step {:tid "u1-verify" :approve-by "op-1" :note "phase gate: not auto-eligible"
           :request {:op :design-rules/verify :subject "unit-1"}})
    (step {:tid "u1-screen" :approve-by "op-1" :note "phase gate: not auto-eligible"
           :request {:op :accuracy-test/screen :subject "unit-1"}})
    (step {:tid "u1-dispatch" :approve-by "op-1" :note "high-stakes: never auto at any phase"
           :request {:op :actuation/dispatch-unit :subject "unit-1"}})
    (step {:tid "u1-certify" :approve-by "op-1" :note "high-stakes + third-party reference"
           :request {:op :actuation/issue-accuracy-certificate :subject "unit-1"
                     :certification/testlab-engagement-ref testlab-engagement-ref}})
    (step {:tid "u1-notice" :approve-by "op-1" :note "high-stakes: equipment already in the field"
           :request {:op :issue-maintenance-notice :subject "unit-1"}})
    ;; A SECOND notice for the SAME unit -- legal, and deliberate: unlike
    ;; dispatch/certificate there is no double-issuance guard here, so
    ;; this is the case that proves the approver table addresses records
    ;; positionally instead of by unit id.
    (step {:tid "u1-notice-2" :approve-by "op-2" :note "second notice for the same unit is legal"
           :request {:op :issue-maintenance-notice :subject "unit-1"}})

    ;; ---- unit-2 (ATL): a jurisdiction this actor has no spec-basis for ----
    (step {:tid "u2-verify" :note "HARD: no official spec-basis for ATL"
           :request {:op :design-rules/verify :subject "unit-2"}})
    (step {:tid "u2-dispatch" :note "HARD: required evidence can never be satisfied"
           :request {:op :actuation/dispatch-unit :subject "unit-2"}})
    (step {:tid "u2-notice" :note "HARD: unit-2 was never dispatched by this actor"
           :request {:op :issue-maintenance-notice :subject "unit-2"}})

    ;; ---- unit-3: verified, but out of its own accuracy spec ----
    (step {:tid "u3-verify" :approve-by "op-1" :note "clears the evidence checklist"
           :request {:op :design-rules/verify :subject "unit-3"}})
    (step {:tid "u3-dispatch" :note "HARD: 0.35 outside its own [-0.10,0.10] bounds"
           :request {:op :actuation/dispatch-unit :subject "unit-3"}})
    (step {:tid "u3-certify" :note "HARD: no independent testlab engagement reference"
           :request {:op :actuation/issue-accuracy-certificate :subject "unit-3"}})

    ;; ---- unit-4: an unresolved accuracy-test defect ----
    (step {:tid "u4-screen" :note "HARD: screening HARD-holds on its own finding"
           :request {:op :accuracy-test/screen :subject "unit-4"}})

    ;; ---- double-actuation guards, off dedicated booleans ----
    (step {:tid "u1-dispatch-again" :note "HARD: unit-1 already dispatched"
           :request {:op :actuation/dispatch-unit :subject "unit-1"}})
    (step {:tid "u1-certify-again" :note "HARD: unit-1 already certified"
           :request {:op :actuation/issue-accuracy-certificate :subject "unit-1"
                     :certification/testlab-engagement-ref testlab-engagement-ref}})

    ;; ---- the rollout gate, not the governor ----
    (step {:tid "u2-verify-phase1" :context junior-operator
           :note "rollout gate: phase 1 has not enabled this write op"
           :request {:op :design-rules/verify :subject "unit-1"}})

    {:db db :steps @!steps}))

;; ----------------------------- classification -----------------------------

(defn- governor-hold?
  "A ledger hold fact the GOVERNOR produced: it carries at least one
  violation. See ns docstring invariant 1."
  [f]
  (and (= :hold (:disposition f))
       (seq (:violations f))))

(defn- phase-hold?
  "A ledger hold fact the ROLLOUT PHASE GATE produced: same `:t`, same
  `:disposition`, but NO violations -- the governor did not refuse
  anything."
  [f]
  (and (= :hold (:disposition f))
       (empty? (:violations f))))

(defn hard-holds  [db] (filterv governor-hold? (store/ledger db)))
(defn gate-holds  [db] (filterv phase-hold?    (store/ledger db)))
(defn commits     [db] (filterv #(= :committed (:t %)) (store/ledger db)))

(defn hard-hold-kinds
  "Distinct violation rules the governor actually raised in this run."
  [db]
  (->> (hard-holds db) (mapcat :violations) (map :rule) distinct sort vec))

;; ----------------------------- approver attribution -----------------------------

(def ^:private approver-keys
  #{:approved-by :approved_by :approver "approved_by" "approved-by" "approver"})

(defn- approver-in
  "Walks any nested artifact looking for an approver field. Returns the
  approver, or nil. Deliberately a SCAN rather than a fixed key path:
  if `machinetool.store` later starts persisting the approver onto the
  dispatch/evidence/notice records, this page starts reporting it with
  no edit here."
  [x]
  (cond
    (map? x) (or (some (fn [[k v]] (when (and (contains? approver-keys k) (some? v)) v)) x)
                 (some approver-in (vals x)))
    (sequential? x) (some approver-in x)
    :else nil))

(def ^:private history-kinds
  "Effects whose artifact lands in an append-only history rather than at
  an addressable key."
  {:unit/mark-dispatched     ["unit-dispatch draft"        store/dispatch-history]
   :unit/mark-certified      ["accuracy-certificate draft" store/evidence-history]
   :maintenance-notice/issue ["maintenance-notice draft"   store/maintenance-notice-history]})

(defn- history-positions
  "thread-id -> the position its artifact occupies in the append-only
  history its effect writes to.

  `machinetool.store/commit-record!` appends in commit order, and the
  step log is in run order, so the n-th committed step with a given
  effect is the n-th entry of that effect's history. Positional on
  purpose: a unit may legitimately receive MORE THAN ONE maintenance
  notice (`machinetool.registry/register-maintenance-notice` has no
  double-issuance guard, unlike dispatch/certificate), so looking the
  record up by `unit_id` would make the second notice inherit the
  FIRST one's approver."
  [steps]
  (first
   (reduce (fn [[acc counts] {:keys [thread-id record disposition]}]
             (let [e (:effect record)]
               (if (and (= :commit disposition) (contains? history-kinds e))
                 [(assoc acc thread-id (get counts e 0)) (update counts e (fnil inc 0))]
                 [acc counts])))
           [{} {}] steps)))

(defn- artifact-for
  "The artifact a committed step actually wrote, fetched back OUT of the
  store. Traced from the run's own `:record` (its `:effect`/`:path`)
  plus its positional slot, never by joining on `[op subject]`."
  [db positions {:keys [record thread-id]}]
  (let [{:keys [effect path]} record
        id (first path)]
    (if-let [[kind history-fn] (history-kinds effect)]
      (let [r (nth (vec (history-fn db)) (get positions thread-id -1) nil)]
        {:kind kind :ref (get r "record_id") :artifact r})
      (case effect
        :unit/upsert              {:kind "unit record"               :ref id
                                   :artifact (store/unit db id)}
        :verification/set         {:kind "design-rules verification" :ref id
                                   :artifact (store/requirements-verification-of db id)}
        :accuracy-test-screen/set {:kind "ISO 230 screening"         :ref id
                                   :artifact (store/accuracy-screen-of db id)}
        {:kind (str effect) :ref id :artifact nil}))))

(defn approver-attribution
  "MEASURED, per approved step: did the approver the human supplied
  actually survive into the committed artifact in the store?

  `machinetool.operation` puts `:approved-by` on the commit record's
  `:payload` only; whether that reaches the SSoT depends on what the
  store's `commit-record!` does with `:payload` for that `:effect`.
  This function does not assume either answer -- it reads the artifact
  back and looks."
  [db steps]
  (let [positions (history-positions steps)]
    (->> steps
       (filter #(and (:approved-by %) (= :commit (:disposition %))))
       (mapv (fn [{:keys [thread-id op approved-by] :as s}]
               (let [{:keys [kind ref artifact]} (artifact-for db positions s)
                     retained (approver-in artifact)]
                 {:thread-id thread-id
                  :op op
                  :kind kind
                  :ref ref
                  :approved-by approved-by
                  :retained? (some? retained)
                  :retained retained}))))))

;; ----------------------------- html helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- kw [v] (esc (str v)))

(defn- code [v] (str "<code>" (kw v) "</code>"))

(defn- tr [& cells]
  (str "        <tr>" (apply str (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- table [headers rows]
  (str "    <table>\n"
       "      <thead><tr>" (apply str (map #(str "<th>" (esc %) "</th>") headers)) "</tr></thead>\n"
       "      <tbody>\n"
       (str/join "\n" rows) "\n"
       "      </tbody>\n"
       "    </table>\n"))

(defn- section [title lead body]
  (str "  <section class=\"card\">\n"
       "    <h2>" (esc title) "</h2>\n"
       "    <p class=\"muted\">" lead "</p>\n"
       body
       "  </section>\n"))

(defn- ok   [s] (str "<span class=\"ok\">" s "</span>"))
(defn- warn [s] (str "<span class=\"warn\">" s "</span>"))
(defn- err  [s] (str "<span class=\"err\">" s "</span>"))
(defn- crit [s] (str "<span class=\"critical\">" s "</span>"))
(defn- mute [s] (str "<span class=\"muted\">" s "</span>"))

;; ----------------------------- rows -----------------------------

(defn- unit-row
  [{:keys [id unit-name jurisdiction status
           positioning-accuracy-deviation-actual
           positioning-accuracy-deviation-min
           positioning-accuracy-deviation-max
           accuracy-test-defect-unresolved?
           unit-dispatched? accuracy-certified?
           dispatch-number evidence-number]}]
  (let [in-spec? (and (>= positioning-accuracy-deviation-actual positioning-accuracy-deviation-min)
                      (<= positioning-accuracy-deviation-actual positioning-accuracy-deviation-max))
        ;; the glyph must not assert a membership that is false
        acc (str positioning-accuracy-deviation-actual
                 (if in-spec? " &isin; [" " &notin; [")
                 positioning-accuracy-deviation-min
                 "," positioning-accuracy-deviation-max "]")]
    (tr (esc id)
        (esc unit-name)
        (esc jurisdiction)
        (if in-spec? (ok acc) (err acc))
        (if accuracy-test-defect-unresolved? (crit "unresolved") (ok "none recorded"))
        (kw status)
        (if unit-dispatched? (ok (esc dispatch-number)) (mute "&mdash;"))
        (if accuracy-certified? (ok (esc evidence-number)) (mute "&mdash;")))))

(defn- outcome-cell [{:keys [disposition approved-by interrupted?]} ledger-fact]
  (cond
    (and (= :commit disposition) approved-by) (ok "approved &amp; committed")
    (= :commit disposition)                   (ok "auto-committed")
    (and (= :hold disposition) ledger-fact (governor-hold? ledger-fact))
    (crit (str "HARD hold &middot; "
               (esc (str/join ", " (map (comp name :rule) (:violations ledger-fact))))))
    (and (= :hold disposition) ledger-fact (phase-hold? ledger-fact))
    (warn (str "rollout-gate hold &middot; " (esc (name (:phase-reason ledger-fact :phase-gate)))))
    (= :hold disposition)                     (warn "hold")
    interrupted?                              (warn "awaiting approval")
    :else                                     (mute "no disposition")))

(defn- step-row [ledger-by-tid {:keys [thread-id op subject phase note] :as s}]
  (tr (code thread-id)
      (code op)
      (esc subject)
      (esc phase)
      (outcome-cell s (get ledger-by-tid thread-id))
      (mute (esc note))))

(defn- hard-hold-row [{:keys [op subject violations]}]
  (str/join "\n"
            (for [v violations]
              (tr (code op)
                  (esc subject)
                  (crit (esc (name (:rule v))))
                  (esc (:detail v))))))

(defn- gate-hold-row [{:keys [op subject phase phase-reason violations]}]
  (tr (code op)
      (esc subject)
      (esc phase)
      (warn (esc (name (or phase-reason :phase-gate))))
      (mute (str "violations: " (count violations)))))

(defn- ledger-row [i {:keys [t op actor subject disposition basis summary]}]
  (tr (esc i)
      (kw t)
      (code op)
      (esc actor)
      (esc subject)
      (kw disposition)
      (if (seq basis)
        (err (esc (str/join ", " (map name basis))))
        (mute "&mdash;"))
      (esc (or summary ""))))

(defn- gate-contract-row [op]
  (let [auto-phases (->> phase/phases
                         (filter (fn [[_ {:keys [auto]}]] (contains? auto op)))
                         (map key) sort vec)
        write-phases (->> phase/phases
                          (filter (fn [[_ {:keys [writes]}]] (contains? writes op)))
                          (map key) sort vec)
        stakes? (contains? governor/high-stakes op)]
    (tr (code op)
        (if stakes? (crit "high-stakes") (mute "&mdash;"))
        (esc (str/join ", " write-phases))
        (if (seq auto-phases)
          (ok (esc (str/join ", " auto-phases)))
          (crit "never, at any phase"))
        (if stakes?
          (warn "always a human machine-tool engineer")
          (mute "human approval unless auto-eligible")))))

(defn- draft-row [kind r]
  (tr (esc kind)
      (esc (get r "record_id"))
      (esc (get r "kind"))
      (esc (get r "unit_id"))
      (esc (get r "jurisdiction"))
      (if-let [d (get r "dispatch_ref")] (esc d) (mute "&mdash;"))))

(defn- attribution-row [{:keys [thread-id op kind ref approved-by retained? retained]}]
  (tr (code thread-id)
      (code op)
      (esc kind)
      (esc ref)
      (esc approved-by)
      (if retained?
        (ok (str (esc retained) " &mdash; retained on record"))
        (warn "(audit only &mdash; not retained on record)"))))

(defn- coverage-row [iso3]
  (let [sb (facts/spec-basis iso3)]
    (tr (esc iso3)
        (if sb (ok (esc (:name sb))) (err "not in machinetool.facts/catalog"))
        (if sb (esc (:owner-authority sb)) (mute "&mdash;"))
        (if sb (esc (count (:required-evidence sb))) (crit "0 &mdash; no spec-basis")))))

;; ----------------------------- document -----------------------------

(defn render
  "Renders the whole console from a store `db` that has already been
  driven by `run-demo!`, plus that run's own step log."
  [db steps]
  (let [ledger        (vec (store/ledger db))
        units         (store/all-units db)
        hard          (hard-holds db)
        gate          (gate-holds db)
        committed     (commits db)
        ;; hold facts land on the ledger in run order; the step log is
        ;; in the same order, so pair each held step with its own fact
        ;; rather than joining on [op subject] (not unique here).
        held-steps    (filter #(= :hold (:disposition %)) steps)
        hold-facts    (filterv #(= :hold (:disposition %)) ledger)
        ledger-by-tid (zipmap (map :thread-id held-steps) hold-facts)
        attribution   (approver-attribution db steps)
        jurisdictions (vec (sort (distinct (map :jurisdiction units))))
        coverage      (facts/coverage jurisdictions)]
    (str
     "<html lang=\"en\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
     "<title>cloud-itonami-isic-2822 &middot; metal-forming machinery &amp; machine tools</title><style>\n"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Metal-forming machinery &amp; machine tools (ISIC 2822) — Operator Console</h1>\n"
     "  <span class=\"badge\">build-time sample · governor-gated · dispatch &amp; certificate issuance are always a human call</span>\n"
     "</header>\n"
     "<main>\n"

     (section "Units (SSoT after this run)"
              (str "Read back out of <code>machinetool.store</code> after the scenario below ran. "
                   "Positioning-accuracy deviation is the unit's own measured ISO 230-2 figure "
                   "against its own recorded spec bounds; "
                   (count units) " units, "
                   (count (filter :unit-dispatched? units)) " dispatched, "
                   (count (filter :accuracy-certified? units)) " certified.")
              (table ["Unit" "Name" "Juris." "Positioning accuracy" "Accuracy-test defect"
                      "Status" "Dispatch no." "Evidence no."]
                     (map unit-row units)))

     (section "Operation runs (this scenario)"
              (str "Each row is one <code>langgraph</code> graph run through "
                   "<code>machinetool.operation</code>, identified by its own thread-id. "
                   (count steps) " runs: "
                   (count (filter #(= :commit (:disposition %)) steps)) " committed, "
                   (count held-steps) " held.")
              (table ["Thread" "Op" "Subject" "Phase" "Outcome" "Why"]
                     (map (partial step-row ledger-by-tid) steps)))

     (section "HARD governor refusals"
              (str "The governor itself refused these — a human approver cannot override any of them, "
                   "and none of them ever reached the approval interrupt. "
                   (count hard) " refusals covering "
                   (count (hard-hold-kinds db)) " of "
                   "the governor's checks. Rule names and detail strings are the governor's own output.")
              (table ["Op" "Subject" "Rule" "Detail (machinetool.governor)"]
                     (map hard-hold-row hard)))

     (section "Rollout-gate holds (NOT governor refusals)"
              (str "Held by <code>machinetool.phase/gate</code> because the rollout phase had not "
                   "enabled the write op yet. These land as the same <code>:governor-hold</code> "
                   "ledger fact but carry an EMPTY <code>:violations</code> vector, so they are "
                   "counted and shown separately — a hold here does not mean the governor found "
                   "anything wrong. " (count gate) " in this run.")
              (table ["Op" "Subject" "Phase" "Reason" "Governor violations"]
                     (map gate-hold-row gate)))

     (section "Action gate (contract)"
              (str "Derived at build time from <code>machinetool.phase/phases</code> and "
                   "<code>machinetool.governor/high-stakes</code>, not hand-described. "
                   "An op that is auto-eligible at no phase can never commit without a human, "
                   "whatever the governor says.")
              (table ["Op" "Stake" "Writable at phase" "Auto-commit at phase" "Human gate"]
                     (map gate-contract-row (sort-by str phase/write-ops))))

     (section "Approver attribution (measured, not assumed)"
              (str "For every approved commit, the artifact was fetched back out of the store and "
                   "scanned for an approver field. Where the approver is not on the record, that is "
                   "stated rather than silently omitted: <code>machinetool.operation</code> attaches "
                   "<code>:approved-by</code> to the commit record's <code>:payload</code>, and only "
                   "the effects whose <code>commit-record!</code> branch persists <code>:payload</code> "
                   "keep it. This table is derived at render time, so it self-corrects if the store "
                   "starts retaining more.")
              (table ["Thread" "Op" "Artifact" "Reference" "Approved by" "On the committed record?"]
                     (map attribution-row attribution)))

     (section "Registry drafts produced by this run"
              (str "Built by <code>machinetool.registry</code> — jurisdiction-scoped sequence numbers, "
                   "every certificate UNSIGNED (<code>status: draft-unsigned</code>): signing and "
                   "filing are the manufacturer's own acts, not this actor's.")
              (table ["Kind" "Record id" "Draft kind" "Unit" "Juris." "Dispatch ref"]
                     (concat (map (partial draft-row "unit dispatch") (store/dispatch-history db))
                             (map (partial draft-row "accuracy certificate") (store/evidence-history db))
                             (map (partial draft-row "maintenance notice") (store/maintenance-notice-history db)))))

     (section "Jurisdiction spec-basis coverage"
              (str "Coverage is reported honestly: a jurisdiction absent from "
                   "<code>machinetool.facts/catalog</code> has NO spec-basis, and the governor "
                   "refuses to let the advisor invent one. Requested "
                   (:requested coverage) ", covered " (:covered coverage)
                   (when (seq (:missing-jurisdictions coverage))
                     (str ", missing " (esc (str/join ", " (:missing-jurisdictions coverage))))) ".")
              (table ["Juris." "Name" "Owner authority" "Required evidence items"]
                     (map coverage-row jurisdictions)))

     (section "Audit ledger (this run)"
              (str "The append-only decision log <code>machinetool.store</code> wrote — "
                   (count ledger) " facts: " (count committed) " commits, "
                   (count hard) " HARD governor refusals, " (count gate) " rollout-gate holds.")
              (table ["#" "Fact" "Op" "Actor" "Subject" "Disposition" "Basis" "Summary"]
                     (map-indexed ledger-row ledger)))

     "  <footer>\n"
     "    <p>Generated by <code>machinetool.render-html</code> "
     "(<code>clojure -M:render-html</code>) by executing the real actor stack: "
     "<code>machinetool.operation</code> &rarr; <code>machinetool.governor</code> &rarr; "
     "<code>machinetool.store</code>. No value on this page is hand-written. "
     "The generator refuses to write this file if the run produces no HARD governor hold.</p>\n"
     "  </footer>\n"
     "</main>\n"
     "</body></html>\n")))

;; ----------------------------- entry point -----------------------------

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db steps]} (run-demo!)
        hard (hard-holds db)
        gate (gate-holds db)
        done (commits db)]

    ;; Invariant 1 -- a console on which the governor never refused
    ;; anything is not evidence that the governor works. Counted on
    ;; NON-EMPTY :violations, so the rollout-gate holds below cannot
    ;; satisfy it.
    (when (empty? hard)
      (throw (ex-info (str "REFUSING to write " out
                           ": the run produced zero HARD governor holds"
                           " (ledger facts with a non-empty :violations vector)."
                           " Rollout-gate holds do not count.")
                      {:out out
                       :ledger-facts (count (store/ledger db))
                       :rollout-gate-holds (count gate)
                       :hard-governor-holds 0})))

    ;; Invariant 2 -- nor is an all-refusals page.
    (when (empty? done)
      (throw (ex-info (str "REFUSING to write " out
                           ": the run produced zero commits, so the approved path is unproven.")
                      {:out out :hard-governor-holds (count hard)})))

    (spit out (render db steps))
    (println "wrote" out)
    (println "  operation runs      " (count steps))
    (println "  ledger facts        " (count (store/ledger db)))
    (println "  commits             " (count done))
    (println "  HARD governor holds " (count hard) (hard-hold-kinds db))
    (println "  rollout-gate holds  " (count gate))
    (println "  dispatch drafts     " (count (store/dispatch-history db)))
    (println "  certificate drafts  " (count (store/evidence-history db)))
    (println "  notice drafts       " (count (store/maintenance-notice-history db)))))
