(ns postalops.governor-contract-test
  "Integration tests: full OperationActor graph exercising the governor's
  hard checks, escalation logic, and audit trail."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [postalops.advisor :as advisor]
            [postalops.store :as store]
            [postalops.operation :as op]))

(defn exec-request [actor tid request ctx]
  (g/run* actor {:request request :context ctx} {:thread-id tid}))

(defn resume-approval [actor tid status]
  (g/run* actor {:approval {:status status :by "coordinator"}} {:thread-id tid :resume? true}))

(deftest item-record-logging-full-flow
  (testing "clean item-record proposal -> auto-commit at phase 3"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-1" :phase 3}
          result (exec-request actor "t1"
                               {:op :log-item-record :facility-id "facility-1" :patch {:items-intake 420}}
                               ctx)]
      (is (some? result))
      (is (> (count (store/ledger db)) 0)
          "commit must append audit facts to ledger")
      (is (> (count (store/coordination-log db)) 0)
          "commit must append record to coordination-log"))))

(deftest security-concern-always-escalates
  (testing ":flag-security-concern escalates for human approval, regardless of phase/confidence"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-2" :phase 3}
          result (exec-request actor "t2"
                               {:op :flag-security-concern :facility-id "facility-1"
                                :patch {:concern "unusual weight" :confidence 0.99}}
                               ctx)]
      (is (some? result))
      ;; At this point the actor is paused for approval, not yet committed
      (is (= 0 (count (store/coordination-log db)))
          "security concern must not auto-commit, must wait for approval")
      ;; Now approve it
      (resume-approval actor "t2" :approved)
      (is (> (count (store/coordination-log db)) 0)
          "after approval, record must be committed"))))

(deftest high-cost-facility-order-always-escalates
  (testing "a high-cost :coordinate-facility-order escalates for human approval, even at phase 3 clean"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-2b" :phase 3}
          result (exec-request actor "t2b"
                               {:op :coordinate-facility-order :facility-id "facility-1"
                                :patch {:item "sorting-line automation upgrade" :quantity 1 :estimated-cost 3200.0
                                        :vendor-id "vendor-1"}}
                               ctx)]
      (is (some? result))
      (is (= 0 (count (store/coordination-log db)))
          "high-cost facility order must not auto-commit, must wait for approval")
      (resume-approval actor "t2b" :approved)
      (is (> (count (store/coordination-log db)) 0)
          "after approval, record must be committed"))))

(deftest low-cost-facility-order-auto-commits
  (testing "a low-cost :coordinate-facility-order naming a verified vendor auto-commits at phase 3 when clean"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-2c" :phase 3}
          result (exec-request actor "t2c"
                               {:op :coordinate-facility-order :facility-id "facility-1"
                                :patch {:item "conveyor belt maintenance" :quantity 1 :estimated-cost 480.0
                                        :vendor-id "vendor-1"}}
                               ctx)]
      (is (some? result))
      (is (> (count (store/coordination-log db)) 0)
          "low-cost facility order must auto-commit when clean at phase 3"))))

(deftest unregistered-facility-hard-hold
  (testing "unregistered facility -> permanent HARD hold, never escalates"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-3" :phase 3}]
      (exec-request actor "t3"
                     {:op :log-item-record :facility-id "unknown-facility"
                      :patch {:items-intake 0}}
                     ctx)
      (is (= 0 (count (store/coordination-log db)))
          "HARD hold must never commit"))))

(deftest unverified-facility-hard-hold
  (testing "registered but unverified facility -> permanent HARD hold"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-4" :phase 3}
          result (exec-request actor "t4"
                               {:op :log-item-record :facility-id "facility-3"
                                :patch {:items-intake 10}}
                               ctx)]
      (is (some? result))
      (is (= 0 (count (store/coordination-log db)))
          "unverified facility must HARD hold"))))

(deftest facility-license-not-active-hard-hold
  (testing "registered+verified but carrier license NOT active facility -> permanent HARD hold (postal-specific flagship check)"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-4c" :phase 3}
          result (exec-request actor "t4c"
                               {:op :log-item-record :facility-id "facility-4"
                                :patch {:items-intake 10}}
                               ctx)]
      (is (some? result))
      (is (= 0 (count (store/coordination-log db)))
          "facility whose carrier license is not active must HARD hold"))))

(deftest unverified-vendor-facility-order-hard-hold
  (testing "a facility-order naming an unverified vendor -> permanent HARD hold"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-4b" :phase 3}
          result (exec-request actor "t4b"
                               {:op :coordinate-facility-order :facility-id "facility-1"
                                :patch {:item "replacement conveyor parts" :quantity 50 :estimated-cost 300.0
                                        :vendor-id "vendor-2"}}
                               ctx)]
      (is (some? result))
      (is (= 0 (count (store/coordination-log db)))
          "unverified vendor must HARD hold"))))

(deftest effect-not-propose-hard-hold
  (testing "proposal with :effect :commit (not :propose) -> hard hold"
    (let [db (store/seed-db)
          bad-advisor (reify advisor/Advisor
                        (-advise [_ _ req]
                          (assoc (advisor/infer nil req) :effect :commit)))
          actor (op/build db {:advisor bad-advisor})
          ctx {:actor-id "test-5" :phase 3}
          result (exec-request actor "t5"
                               {:op :log-item-record :facility-id "facility-1"
                                :patch {:items-intake 420}}
                               ctx)]
      (is (some? result))
      (is (= 0 (count (store/coordination-log db)))
          "non-:propose effect must HARD hold"))))

(deftest scope-excluded-content-hard-hold
  (testing "proposal drifting into mail-content-inspection/interception/refusal scope -> permanent hard hold"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-6" :phase 3}
          result (exec-request actor "t6"
                               {:op :log-item-record :facility-id "facility-1"
                                :out-of-scope? true  ; triggers scope pollution in advisor
                                :patch {}}
                               ctx)]
      (is (some? result))
      (is (= 0 (count (store/coordination-log db)))
          "scope-excluded content must HARD hold"))))

(deftest phase-1-approval-gate
  (testing "phase 1 approved request -> commits after human approval"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-7" :phase 1}]
      (exec-request actor "t7"
                     {:op :log-item-record :facility-id "facility-1"
                      :patch {:items-intake 420}}
                     ctx)
      (is (= 0 (count (store/coordination-log db)))
          "phase 1 must not auto-commit, requires approval")
      (resume-approval actor "t7" :approved)
      (is (> (count (store/coordination-log db)) 0)
          "after approval, must commit")
      (is (some #(= :committed (:t %)) (store/ledger db))
          "committed fact must be logged after approval"))))

(deftest audit-trail-completeness
  (testing "every decision leaves immutable audit facts"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-8" :phase 3}]
      (exec-request actor "t8a"
                     {:op :log-item-record :facility-id "facility-1" :patch {:items-intake 420}}
                     ctx)
      (exec-request actor "t8b"
                     {:op :log-item-record :facility-id "unknown" :patch {:items-intake 420}}
                     ctx)
      (let [ledger (store/ledger db)]
        (is (> (count ledger) 0))
        (is (some #(= :committed (:t %)) ledger)
            "successful commits must be logged")
        (is (some #(= :governor-hold (:t %)) ledger)
            "HARD holds must be logged")))))
