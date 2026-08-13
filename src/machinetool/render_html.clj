(ns machinetool.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo. Before this namespace
  existed, `docs/samples/operator-console.html` was a hand-written page:
  it used real seed ids, but NO generator ever produced it, so nothing
  connected its numbers to a run. This namespace replaces it with a page
  that is produced by driving the REAL actor stack --
  `machinetool.operation` (langgraph StateGraph) -> `machinetool.governor`
  -> `machinetool.store` -- exactly the way `machinetool.sim`
  (`clojure -M:dev:run`) does, and then reading the resulting store back.

  Every id, number, jurisdiction, violation rule, violation detail,
  record_id and count on the page is read out of THIS run's governor
  verdicts, graph audit channel and store registers. Nothing is typed in.
  Where a value genuinely cannot be obtained from the store, the page
  says so rather than inventing it (see `approver-attribution-rows`,
  which MEASURES per-register approver retention instead of asserting
  it -- if the store is later changed to keep the approver, the page
  self-corrects on the next render).

  Deterministic: no timestamps, no wall-clock, no map-iteration order
  leaks (every map is walked in an explicit or sorted order), so two
  consecutive renders from the same seed are byte-identical.

  Build-time invariant: `-main` REFUSES to write the page unless this
  run produced at least one governor HARD hold that carries a non-empty
  violation list. The two-stage check matters -- `machinetool.phase/gate`
  can also emit a hold (`:phase-disabled`) whose violation list is
  EMPTY, and a naive `(count holds)` would be satisfied by that alone
  while the page showed no actual refusal.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [jp-go-dds.skin]
            [langgraph.graph :as g]
            [machinetool.export :as export]
            [machinetool.facts :as facts]
            [machinetool.governor :as governor]
            [machinetool.operation :as op]
            [machinetool.phase :as phase]
            [machinetool.store :as store]))

;; ----------------------------- scenario -----------------------------

(def ^:private approver "op-1")

(defn- operator [phase]
  {:actor-id approver :actor-role :machine-tool-engineer :phase phase})

(def ^:private testlab-ref
  "The independent third-party accredited testing laboratory reference
  (`cloud-itonami-isic-7120`) an `:actuation/issue-accuracy-certificate`
  proposal MUST carry since `machinetool.governor` ns docstring Addendum
  2. Supplied by the CALLER here exactly as `machinetool.sim` supplies
  it -- the advisor only echoes it, and the governor independently
  re-verifies its wire shape (`registry/testlab-engagement-ref-fields-
  present?`) before commit is ever reached."
  {:testlab-engagement-ref/id "engagement-1"
   :testlab-engagement-ref/source-actor "cloud-itonami-isic-7120"
   :testlab-engagement-ref/certification-number "JPN-CERT-000000"})

(defn- run!
  "Executes one operation against `actor` on its own thread id, then --
  when the actor interrupted for human approval and `approval` is
  supplied -- resumes it with that decision. Records the FINAL graph
  state under `runs` so the renderer reads each run's own audit channel
  directly instead of re-deriving it by joining on `[op subject]`,
  which is not unique in this scenario (unit-1 is the subject of both an
  approved and a refused `:actuation/dispatch-unit`)."
  [runs actor tid phase request & [approval]]
  (let [ctx (operator phase)
        r1 (g/run* actor {:request request :context ctx} {:thread-id tid})
        r2 (when (and approval (= :escalate (get-in r1 [:state :disposition])))
             (g/run* actor {:approval approval} {:thread-id tid :resume? true}))
        final (:state (or r2 r1))]
    (swap! runs conj {:thread tid
                      :op (:op request)
                      :subject (:subject request)
                      :phase phase
                      :interrupted? (some? r2)
                      :state final})
    final))

(defn- approve [] {:status :approved :by approver})
(defn- reject  [] {:status :rejected  :by approver})

