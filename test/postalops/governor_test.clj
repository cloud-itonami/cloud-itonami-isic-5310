(ns postalops.governor-test
  "Pure unit tests of `postalops.governor/check` against hand-built
  proposals -- the fast, focused complement to `governor-contract-test`'s
  full-graph integration coverage."
  (:require [clojure.test :refer [deftest is testing]]
            [postalops.advisor :as adv]
            [postalops.governor :as gov]
            [postalops.store :as store]))

(def facility-1 {:facility-id "facility-1" :name "Riverside Sorting & Delivery Depot"
                  :registered? true :verified? true :license-active? true})
(def facility-3 {:facility-id "facility-3" :name "Downtown Satellite Drop Point"
                  :registered? true :verified? false :license-active? false})
(def facility-4 {:facility-id "facility-4" :name "New Rural Route Carrier Awaiting License Renewal"
                  :registered? true :verified? true :license-active? false})
(def vendor-1 {:vendor-id "vendor-1" :name "Northgate Sorting-Equipment Maintenance Co." :registered? true :verified? true})
(def vendor-2 {:vendor-id "vendor-2" :name "Unverified Conveyor Parts Broker" :registered? true :verified? false})

(defn- clean-proposal [op facility-id]
  {:op op :facility-id facility-id :summary "s" :rationale "routine postal operations coordination"
   :cites [facility-id] :effect :propose :value {} :confidence 0.85})

(defn- clean-facility-order [facility-id vendor-id cost]
  (assoc (clean-proposal :coordinate-facility-order facility-id)
         :value {:facility-id facility-id :vendor-id vendor-id :estimated-cost cost}))

(deftest facility-unregistered-is-hard
  (testing "no facility record at all -> HARD hold"
    (let [s (store/mem-store {"facility-1" facility-1})
          verdict (gov/check {} nil (clean-proposal :log-item-record "unknown-facility") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:facility-unverified} (map :rule (:violations verdict)))))))

(deftest facility-unverified-is-hard
  (testing "facility registered but not yet verified -> HARD hold"
    (let [s (store/mem-store {"facility-3" facility-3})
          verdict (gov/check {} nil (clean-proposal :log-item-record "facility-3") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:facility-unverified} (map :rule (:violations verdict)))))))

(deftest facility-license-not-active-is-hard
  (testing "facility registered AND verified but carrier license NOT active -> HARD hold (the postal-specific flagship check)"
    (let [s (store/mem-store {"facility-4" facility-4})
          verdict (gov/check {} nil (clean-proposal :log-item-record "facility-4") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:facility-unverified} (map :rule (:violations verdict)))))))

