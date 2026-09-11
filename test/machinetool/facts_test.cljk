(ns machinetool.facts-test
  (:require [clojure.test :refer [deftest is]]
            [machinetool.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN")))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "GBR"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["GBR" "JPN"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "JPN")]
    (is (facts/required-evidence-satisfied? "JPN" all))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))

(deftest industrial-welding-cell-unit-type-has-unspsc-and-gtin
  (let [u (facts/unit-type-by-id :unit/industrial-welding-cell)]
    (is (some? u))
    (is (= "23271400" (:unspsc-code u)) "UNSPSC class code (family 23270000, class 'Welding machinery')")
    (is (= "0212822000018" (:gtin u)))
    (is (= :unissued-blueprint-placeholder (:gtin/status u))
        "placeholder GTIN is never presented as a real, GS1-issued identifier")))

(deftest cnc-machining-center-unit-type-has-unspsc-and-gtin
  (let [u (facts/unit-type-by-id :unit/cnc-machining-center)]
    (is (some? u))
    (is (= "23242400" (:unspsc-code u)) "UNSPSC class code (family 23240000, class 'Machining centers')")
    (is (= "0212822000025" (:gtin u)))
    (is (= :unissued-blueprint-placeholder (:gtin/status u))
        "placeholder GTIN is never presented as a real, GS1-issued identifier")))

(deftest metal-additive-manufacturing-system-unit-type-has-unspsc-and-gtin
  (let [u (facts/unit-type-by-id :unit/metal-additive-manufacturing-system)]
    (is (some? u))
    (is (= "23261505" (:unspsc-code u))
        "UNSPSC commodity code (family 23260000, class 23261500 'Rapid prototyping machines', commodity 'Selective laser sintering machine')")
    (is (= "0212822000032" (:gtin u)))
    (is (= :unissued-blueprint-placeholder (:gtin/status u))
        "placeholder GTIN is never presented as a real, GS1-issued identifier")
    (is (= :powder-bed-fusion (:build-process u)))
    (is (vector? (:build-volume-mm u)))
    (is (seq (:materials u)))))

(deftest unknown-unit-type-has-no-fabricated-entry
  (is (nil? (facts/unit-type-by-id :unit/does-not-exist))))
