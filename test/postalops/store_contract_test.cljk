(ns postalops.store-contract-test
  "Contract tests for `postalops.store/Store` protocol."
  (:require [clojure.test :refer [deftest is testing]]
            [postalops.store :as store]))

(deftest mem-store-facility-lookup
  (testing "MemStore can store and retrieve facilities by ID (string keys)"
    (let [facilities {"f1" {:facility-id "f1" :name "Alice's Sorting Depot" :registered? true :verified? true :license-active? true}}
          s (store/mem-store facilities)]
      (is (some? (store/facility-record s "f1")))
      (is (nil? (store/facility-record s "f99"))))))

(deftest mem-store-all-facility-records
  (testing "MemStore returns all facilities in sorted order"
    (let [facilities {"f2" {:facility-id "f2" :name "Bob's Regional Hub"}
                      "f1" {:facility-id "f1" :name "Alice's Sorting Depot"}
                      "f3" {:facility-id "f3" :name "Carol's Rural Route"}}
          s (store/mem-store facilities)
          all-f (store/all-facility-records s)]
      (is (= 3 (count all-f)))
      (is (= "f1" (:facility-id (first all-f))))
      (is (= "f3" (:facility-id (last all-f)))))))

(deftest mem-store-vendor-lookup
  (testing "MemStore can store and retrieve vendors by ID (string keys)"
    (let [vendors {"v1" {:vendor-id "v1" :name "Acme Sorting Equipment" :registered? true :verified? true}}
          s (store/mem-store {} vendors)]
      (is (some? (store/vendor-record s "v1")))
      (is (nil? (store/vendor-record s "v99"))))))

(deftest mem-store-all-vendor-records
  (testing "MemStore returns all vendors in sorted order"
    (let [vendors {"v2" {:vendor-id "v2" :name "Beta Maintenance"}
                   "v1" {:vendor-id "v1" :name "Acme Sorting Equipment"}}
          s (store/mem-store {} vendors)
          all-v (store/all-vendor-records s)]
      (is (= 2 (count all-v)))
      (is (= "v1" (:vendor-id (first all-v)))))))

(deftest mem-store-ledger-append
  (testing "MemStore append-ledger! adds facts to immutable log"
    (let [s (store/mem-store {})
          fact1 {:t :test :data "fact1"}
          fact2 {:t :test :data "fact2"}]
      (is (= 0 (count (store/ledger s))))
      (store/append-ledger! s fact1)
      (is (= 1 (count (store/ledger s))))
      (store/append-ledger! s fact2)
      (is (= 2 (count (store/ledger s)))))))

(deftest mem-store-coordination-log
  (testing "MemStore commit-record! appends to coordination-log"
    (let [s (store/mem-store {})
          record {:op :log-item-record :facility-id "f1" :value {:items-intake 420}}]
      (is (= 0 (count (store/coordination-log s))))
      (store/commit-record! s record)
      (is (= 1 (count (store/coordination-log s))))
      (is (= record (first (store/coordination-log s)))))))

(deftest mem-store-with-facility-records
  (testing "MemStore with-facility-records replaces the facility directory"
    (let [s (store/mem-store {})
          new-facilities {"f1" {:facility-id "f1" :name "Alice's Sorting Depot"}}]
      (is (= 0 (count (store/all-facility-records s))))
      (store/with-facility-records s new-facilities)
      (is (= 1 (count (store/all-facility-records s)))))))

(deftest mem-store-with-vendor-records
  (testing "MemStore with-vendor-records replaces the vendor directory"
    (let [s (store/mem-store {})
          new-vendors {"v1" {:vendor-id "v1" :name "Acme Sorting Equipment"}}]
      (is (= 0 (count (store/all-vendor-records s))))
      (store/with-vendor-records s new-vendors)
      (is (= 1 (count (store/all-vendor-records s)))))))

(deftest seed-db-has-demo-data
  (testing "seed-db creates a populated MemStore with demo facilities and vendors"
    (let [s (store/seed-db)]
      (is (> (count (store/all-facility-records s)) 0))
      (is (some? (store/facility-record s "facility-1")))
      (is (some? (store/facility-record s "facility-2")))
      (is (some? (store/facility-record s "facility-3")))
      (is (some? (store/facility-record s "facility-4")))
      (is (> (count (store/all-vendor-records s)) 0))
      (is (some? (store/vendor-record s "vendor-1")))
      (is (some? (store/vendor-record s "vendor-2"))))))

(deftest demo-data-string-key-consistency
  (testing "demo-data uses string keys, not keywords, for facility-id/vendor-id"
    (let [demo (store/demo-data)
          facilities (:facilities demo)
          vendors (:vendors demo)]
      (doseq [[k v] facilities]
        (is (string? k) "facility keys must be strings")
        (is (string? (:facility-id v)) "facility-id must be string")
        (is (= k (:facility-id v)) "key must match facility-id"))
      (doseq [[k v] vendors]
        (is (string? k) "vendor keys must be strings")
        (is (string? (:vendor-id v)) "vendor-id must be string")
        (is (= k (:vendor-id v)) "key must match vendor-id")))))

(deftest demo-data-license-active-field-present
  (testing "every demo facility record has an explicit :license-active? boolean field (the postal-specific verification dimension)"
    (let [facilities (:facilities (store/demo-data))]
      (doseq [[_ v] facilities]
        (is (boolean? (:license-active? v))
            (str (:facility-id v) " must have an explicit :license-active? boolean"))))))

(deftest store-is-append-only
  (testing "appended facts are immutable and never removed"
    (let [s (store/seed-db)
          fact1 {:t :event1 :data "a"}
          fact2 {:t :event2 :data "b"}]
      (store/append-ledger! s fact1)
      (let [ledger-after-1 (store/ledger s)]
        (store/append-ledger! s fact2)
        (let [ledger-after-2 (store/ledger s)]
          (is (= (count ledger-after-1) (dec (count ledger-after-2))))
          (is (every? #(some (fn [x] (= x %)) ledger-after-2) ledger-after-1)
              "all prior facts must still be present"))))))