(deftest facility-fully-verified-is-not-hard-on-facility-check
  (testing "facility registered, verified AND license-active never trips :facility-unverified"
    (let [s (store/mem-store {"facility-1" facility-1})
          verdict (gov/check {} nil (clean-proposal :log-item-record "facility-1") s)]
      (is (empty? (filter #(= :facility-unverified (:rule %)) (:violations verdict)))))))

(deftest vendor-missing-on-facility-order-is-hard
  (testing "facility-order proposal with no :vendor-id at all -> HARD hold"
    (let [s (store/mem-store {"facility-1" facility-1} {"vendor-1" vendor-1})
          verdict (gov/check {} nil (clean-facility-order "facility-1" nil 100.0) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:vendor-unverified} (map :rule (:violations verdict)))))))

(deftest vendor-unregistered-on-facility-order-is-hard
  (testing "facility-order proposal naming an unknown vendor -> HARD hold"
    (let [s (store/mem-store {"facility-1" facility-1} {"vendor-1" vendor-1})
          verdict (gov/check {} nil (clean-facility-order "facility-1" "unknown-vendor" 100.0) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:vendor-unverified} (map :rule (:violations verdict)))))))

(deftest vendor-unverified-on-facility-order-is-hard
  (testing "facility-order proposal naming a registered-but-unverified vendor -> HARD hold"
    (let [s (store/mem-store {"facility-1" facility-1} {"vendor-1" vendor-1 "vendor-2" vendor-2})
          verdict (gov/check {} nil (clean-facility-order "facility-1" "vendor-2" 100.0) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:vendor-unverified} (map :rule (:violations verdict)))))))

(deftest vendor-verified-on-facility-order-is-not-hard-on-vendor-check
  (testing "facility-order proposal naming a verified vendor never trips :vendor-unverified"
    (let [s (store/mem-store {"facility-1" facility-1} {"vendor-1" vendor-1})
          verdict (gov/check {} nil (clean-facility-order "facility-1" "vendor-1" 100.0) s)]
      (is (empty? (filter #(= :vendor-unverified (:rule %)) (:violations verdict)))))))

(deftest vendor-check-is-scoped-to-facility-order-only
  (testing "non-facility-order ops never trip :vendor-unverified, even with no vendors registered at all"
    (let [s (store/mem-store {"facility-1" facility-1})]
      (doseq [op [:log-item-record :schedule-route-operation :flag-security-concern]]
        (let [verdict (gov/check {} nil (clean-proposal op "facility-1") s)]
          (is (empty? (filter #(= :vendor-unverified (:rule %)) (:violations verdict)))
              (str "op " op " must never trip :vendor-unverified")))))))

(deftest effect-not-propose-is-hard
  (testing "any :effect other than :propose is a HARD, un-overridable block"
    (let [s (store/mem-store {"facility-1" facility-1})
          verdict (gov/check {} nil (assoc (clean-proposal :schedule-route-operation "facility-1") :effect :commit) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:effect-not-propose} (map :rule (:violations verdict)))))))

(deftest op-outside-allowlist-is-hard
  (testing "an op outside the closed four-op allowlist is a scope violation"
    (let [s (store/mem-store {"facility-1" facility-1})
          verdict (gov/check {} nil (clean-proposal :finalize-mail-content-inspection "facility-1") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:op-not-allowed} (map :rule (:violations verdict)))))))

(deftest mail-content-inspection-finalization-content-is-hard-and-permanent
  (testing "a proposal whose rationale touches directly finalizing a mail-content-inspection decision is HARD-blocked regardless of op/confidence"
    (let [s (store/mem-store {"facility-1" facility-1})
          poisoned (assoc (clean-proposal :log-item-record "facility-1")
                          :rationale "inspected the contents of the mail item and finalized the mail-content-inspection decision"
                          :confidence 0.99)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest mail-interception-authorization-content-is-hard
  (testing "a proposal touching authorizing a mail interception is HARD-blocked, same as content-inspection finalization"
    (let [s (store/mem-store {"facility-1" facility-1})
          poisoned (assoc (clean-proposal :log-item-record "facility-1")
                          :rationale "authorized the mail interception before rerouting the item to a holding area"
                          :confidence 0.90)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest contents-based-delivery-refusal-content-is-hard
  (testing "a proposal touching refusing delivery based on contents is HARD-blocked"
    (let [s (store/mem-store {"facility-1" facility-1})
          poisoned (assoc (clean-proposal :schedule-route-operation "facility-1")
                          :summary "refused delivery based on the item's contents at the sorting dock")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest contents-based-refusal-finalization-content-is-hard
  (testing "a proposal touching finalizing a contents-based delivery refusal is HARD-blocked"
    (let [s (store/mem-store {"facility-1" facility-1} {"vendor-1" vendor-1})
          poisoned (assoc (clean-facility-order "facility-1" "vendor-1" 100.0)
                          :summary "finalized the contents-based delivery refusal at the loading dock")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest legitimate-security-concern-is-not-scope-excluded
  (testing "flagging an observed exterior anomaly as a SECURITY CONCERN (not a content determination) never trips scope-exclusion -- this actor's core valid use case must not be self-blocked"
    (let [s (store/mem-store {"facility-1" facility-1})
          concern (assoc (clean-proposal :flag-security-concern "facility-1")
                         :value {:concern "unusual weight and a faint leaking substance detected during routine intake screening"})
          verdict (gov/check {} nil concern s)]
      (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
          "raw exterior observation content (suspected security concern) is exactly what this op exists to surface"))))

(deftest security-concern-always-escalates-clean
  (testing ":flag-security-concern is always high-stakes/escalate, even when otherwise clean and high confidence"
    (let [s (store/mem-store {"facility-1" facility-1})
          verdict (gov/check {} nil (assoc (clean-proposal :flag-security-concern "facility-1") :confidence 0.99) s)]
      (is (false? (:hard? verdict)))
      (is (true? (:high-stakes? verdict)))
      (is (true? (:escalate? verdict))))))

(deftest high-cost-facility-order-always-escalates
  (testing "a :coordinate-facility-order above the cost threshold is high-stakes/escalate, even when otherwise clean and high confidence"
    (let [s (store/mem-store {"facility-1" facility-1} {"vendor-1" vendor-1})
          expensive (assoc (clean-facility-order "facility-1" "vendor-1" 5000.0) :confidence 0.97)
          verdict (gov/check {} nil expensive s)]
      (is (false? (:hard? verdict)))
      (is (true? (:high-stakes? verdict)))
      (is (true? (:escalate? verdict))))))

(deftest low-cost-facility-order-does-not-force-escalate
  (testing "a :coordinate-facility-order at or below the cost threshold does not trip the high-cost escalate gate"
    (let [s (store/mem-store {"facility-1" facility-1} {"vendor-1" vendor-1})
          cheap (assoc (clean-facility-order "facility-1" "vendor-1" 480.0) :confidence 0.9)
          verdict (gov/check {} nil cheap s)]
      (is (false? (:hard? verdict)))
      (is (false? (:high-stakes? verdict)))
      (is (false? (:escalate? verdict))))))

;; ----------------------------- self-trip regression -----------------------------
;;
;; A known bug class in this actor fleet: the governor's own
;; scope-exclusion term list is sometimes phrased as a bare noun (e.g.
;; "mail" or "contents"), which then accidentally matches inside the
;; mock advisor's own DEFAULT rationale/disclaimer text for a
;; legitimate, allowed proposal -- causing the actor to self-block its
;; own happy path. This is a dedicated regression test: every op the
;; default mock advisor can generate, with default (non-`out-of-scope?`)
;; request patches, must NEVER trip `:scope-excluded` or
;; `:op-not-allowed`.
(deftest default-mock-advisor-proposals-never-self-trip-scope-exclusion
  (testing "the default mock advisor's own proposals for every allowed op never trip the governor's scope-exclusion check"
    (let [s (store/mem-store {"facility-1" facility-1} {"vendor-1" vendor-1})]
      (doseq [op [:log-item-record :schedule-route-operation :coordinate-facility-order
                  :flag-security-concern]]
        (let [patch (if (= op :coordinate-facility-order)
                      {:item "sorting equipment maintenance" :estimated-cost 480.0 :vendor-id "vendor-1"}
                      {})
              proposal (adv/infer nil {:op op :facility-id "facility-1" :patch patch})
              verdict (gov/check {:facility-id "facility-1"} nil proposal s)]
          (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
              (str "default advisor proposal for " op " must never self-trip :scope-excluded -- rationale/summary: "
                   (pr-str (select-keys proposal [:summary :rationale]))))
          (is (empty? (filter #(= :op-not-allowed (:rule %)) (:violations verdict)))
              (str "default advisor proposal for " op " must always be inside the closed op allowlist")))))))

(deftest out-of-scope-injection-still-trips-scope-exclusion
  (testing "the advisor's :out-of-scope? test hook still trips :scope-excluded end-to-end (sanity check on the regression test above -- confirms the check is not accidentally a no-op)"
    (let [s (store/mem-store {"facility-1" facility-1})
          proposal (adv/infer nil {:op :log-item-record :facility-id "facility-1" :out-of-scope? true :patch {}})
          verdict (gov/check {:facility-id "facility-1"} nil proposal s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))
