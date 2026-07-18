(ns machinetool.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the sibling
  actor."
  (:require [clojure.test :refer [deftest is testing]]
            [machinetool.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sakura CNC Lathe SL-04" (:unit-name (store/unit s "unit-1"))))
      (is (= "JPN" (:jurisdiction (store/unit s "unit-1"))))
      (is (= 0.05 (:positioning-accuracy-deviation-actual (store/unit s "unit-1"))))
      (is (= -0.10 (:positioning-accuracy-deviation-min (store/unit s "unit-1"))))
      (is (= 0.10 (:positioning-accuracy-deviation-max (store/unit s "unit-1"))))
      (is (false? (:accuracy-test-defect-unresolved? (store/unit s "unit-1"))))
      (is (= 0.35 (:positioning-accuracy-deviation-actual (store/unit s "unit-3"))))
      (is (true? (:accuracy-test-defect-unresolved? (store/unit s "unit-4"))))
      (is (false? (:unit-dispatched? (store/unit s "unit-1"))))
      (is (false? (:accuracy-certified? (store/unit s "unit-1"))))
      (is (= ["unit-1" "unit-2" "unit-3" "unit-4"]
             (mapv :id (store/all-units s))))
      (is (nil? (store/accuracy-screen-of s "unit-1")))
      (is (nil? (store/requirements-verification-of s "unit-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/dispatch-history s)))
      (is (= [] (store/evidence-history s)))
      (is (zero? (store/next-dispatch-sequence s "JPN")))
      (is (zero? (store/next-evidence-sequence s "JPN")))
      (is (false? (store/unit-already-dispatched? s "unit-1")))
      (is (false? (store/unit-already-certified? s "unit-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :unit/upsert
                                 :value {:id "unit-1" :unit-name "Sakura CNC Lathe SL-04"}})
        (is (= "Sakura CNC Lathe SL-04" (:unit-name (store/unit s "unit-1"))))
        (is (= 0.05 (:positioning-accuracy-deviation-actual (store/unit s "unit-1"))) "unrelated field preserved"))
      (testing "verification / accuracy-screen payloads commit and read back"
        (store/commit-record! s {:effect :verification/set :path ["unit-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/requirements-verification-of s "unit-1")))
        (store/commit-record! s {:effect :accuracy-test-screen/set :path ["unit-1"]
                                 :payload {:unit-id "unit-1" :verdict :resolved}})
        (is (= {:unit-id "unit-1" :verdict :resolved} (store/accuracy-screen-of s "unit-1"))))
      (testing "unit dispatch drafts a record and advances the sequence"
        (store/commit-record! s {:effect :unit/mark-dispatched :path ["unit-1"]})
        (is (= "JPN-MTL-000000" (get (first (store/dispatch-history s)) "record_id")))
        (is (= "unit-dispatch-draft" (get (first (store/dispatch-history s)) "kind")))
        (is (true? (:unit-dispatched? (store/unit s "unit-1"))))
        (is (= 1 (count (store/dispatch-history s))))
        (is (= 1 (store/next-dispatch-sequence s "JPN")))
        (is (true? (store/unit-already-dispatched? s "unit-1")))
        (is (false? (store/unit-already-dispatched? s "unit-2"))))
      (testing "accuracy certificate drafts a record and advances the sequence"
        (store/commit-record! s {:effect :unit/mark-certified :path ["unit-1"]})
        (is (= "JPN-ACC-000000" (get (first (store/evidence-history s)) "record_id")))
        (is (= "accuracy-certificate-draft" (get (first (store/evidence-history s)) "kind")))
        (is (true? (:accuracy-certified? (store/unit s "unit-1"))))
        (is (= 1 (count (store/evidence-history s))))
        (is (= 1 (store/next-evidence-sequence s "JPN")))
        (is (true? (store/unit-already-certified? s "unit-1")))
        (is (false? (store/unit-already-certified? s "unit-2")))
        (is (nil? (:testlab-engagement-ref (store/unit s "unit-1")))
            "no ref supplied at the store layer -> not fabricated (the governor, not the store, enforces mandatory presence)"))
      (testing "accuracy certificate WITH a :certification/testlab-engagement-ref persists the reference onto the unit"
        (store/commit-record! s {:effect :unit/mark-certified :path ["unit-2"]
                                 :value {:certification/testlab-engagement-ref
                                         {:testlab-engagement-ref/id "engagement-1"
                                          :testlab-engagement-ref/source-actor "cloud-itonami-isic-7120"
                                          :testlab-engagement-ref/certification-number "ATL-CERT-000000"}}})
        (is (true? (:accuracy-certified? (store/unit s "unit-2"))))
        (is (= "cloud-itonami-isic-7120"
               (:testlab-engagement-ref/source-actor (:testlab-engagement-ref (store/unit s "unit-2")))))
        (is (= "ATL-CERT-000000"
               (:testlab-engagement-ref/certification-number (:testlab-engagement-ref (store/unit s "unit-2"))))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest maintenance-notice-write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "unit dispatch first, so the maintenance notice has a real dispatch-ref to reference"
        (store/commit-record! s {:effect :unit/mark-dispatched :path ["unit-1"]}))
      (testing "maintenance notice drafts a record, advances the sequence, and allows more than one per unit"
        (store/commit-record! s {:effect :maintenance-notice/issue :path ["unit-1"]
                                 :value {:unit-id "unit-1" :dispatch-ref "JPN-MTL-000000"}})
        (is (= "JPN-MMN-000000" (get (first (store/maintenance-notice-history s)) "record_id")))
        (is (= "maintenance-notice-draft" (get (first (store/maintenance-notice-history s)) "kind")))
        (is (= "JPN-MTL-000000" (get (first (store/maintenance-notice-history s)) "dispatch_ref")))
        (is (= 1 (count (store/maintenance-notice-history s))))
        (is (= 1 (store/next-maintenance-notice-sequence s "JPN")))
        (store/commit-record! s {:effect :maintenance-notice/issue :path ["unit-1"]
                                 :value {:unit-id "unit-1" :dispatch-ref "JPN-MTL-000000"}})
        (is (= 2 (count (store/maintenance-notice-history s)))
            "a unit may receive more than one maintenance notice, unlike dispatch/certificate")
        (is (= "JPN-MMN-000001" (get (second (store/maintenance-notice-history s)) "record_id")))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/unit s "nope")))
    (is (= [] (store/all-units s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/dispatch-history s)))
    (is (= [] (store/evidence-history s)))
    (is (zero? (store/next-dispatch-sequence s "JPN")))
    (is (zero? (store/next-evidence-sequence s "JPN")))
    (store/with-units s {"x" {:id "x" :unit-name "n" :positioning-accuracy-deviation-actual 0.05
                                   :positioning-accuracy-deviation-min -0.10 :positioning-accuracy-deviation-max 0.10
                                   :accuracy-test-defect-unresolved? false
                                   :unit-dispatched? false :accuracy-certified? false
                                   :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:unit-name (store/unit s "x"))))))
