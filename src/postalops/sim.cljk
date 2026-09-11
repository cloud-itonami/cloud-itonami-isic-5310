(ns postalops.sim
  "Demo driver -- `clojure -M:run`. Walks a clean item-record logging
  request through intake -> advise -> govern -> decide -> approval ->
  commit at phase 1 (assisted-logging, always approval), then re-runs the
  same op at phase 3 (supervised-auto, clean + high confidence ->
  auto-commit), then a route-operation-scheduling request and a low-cost
  facility-order coordination naming a verified vendor (both auto-commit
  clean at phase 3), then a high-cost facility-order (ALWAYS escalates
  regardless of phase), then a security-concern flag (ALWAYS escalates,
  at any phase -- approve, then commit), then HARD-hold scenarios: an
  unregistered facility, a facility registered but not yet verified, a
  facility registered+verified but whose carrier license is NOT active
  (the postal-specific flagship check), a facility-order naming an
  unverified vendor, a proposal whose own `:effect` is not `:propose`,
  and a proposal that has drifted into the permanently-excluded
  mail-content-inspection/interception/contents-based-refusal scope."
  (:require [langgraph.graph :as g]
            [postalops.advisor :as advisor]
            [postalops.store :as store]
            [postalops.operation :as op]))

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "postal-ops-coordinator-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        coordinator-phase-1 {:actor-id "coord-1" :actor-role :postal-ops-coordinator :phase 1}
        coordinator-phase-3 {:actor-id "coord-1" :actor-role :postal-ops-coordinator :phase 3}
        actor (op/build db)]

    (println "== log-item-record facility-1 (phase 1, escalates -- human approves) ==")
    (let [r (exec-op actor "t1" {:op :log-item-record :facility-id "facility-1"
                                  :patch {:items-intake 420 :items-sorted 390 :items-delivered 375}} coordinator-phase-1)]
      (println r)
      (println "-- human postal ops coordinator approves --")
      (println (approve! actor "t1")))

    (println "\n== log-item-record facility-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t2" {:op :log-item-record :facility-id "facility-1"
                                  :patch {:items-intake 300 :items-sorted 300 :items-delivered 288}} coordinator-phase-3))

    (println "\n== schedule-route-operation facility-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t3" {:op :schedule-route-operation :facility-id "facility-1"
                                  :patch {:route "rural-route-12" :date "2026-07-20" :dock "dock-2"}} coordinator-phase-3))

    (println "\n== coordinate-facility-order facility-1, low cost, verified vendor (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t4" {:op :coordinate-facility-order :facility-id "facility-1"
                                  :patch {:item "conveyor belt maintenance" :quantity 1 :estimated-cost 480.0
                                          :vendor-id "vendor-1"}} coordinator-phase-3))

    (println "\n== coordinate-facility-order facility-1, HIGH cost (ALWAYS escalates, even at phase 3) ==")
    (let [r (exec-op actor "t5" {:op :coordinate-facility-order :facility-id "facility-1"
                                 :patch {:item "sorting-line automation upgrade" :quantity 1 :estimated-cost 3800.0
                                         :vendor-id "vendor-1"}} coordinator-phase-3)]
      (println r)
      (println "-- human postal ops coordinator reviews & approves --")
      (println (approve! actor "t5")))

    (println "\n== flag-security-concern facility-1 (ALWAYS escalates, even at phase 3) ==")
    (let [r (exec-op actor "t6" {:op :flag-security-concern :facility-id "facility-1"
                                 :patch {:concern "unusual weight and a faint leaking substance detected during routine intake screening" :confidence 0.92}} coordinator-phase-3)]
      (println r)
      (println "-- human postal ops coordinator reviews & responds --")
      (println (approve! actor "t6")))

    (println "\n== log-item-record facility-99 (unregistered facility -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :log-item-record :facility-id "facility-99"
                                  :patch {:items-intake 0}} coordinator-phase-3))

    (println "\n== log-item-record facility-3 (registered but unverified -> HARD hold) ==")
    (println (exec-op actor "t8" {:op :log-item-record :facility-id "facility-3"
                                  :patch {:items-intake 10}} coordinator-phase-3))

    (println "\n== log-item-record facility-4 (registered+verified but license NOT active -> HARD hold) ==")
    (println (exec-op actor "t8b" {:op :log-item-record :facility-id "facility-4"
                                   :patch {:items-intake 10}} coordinator-phase-3))

    (println "\n== coordinate-facility-order facility-1, vendor-2 unverified (-> HARD hold) ==")
    (println (exec-op actor "t9" {:op :coordinate-facility-order :facility-id "facility-1"
                                  :patch {:item "replacement conveyor parts" :quantity 50 :estimated-cost 300.0
                                          :vendor-id "vendor-2"}} coordinator-phase-3))

    (println "\n== schedule-route-operation facility-1, advisor attempts direct actuation (:effect :commit) -> HARD hold ==")
    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                (-advise [_ _ req]
                                                  (assoc (advisor/infer nil req) :effect :commit)))})]
      (println (exec-op actor-direct "t10" {:op :schedule-route-operation :facility-id "facility-1"
                                           :patch {:route "rural-route-14" :date "2026-07-22"}} coordinator-phase-3)))

    (println "\n== log-item-record facility-1, advisor drifts into mail-content scope -> HARD hold, permanent ==")
    (println (exec-op actor "t11" {:op :log-item-record :facility-id "facility-1"
                                   :out-of-scope? true
                                   :patch {}} coordinator-phase-3))

    (println "\n== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "\n== committed coordination log ==")
    (doseq [r (store/coordination-log db)] (println r))))
