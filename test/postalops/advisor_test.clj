(ns postalops.advisor-test
  "Unit tests of `postalops.advisor` proposal generation."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [postalops.advisor :as adv]
            [postalops.store :as store]))

(def db (store/seed-db))

(deftest propose-item-record-shape
  (testing "item-record proposal has correct shape and fields"
    (let [p (adv/infer db {:op :log-item-record
                           :facility-id "facility-1"
                           :patch {:items-intake 420 :items-sorted 390 :items-delivered 375}})]
      (is (= :log-item-record (:op p)))
      (is (= "facility-1" (:facility-id p)))
      (is (= :propose (:effect p)))
      (is (<= 0 (:confidence p) 1))
      (is (map? (:value p)))
      (is (contains? (:value p) :facility-id)))))

(deftest propose-route-operation-shape
  (testing "route-operation proposal has correct shape"
    (let [p (adv/infer db {:op :schedule-route-operation
                           :facility-id "facility-2"
                           :patch {:route "rural-route-12" :date "2026-07-20"}})]
      (is (= :schedule-route-operation (:op p)))
      (is (= "facility-2" (:facility-id p)))
      (is (= :propose (:effect p))))))

(deftest propose-facility-order-shape
  (testing "facility-order proposal has correct shape"
    (let [p (adv/infer db {:op :coordinate-facility-order
                           :facility-id "facility-1"
                           :patch {:item "conveyor belt maintenance" :quantity 1 :estimated-cost 480.0
                                   :vendor-id "vendor-1"}})]
      (is (= :coordinate-facility-order (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p)))
      (is (= "vendor-1" (get-in p [:value :vendor-id]))))))

(deftest propose-security-concern-shape
  (testing "security-concern proposal always escalates"
    (let [p (adv/infer db {:op :flag-security-concern
                           :facility-id "facility-1"
                           :patch {:concern "unusual weight and a faint leaking substance"}})]
      (is (= :flag-security-concern (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p))))))

(deftest all-proposals-effect-is-always-propose
  (testing "every proposal type has :effect :propose, never direct actuation"
    (doseq [op [:log-item-record :schedule-route-operation :coordinate-facility-order
                :flag-security-concern]]
      (let [p (adv/infer db {:op op :facility-id "facility-1" :patch {}})]
        (is (= :propose (:effect p))
            (str "op " op " must have :effect :propose"))))))

(deftest rationale-string-is-present
  (testing "every proposal has a rationale explaining the advisor's thinking"
    (doseq [op [:log-item-record :schedule-route-operation :coordinate-facility-order
                :flag-security-concern]]
      (let [p (adv/infer db {:op op :facility-id "facility-1" :patch {}})]
        (is (string? (:rationale p))
            (str "op " op " must have a :rationale string"))))))

(deftest security-concern-rationale-never-claims-content-determination
  (testing "the default :flag-security-concern rationale/summary text never itself uses content-inspection/interception/refusal-finalization vocabulary -- it is an exterior observation only, never a content determination"
    (let [p (adv/infer db {:op :flag-security-concern :facility-id "facility-1"
                           :patch {:concern "unusual weight and a faint leaking substance"}})
          blob (str/lower-case (str (:summary p) " " (:rationale p)))]
      (doseq [forbidden ["inspect" "intercept" "傍受" "開封" "内容"]]
        (is (not (str/includes? blob forbidden))
            (str "default flag-security-concern text must not use " (pr-str forbidden)))))))