(defn run-demo!
  "Drives a freshly seeded store through a scenario that reaches every
  disposition this actor can produce, and every HARD violation rule its
  governor implements that the seed can actually exercise.

  Approved path (unit-1, a JPN CNC lathe whose measured ISO 230-2
  positioning-accuracy deviation is inside its own recorded spec bounds
  and which carries no unresolved accuracy-test defect): intake
  (auto-commits at phase 3 -- the only op in any phase's `:auto` set),
  design-rules verification, ISO 230 accuracy-test screening, unit
  dispatch, accuracy-certificate issuance carrying the mandatory
  third-party testlab engagement reference, and finally a maintenance/
  recall notice referencing that unit's OWN recorded dispatch-number.
  The last three ALWAYS escalate -- they are permanently absent from
  every phase's `:auto` set and permanently present in the governor's
  `high-stakes` set -- so each one is committed only after a human
  machine-tool engineer approves.

  Human refusal (unit-4): a governor-clean design-rules verification the
  human operator REJECTS. This is a third category, distinct from both
  a governor refusal and a rollout-gate hold.

  Rollout-phase gate (unit-1 at phase 1): a governor-clean
  `:design-rules/verify` that phase 1 does not yet allow to write at
  all. It holds with `:phase-disabled` and an EMPTY violation list --
  no compliance rule was broken, the rollout simply has not reached
  this op. Deliberately included so the page can show that a hold count
  alone is not evidence of a refusal.

  Governor HARD refusals -- eight distinct rules, none of which ever
  reaches a human:
    :no-spec-basis                  unit-2, design-rules verification
                                    for a jurisdiction with no entry in
                                    `machinetool.facts/catalog`
    :evidence-incomplete            unit-2, dispatch attempted with no
                                    verification on file at all
    :unit-accuracy-out-of-range     unit-3, measured deviation outside
                                    its own recorded spec bounds
    :accuracy-test-defect-unresolved unit-4, the screening op's OWN
                                    finding refuses itself
    :testlab-engagement-ref-missing unit-3, accuracy certificate with no
                                    independent third-party reference
    :dispatch-ref-unverified        unit-3, maintenance notice naming a
                                    unit this actor never dispatched
    :already-dispatched             unit-1, second dispatch attempt
    :already-certified              unit-1, second certificate issuance

  Returns `{:db store :runs [..]}`."
  []
  (let [db (store/seed-db)
        actor (op/build db)
        runs (atom [])]

    ;; --- unit-1: the full approved lifecycle -------------------------
    (run! runs actor "t01-intake" 3
          {:op :unit/intake :subject "unit-1"
           :patch {:id "unit-1" :unit-name "Sakura CNC Lathe SL-04"}})
    (run! runs actor "t02-verify" 3
          {:op :design-rules/verify :subject "unit-1"} (approve))
    (run! runs actor "t03-screen" 3
          {:op :accuracy-test/screen :subject "unit-1"} (approve))
    (run! runs actor "t04-dispatch" 3
          {:op :actuation/dispatch-unit :subject "unit-1"} (approve))
    (run! runs actor "t05-certify" 3
          {:op :actuation/issue-accuracy-certificate :subject "unit-1"
           :certification/testlab-engagement-ref testlab-ref} (approve))
    (run! runs actor "t06-notice" 3
          {:op :issue-maintenance-notice :subject "unit-1"} (approve))

    ;; --- unit-3: verified, then refused on its own measured accuracy --
    (run! runs actor "t07-verify" 3
          {:op :design-rules/verify :subject "unit-3"} (approve))
    (run! runs actor "t08-dispatch" 3
          {:op :actuation/dispatch-unit :subject "unit-3"})
    (run! runs actor "t09-certify" 3
          {:op :actuation/issue-accuracy-certificate :subject "unit-3"})
    (run! runs actor "t10-notice" 3
          {:op :issue-maintenance-notice :subject "unit-3"})

    ;; --- unit-2: unregistered jurisdiction, so never verifiable -------
    (run! runs actor "t11-verify" 3
          {:op :design-rules/verify :subject "unit-2" :no-spec? true})
    (run! runs actor "t12-dispatch" 3
          {:op :actuation/dispatch-unit :subject "unit-2"})

    ;; --- unit-4: screening refuses itself; the human refuses the rest -
    (run! runs actor "t13-screen" 3
          {:op :accuracy-test/screen :subject "unit-4"})
    (run! runs actor "t14-verify" 3
          {:op :design-rules/verify :subject "unit-4"} (reject))

    ;; --- unit-1 again: the two single-shot actuation guards -----------
    (run! runs actor "t15-dispatch" 3
          {:op :actuation/dispatch-unit :subject "unit-1"})
    (run! runs actor "t16-certify" 3
          {:op :actuation/issue-accuracy-certificate :subject "unit-1"
           :certification/testlab-engagement-ref testlab-ref})

    ;; --- rollout-phase gate: governor-clean, phase not there yet ------
    (run! runs actor "t17-phase1" 1
          {:op :design-rules/verify :subject "unit-1"} (approve))

    {:db db :runs @runs}))

;; ----------------------------- run analysis -----------------------------

(defn- audit-of [run] (get-in run [:state :audit] []))

(defn- fact-of [run t] (first (filter #(= t (:t %)) (audit-of run))))

(defn- approval-of
  "The approver this run's OWN audit channel recorded, or nil. Read per
  run, never joined on `[op subject]`."
  [run]
  (when-let [f (fact-of run :approval-granted)] (:by f)))

(defn- rejection-of [run]
  (when (some #(= :approval-rejected (:t %)) (audit-of run)) approver))

(defn- verdict-of [run] (get-in run [:state :verdict]))

(defn- hard-hold-facts
  "Ledger facts this run's governor wrote as a HARD refusal: `:t
  :governor-hold` carrying at least one violation. A `:phase-disabled`
  hold has an EMPTY violation list and is deliberately NOT counted here
  -- see the ns docstring."
  [ledger]
  (filterv #(and (= :governor-hold (:t %)) (seq (:violations %))) ledger))

(defn- phase-gate-hold-facts
  "Holds the ROLLOUT gate produced rather than the governor: they carry
  a `:phase-reason` and no violations."
  [ledger]
  (filterv #(and (= :governor-hold (:t %)) (empty? (:violations %))) ledger))

(defn- human-rejection-facts [ledger]
  (filterv #(= :approval-rejected (:t %)) ledger))

;; ----------------------------- approver attribution -----------------------------

(def ^:private approver-keys
  "Every key shape an approver could plausibly land under. Checked by
  PRESENCE against the persisted registers -- this is a measurement, not
  an assumption about this repo's store."
  [:approved-by :approver :approved_by :by
   "approved-by" "approver" "approved_by" "by"])

(defn- approver-in
  "The approver value actually present in `m`, with the key it was found
  under, or nil."
  [m]
  (when (map? m)
    (some (fn [k] (when-some [v (get m k)] {:key k :value v})) approver-keys)))

(defn- any-approver-in [ms] (some approver-in ms))

(defn- approver-attribution-rows
  "MEASURES, per persisted register, whether the human approver this run
  recorded in the graph's own audit channel survived into the store.

  This is derived at render time from the store's actual contents, not
  hard-coded: if `machinetool.store/commit-record!` is later changed to
  persist `:payload` (which is where `machinetool.operation`'s
  `:request-approval` node puts `:approved-by`) for the actuation
  effects too, these rows flip to `retained` with no edit here."
  [db runs]
  (let [approved (filter approval-of runs)
        ;; the register each committed effect actually writes into
        probe
        {:unit/upsert
         (fn [subject] [(store/unit db subject)])
         :verification/set
         (fn [subject] [(store/requirements-verification-of db subject)])
         :accuracy-test-screen/set
         (fn [subject] [(store/accuracy-screen-of db subject)])
         :unit/mark-dispatched
         (fn [subject] (into [(store/unit db subject)]
                             (filter #(= subject (get % "unit_id"))
                                     (store/dispatch-history db))))
         :unit/mark-certified
         (fn [subject] (into [(store/unit db subject)]
                             (filter #(= subject (get % "unit_id"))
                                     (store/evidence-history db))))
         :maintenance-notice/issue
         (fn [subject] (filterv #(= subject (get % "unit_id"))
                                (store/maintenance-notice-history db)))}]
    (for [run approved
          :let [record (get-in run [:state :record])
                effect (:effect record)
                subject (:subject run)
                persisted ((get probe effect (constantly [])) subject)
                found (any-approver-in persisted)
                payload-had? (some? (approver-in (:payload record)))]]
      {:op (:op run)
       :subject subject
       :effect effect
       :approver-in-audit (approval-of run)
       :approver-in-commit-record (if payload-had? "present in :payload" "absent")
       :approver-in-store (if found
                            (str "retained under " (pr-str (:key found))
                                 " = " (pr-str (:value found)))
                            "NOT retained")
       :retained? (some? found)})))

;; ----------------------------- html -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw-name [v] (if (keyword? v) (name v) (str v)))

(defn- code [v] (str "<code>" (esc v) "</code>"))

(defn- cls [c v] (str "<span class=\"" c "\">" (esc v) "</span>"))

(defn- na
  "The page NEVER invents a value. When the store has nothing to give,
  say exactly that."
  [] "<span class=\"muted\">not recorded by the store</span>")

(defn- row [& cells]
  (str "        <tr>" (str/join (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- table [headers rows]
  (if (empty? rows)
    "    <p class=\"muted\">This run produced no rows for this table.</p>\n"
    (str "    <table>\n"
         "      <thead><tr>"
         (str/join (map #(str "<th>" (esc %) "</th>") headers))
         "</tr></thead>\n"
         "      <tbody>\n"
         (str/join "\n" rows) "\n"
         "      </tbody>\n"
         "    </table>\n")))

(defn- section [title lede body]
  (str "  <section class=\"card\">\n"
       "    <h2>" (esc title) "</h2>\n"
       "    <p class=\"muted\">" lede "</p>\n"
       body
       "  </section>\n"))

;; --- individual sections ---

(defn- units-section [db]
  (section
   "Units (store SSoT after this run)"
   (str "Read back from " (code "machinetool.store/all-units") " after the scenario below. "
        "Accuracy bounds and the unresolved-defect flag are the unit's own permanent "
        "ground-truth fields; " (code ":unit-dispatched?") " / " (code ":accuracy-certified?")
        " are the dedicated single-shot actuation guards (never a " (code ":status") " value).")
   (table ["Unit" "Name" "Jurisdiction" "ISO 230-2 deviation vs spec" "Unresolved defect"
           "Dispatched" "Certified" "dispatch-number" "evidence-number" "testlab engagement ref"]
          (for [u (store/all-units db)]
            (row (code (:id u))
                 (esc (:unit-name u))
                 (esc (:jurisdiction u))
                 (let [{:keys [positioning-accuracy-deviation-actual
                               positioning-accuracy-deviation-min
                               positioning-accuracy-deviation-max]} u
                       out? (and (number? positioning-accuracy-deviation-actual)
                                 (or (< positioning-accuracy-deviation-actual
                                        positioning-accuracy-deviation-min)
                                     (> positioning-accuracy-deviation-actual
                                        positioning-accuracy-deviation-max)))]
                   (cls (if out? "critical" "ok")
                        (str positioning-accuracy-deviation-actual
                             (if out? " outside [" " within [")
                             positioning-accuracy-deviation-min ","
                             positioning-accuracy-deviation-max "]")))
                 (if (:accuracy-test-defect-unresolved? u)
                   (cls "critical" "yes") (cls "ok" "no"))
                 (if (:unit-dispatched? u) (cls "ok" "yes") (cls "muted" "no"))
                 (if (:accuracy-certified? u) (cls "ok" "yes") (cls "muted" "no"))
                 (if-let [d (:dispatch-number u)] (code d) (na))
                 (if-let [e (:evidence-number u)] (code e) (na))
                 (if-let [r (:testlab-engagement-ref u)]
                   (code (:testlab-engagement-ref/certification-number r))
                   (na)))))))

(defn- disposition-cell [run]
  (let [d (get-in run [:state :disposition])
        v (verdict-of run)
        rejected? (rejection-of run)]
    (cond
      (and (= :hold d) (seq (:violations v)))
      (cls "critical" "HARD hold (governor)")
      rejected?
      (cls "err" "human rejected")
      (= :hold d)
      (cls "warn" "hold (rollout gate)")
      (approval-of run)
      (cls "ok" "approved &rarr; committed")
      (= :commit d)
      (cls "ok" "auto-committed")
      :else (cls "muted" (kw-name d)))))

(defn- gate-cell [run]
  (let [reqf (fact-of run :approval-requested)
        holdf (fact-of run :governor-hold)]
    (cond
      (seq (:violations holdf)) (esc (str/join ", " (map (comp kw-name :rule) (:violations holdf))))
      (:phase-reason holdf) (code (kw-name (:phase-reason holdf)))
      reqf (code (kw-name (:reason reqf)))
      :else (cls "muted" "governor clean, phase auto-eligible"))))

(defn- runs-section [runs]
  (section
   "Operation runs in this render (17 graph executions)"
   (str "Each row is one compiled " (code "machinetool.operation")
        " StateGraph run — <code>intake &rarr; advise &rarr; govern &rarr; decide</code>, then "
        (code ":commit") ", " (code ":hold") " or an "
        (code "interrupt-before #{:request-approval}") " pause resumed by a human decision. "
        "Disposition, gate reason and advisor confidence are read from that run's own "
        "verdict and audit channel.")
   (table ["Thread" "Op" "Subject" "Phase" "Advisor confidence" "Gate / rule" "Disposition"]
          (for [r runs]
            (row (code (:thread r))
                 (code (kw-name (:op r)))
                 (code (:subject r))
                 (esc (:phase r))
                 (if-let [c (:confidence (verdict-of r))] (esc c) (na))
                 (gate-cell r)
                 (disposition-cell r))))))

(defn- hard-holds-section [ledger]
  (let [holds (hard-hold-facts ledger)
        rules (sort (distinct (map (comp kw-name :rule) (mapcat :violations holds))))]
    (section
     (str "Governor HARD refusals — " (count holds) " holds, "
          (count rules) " distinct rules")
     (str "Written by " (code "machinetool.governor/hold-fact") " into the append-only ledger. "
          "A HARD violation is un-overridable: these never reach a human approver at all, so "
          "no row here has an approver. Rules observed in this run: "
          (str/join ", " (map code rules)) ". "
          "This table lists only what THIS run exercised — it is not a claim about the full "
          "check set.")
     (table ["Op" "Subject" "Rule" "Detail (verbatim from the governor)" "Advisor confidence"]
            (for [f holds
                  v (:violations f)]
              (row (code (kw-name (:op f)))
                   (code (:subject f))
                   (cls "critical" (kw-name (:rule v)))
                   (esc (:detail v))
                   (esc (:confidence f))))))))

(defn- phase-holds-section [ledger]
  (let [holds (phase-gate-hold-facts ledger)]
    (section
     (str "Rollout-phase gate holds — " (count holds))
     (str "A DIFFERENT thing from a governor refusal, deliberately kept in its own table. "
          (code "machinetool.phase/gate") " held these because the op is not yet enabled to "
          "write at that phase — no compliance rule was broken, so the violation list is "
          "empty. A build check that merely counted holds would be satisfied by rows like "
          "these while the page showed no actual refusal; "
          (code "machinetool.render-html/-main")
          " therefore requires at least one hold carrying a non-empty violation list.")
     (table ["Op" "Subject" "Phase" "Phase reason" "Violations"]
            (for [f holds]
              (row (code (kw-name (:op f)))
                   (code (:subject f))
                   (esc (:phase f))
                   (code (kw-name (:phase-reason f)))
                   (cls "muted" (str (count (:violations f)) " (empty by construction)"))))))))

(defn- approvals-section [runs]
  (let [decided (filter #(or (approval-of %) (rejection-of %)) runs)]
    (section
     (str "Human approval decisions — " (count decided))
     (str "Every write this actor makes outside phase-3 " (code ":unit/intake")
          " pauses at " (code ":request-approval") " and is resumed by a human machine-tool "
          "engineer. The three actuation ops are permanently absent from every phase's "
          (code ":auto") " set AND permanently members of "
          (code "machinetool.governor/high-stakes") " — two independent layers agree they "
          "are always a human call. Approver read from each run's own "
          (code ":approval-granted") " / " (code ":approval-rejected") " audit fact.")
     (table ["Thread" "Op" "Subject" "Escalation reason" "Decision" "Approver"]
            (for [r decided]
              (row (code (:thread r))
                   (code (kw-name (:op r)))
                   (code (:subject r))
                   (if-let [f (fact-of r :approval-requested)]
                     (code (kw-name (:reason f))) (na))
                   (if (approval-of r) (cls "ok" "approved") (cls "err" "rejected"))
                   (code (or (approval-of r) (rejection-of r)))))))))

(defn- attribution-section [db runs]
  (let [rows (approver-attribution-rows db runs)
        retained (count (filter :retained? rows))
        dropped (- (count rows) retained)]
    (section
     (str "Approver attribution — measured, " retained " retained / " dropped " dropped")
     (str "Not asserted: each row probes the register that its own commit effect actually "
          "writes into, and reports whether an approver key is present there. "
          (code "machinetool.operation") "'s " (code ":request-approval")
          " node puts " (code ":approved-by") " on the record's " (code ":payload")
          ", so whether it survives depends on whether that effect's branch of "
          (code "machinetool.store/commit-record!") " reads " (code ":payload") " or "
          (code ":value") ". Silently omitting this would leave a reader unable to tell "
          "&quot;nobody approved&quot; from &quot;the store did not keep it&quot;. If the "
          "store is changed to retain it, these rows flip with no edit to the renderer.")
     (table ["Op" "Effect" "Subject" "Approver in run audit" "In commit record" "In store register"]
            (for [r rows]
              (row (code (kw-name (:op r)))
                   (code (kw-name (:effect r)))
                   (code (:subject r))
                   (code (:approver-in-audit r))
                   (if (= "absent" (:approver-in-commit-record r))
                     (cls "muted" (:approver-in-commit-record r))
                     (cls "ok" (:approver-in-commit-record r)))
                   (if (:retained? r)
                     (cls "ok" (:approver-in-store r))
                     (cls "err" (:approver-in-store r)))))))))

(defn- verification-section [db]
  (section
   "Design-rules verifications on file"
   (str "Committed " (code ":verification/set") " payloads, read back per unit via "
        (code "machinetool.store/requirements-verification-of")
        ". The governor's " (code ":evidence-incomplete")
        " check re-derives sufficiency from these against "
        (code "machinetool.facts/required-evidence-satisfied?")
        " — it never trusts the advisor's confidence.")
   (table ["Unit" "Jurisdiction" "spec-basis provenance" "Checklist items" "Evidence satisfied"
           "Approver retained"]
          (for [u (store/all-units db)
                :let [v (store/requirements-verification-of db (:id u))]]
            (row (code (:id u))
                 (if v (esc (:jurisdiction v)) (na))
                 (if v (if (:spec-basis v) (esc (:spec-basis v)) (cls "critical" "none — refused"))
                     (na))
                 (if v (esc (count (:checklist v))) (na))
                 (if v
                   (if (facts/required-evidence-satisfied? (:jurisdiction v) (:checklist v))
                     (cls "ok" "yes") (cls "critical" "no"))
                   (na))
                 (if-let [a (and v (approver-in v))]
                   (cls "ok" (str (pr-str (:key a)) " = " (pr-str (:value a))))
                   (if v (cls "err" "not retained") (na))))))))

(defn- screening-section [db]
  (section
   "ISO 230 accuracy-test screenings on file"
   (str "Committed " (code ":accuracy-test-screen/set") " payloads via "
        (code "machinetool.store/accuracy-screen-of")
        ". A screening that finds an unresolved defect HARD-holds ITSELF, so it never "
        "commits and never appears here — which is why unit-4 has no row despite being "
        "screened in this run.")
   (table ["Unit" "Verdict" "Approver retained"]
          (for [u (store/all-units db)
                :let [s (store/accuracy-screen-of db (:id u))]
                :when s]
            (row (code (:id u))
                 (if (= :resolved (:verdict s)) (cls "ok" (kw-name (:verdict s)))
                     (cls "critical" (kw-name (:verdict s))))
                 (if-let [a (approver-in s)]
                   (cls "ok" (str (pr-str (:key a)) " = " (pr-str (:value a))))
                   (cls "err" "not retained")))))))

(defn- registry-section [db]
  (let [rows (concat
              (map #(vector "unit-dispatch" %) (store/dispatch-history db))
              (map #(vector "accuracy-certificate" %) (store/evidence-history db))
              (map #(vector "maintenance-notice" %) (store/maintenance-notice-history db)))]
    (section
     (str "Registry drafts produced — " (count rows))
     (str "Built by " (code "machinetool.registry") " with a jurisdiction-scoped sequence "
          "number. Every one is an UNSIGNED draft: signing and filing is the manufacturer's "
          "own act, not this actor's. There is no international check-digit standard for "
          "these references and this actor does not invent one.")
     (table ["Kind" "record_id" "Unit" "Jurisdiction" "dispatch_ref" "Immutable"]
            (for [[kind r] rows]
              (row (esc kind)
                   (code (get r "record_id"))
                   (code (get r "unit_id"))
                   (esc (get r "jurisdiction"))
                   (if-let [d (get r "dispatch_ref")] (code d) (cls "muted" "n/a"))
                   (if (get r "immutable") (cls "ok" "true") (cls "err" "false"))))))))

(defn- jurisdiction-section []
  (let [cov (facts/coverage)]
    (section
     (str "Jurisdiction spec-basis catalog — " (:covered cov) " of "
          (:requested cov) " seeded")
     (str "Read from " (code "machinetool.facts/catalog")
          ". Coverage is reported honestly: a jurisdiction absent from this table has NO "
          "spec-basis, and the governor refuses to let the advisor invent one. That is "
          "exactly why unit-2's " (code "ATL")
          " verification HARD-held above — " (code "ATL")
          " is not here and this actor will not guess its requirements. "
          (esc (:note cov)))
     (table ["ISO3" "Name" "Owner authority" "Legal basis" "Required evidence"]
            (for [iso3 (sort (keys facts/catalog))
                  :let [s (facts/spec-basis iso3)]]
              (row (code iso3)
                   (esc (:name s))
                   (esc (:owner-authority s))
                   (esc (:legal-basis s))
                   (str "<ol><li>"
                        (str/join "</li><li>" (map esc (:required-evidence s)))
                        "</li></ol>")))))))

(defn- action-gate-section []
  (section
   "Action gate — derived from the phase table and the governor's stake set"
   (str "Not a hand-written description: each row is computed from "
        (code "machinetool.phase/phases") " (the lowest phase that permits the write, and "
        "the lowest phase that permits an auto-commit) and "
        (code "machinetool.governor/high-stakes")
        ". An op that is in no phase's " (code ":auto") " set is a permanent structural "
        "fact of this actor, not a rollout milestone still to come.")
   (table ["Op" "First phase that may write" "First phase that may auto-commit"
           "In governor high-stakes set"]
          (let [ordered (sort-by str phase/write-ops)
                phs (sort (keys phase/phases))
                first-where (fn [k op]
                              (first (for [p phs
                                           :when (contains? (get-in phase/phases [p k]) op)]
                                       p)))]
            (for [o ordered]
              (row (code (kw-name o))
                   (if-let [p (first-where :writes o)] (esc (str "phase " p)) (cls "err" "never"))
                   (if-let [p (first-where :auto o)]
                     (cls "ok" (str "phase " p))
                     (cls "warn" "never — always a human call"))
                   (if (contains? governor/high-stakes o)
                     (cls "critical" "yes — always escalates")
                     (cls "muted" "no"))))))))

(defn- ledger-section [ledger]
  (section
   (str "Append-only audit ledger — " (count ledger) " facts")
   (str "The complete " (code "machinetool.store/ledger")
        " this render produced, in write order. Every commit, every governor refusal and "
        "every human rejection is here; nothing is filtered out.")
   (table ["#" "Fact" "Op" "Subject" "Actor" "Basis / rules"]
          (map-indexed
           (fn [i f]
             (row (esc i)
                  (case (:t f)
                    :committed (cls "ok" (kw-name (:t f)))
                    :governor-hold (if (seq (:violations f))
                                     (cls "critical" (kw-name (:t f)))
                                     (cls "warn" (kw-name (:t f))))
                    :approval-rejected (cls "err" (kw-name (:t f)))
                    (cls "muted" (kw-name (:t f))))
                  (code (kw-name (:op f)))
                  (code (:subject f))
                  (if-let [a (:actor f)] (code a) (na))
                  (if (seq (:basis f))
                    (esc (str/join " · " (map kw-name (:basis f))))
                    (if-let [pr* (:phase-reason f)] (code (kw-name pr*)) (cls "muted" "—")))))
           ledger))))

(defn- export-section [db]
  (let [pkg (export/audit-package db)
        bundle (export/package->csv-bundle db)]
    (section
     "Social hand-off — audit package this run would produce"
     (str "Counts and CSV byte sizes come from "
          (code "machinetool.export/audit-package") " and "
          (code "package-&gt;csv-bundle") " over the store this render just built — the "
          "package body a machine-tool manufacturer hands to conformity or market-regulator "
          "inspectors. Pure data transforms: no I/O, no signature.")
     (table ["Artifact" "Rows" "CSV bytes"]
            (let [counts (:counts pkg)]
              (for [[fname ck] [["units.csv" :units]
                                ["ledger.csv" :ledger]
                                ["dispatches.csv" :dispatches]
                                ["accuracy-certificates.csv" :accuracy-certificates]]]
                (row (code fname)
                     (esc (get counts ck))
                     (esc (count (get bundle fname ""))))))))))

(defn- footer [ledger runs]
  (let [holds (hard-hold-facts ledger)]
    (str "<footer class=\"muted\">\n"
         "  <p>Generated by <code>machinetool.render-html</code> "
         "(<code>clojure -M:dev:render-html</code>) by executing "
         (count runs) " real <code>machinetool.operation</code> graph runs against a fresh "
         "<code>machinetool.store/seed-db</code>. "
         (count ledger) " ledger facts, " (count holds)
         " governor HARD holds. No timestamps, no wall-clock and no invented values: two "
         "consecutive renders from the same seed are byte-identical, and the build refuses "
         "to write this page at all if the run produces no HARD hold carrying a violation."
         "</p>\n</footer>\n")))

(defn render
  "Renders the whole document from a `run-demo!` result."
  [{:keys [db runs]}]
  (let [ledger (vec (store/ledger db))]
    (str
     "<!DOCTYPE html>\n"
     "<html lang=\"en\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">"
     "<meta name=\"color-scheme\" content=\"light\">"
     "<title>cloud-itonami-isic-2822 &middot; machine-tool plant &middot; Operator Console</title>"
     "<style>\n" (jp-go-dds.skin/dds+skin) "\n</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Machine-tool &amp; metal-forming machinery plant (ISIC 2822) — Operator Console</h1>\n"
     "  <span class=\"badge\">generated from a real actor run · governor-gated · never dispatches hardware</span>\n"
     "</header>\n"
     "<main>\n"
     (units-section db)
     (runs-section runs)
     (hard-holds-section ledger)
     (phase-holds-section ledger)
     (approvals-section runs)
     (attribution-section db runs)
     (verification-section db)
     (screening-section db)
     (registry-section db)
     (action-gate-section)
     (jurisdiction-section)
     (ledger-section ledger)
     (export-section db)
     "</main>\n"
     (footer ledger runs)
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db runs] :as result} (run-demo!)
        ledger (vec (store/ledger db))
        holds (filterv #(= :governor-hold (:t %)) ledger)
        hard (hard-hold-facts ledger)
        rules (distinct (map :rule (mapcat :violations hard)))]
    ;; Build-time invariant, two-stage on purpose. Stage 1 alone would be
    ;; satisfied by a `:phase-disabled` hold, which carries no violations
    ;; and refuses nothing on compliance grounds.
    (when (empty? holds)
      (throw (ex-info "render-html: the scenario produced ZERO governor holds -- refusing to write a page that shows no refusal"
                      {:runs (count runs) :ledger (count ledger)})))
    (when (empty? hard)
      (throw (ex-info "render-html: every hold this run produced carried an EMPTY violation list (rollout-gate holds only) -- refusing to write a page that shows no HARD refusal"
                      {:runs (count runs) :holds (count holds)})))
    (spit out (render result))
    (println "wrote" out
             (str "(" (count runs) " graph runs, "
                  (count ledger) " ledger facts, "
                  (count hard) " HARD holds over "
                  (count rules) " distinct rules: "
                  (str/join ", " (sort (map name rules)))
                  ", " (count (store/dispatch-history db)) " dispatch / "
                  (count (store/evidence-history db)) " certificate / "
                  (count (store/maintenance-notice-history db)) " maintenance-notice drafts)"))))
