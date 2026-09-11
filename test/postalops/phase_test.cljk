(ns postalops.phase-test
  "Unit tests of `postalops.phase` rollout logic."
  (:require [clojure.test :refer [deftest is testing]]
            [postalops.phase :as phase]))

(deftest phase-0-read-only
  (testing "phase 0 allows no writes"
    (doseq [op [:log-item-record :schedule-route-operation :coordinate-facility-order
                :flag-security-concern]]
      (let [{:keys [disposition]} (phase/gate 0 {:op op} :commit)]
        (is (= :hold disposition)
            (str "phase 0 must hold all ops including " op))))))

(deftest phase-1-item-record-only
  (testing "phase 1 allows only item-record logging, requires approval"
    (let [{:keys [disposition reason]} (phase/gate 1 {:op :log-item-record} :commit)]
      (is (= :escalate disposition))
      (is (= :phase-approval reason)))
    (let [{:keys [disposition]} (phase/gate 1 {:op :schedule-route-operation} :commit)]
      (is (= :hold disposition)))))

(deftest phase-2-adds-coordination-ops
  (testing "phase 2 allows coordination ops, still requires approval"
    (doseq [op [:log-item-record :schedule-route-operation :coordinate-facility-order]]
      (let [{:keys [disposition]} (phase/gate 2 {:op op} :commit)]
        (is (= :escalate disposition)
            (str "phase 2 op " op " requires approval"))))))

(deftest phase-3-auto-commits-clean-ops
  (testing "phase 3 auto-commits clean, high-conf non-safety ops"
    (let [{:keys [disposition]} (phase/gate 3 {:op :log-item-record} :commit)]
      (is (= :commit disposition)))
    (let [{:keys [disposition]} (phase/gate 3 {:op :schedule-route-operation} :commit)]
      (is (= :commit disposition)))
    (let [{:keys [disposition]} (phase/gate 3 {:op :coordinate-facility-order} :commit)]
      (is (= :commit disposition)))))

(deftest security-concern-holds-when-not-enabled
  (testing ":flag-security-concern holds in phases 0-2 (not yet enabled)"
    (doseq [ph [0 1 2]]
      (let [{:keys [disposition]} (phase/gate ph {:op :flag-security-concern} :escalate)]
        (is (= :hold disposition)
            (str "phase " ph " has not enabled flag-security-concern yet"))))))

(deftest security-concern-escalates-when-enabled
  (testing ":flag-security-concern ALWAYS escalates when enabled, even if governor says commit"
    (let [{:keys [disposition]} (phase/gate 3 {:op :flag-security-concern} :commit)]
      (is (= :escalate disposition)
          "phase 3 must escalate security concerns regardless of governor disposition"))))

(deftest security-concern-never-in-any-phase-auto-set
  (testing ":flag-security-concern must never be a member of any phase's :auto set -- a permanent structural fact, not a rollout milestone"
    (doseq [[ph {:keys [auto]}] phase/phases]
      (is (not (contains? auto :flag-security-concern))
          (str "phase " ph " :auto set must never contain :flag-security-concern")))))

(deftest high-cost-facility-order-escalates-at-phase-3
  (testing "the governor already turned a high-cost facility order into :escalate upstream -- phase 3 must not force it back to :commit"
    (let [{:keys [disposition]} (phase/gate 3 {:op :coordinate-facility-order} :escalate)]
      (is (= :escalate disposition)))))

(deftest hard-hold-always-wins
  (testing "a governor HARD hold stays HOLD regardless of phase"
    (doseq [ph [0 1 2 3]]
      (let [{:keys [disposition]} (phase/gate ph {:op :log-item-record} :hold)]
        (is (= :hold disposition)
            (str "phase " ph " must respect governor HARD hold"))))))

(deftest verdict->disposition-maps-correctly
  (testing "verdict->disposition correctly translates governor verdict to base disposition"
    (is (= :hold (phase/verdict->disposition {:hard? true :escalate? false})))
    (is (= :escalate (phase/verdict->disposition {:hard? false :escalate? true})))
    (is (= :commit (phase/verdict->disposition {:hard? false :escalate? false})))))
