(ns postalops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave5 rollout ledger seq 6+, iter22): this repo previously had NO demo
  page and no generator at all. This namespace drives the REAL actor
  stack (`postalops.operation` -> `postalops.governor` -> `postalops.store`)
  through a scenario built from this actor's OWN seeded demo data
  (`postalops.store/seed-db` -- facility-1/facility-2 fully registered +
  verified + license-active, facility-3 registered but NOT verified,
  facility-4 registered + verified but its carrier license is NOT
  active, vendor-1 registered + verified, vendor-2 registered but NOT
  verified) and renders the result deterministically -- no invented
  numbers, no timestamps in the page content, byte-identical across
  reruns against the same seed (verified by diffing two consecutive
  runs before shipping).

  Cross-checked against this repo's own `postalops.sim` demo driver
  (`clojure -M:dev:run`) BEFORE writing this scenario: `postalops.sim`
  already drives real seeded ids correctly (facility-1/2/3/4, vendor-1/2,
  the nonexistent facility-99) with dispositions matching governor.cljc's
  own rules exactly -- unlike `cloud-itonami-isic-851`'s `schoolops.sim`
  (a known prior latent-bug case, ids that did not exist in that repo's
  own seed data), this repo's sim driver was safe to read for reference
  ids. This renderer keeps its own `run-demo!` scenario below so the
  build-time generator has no runtime dependency on the demo driver
  either way -- every field read by `render` below is real governor/
  store output, not a hand-typed copy.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [kotoba.lang.text :as str]
            [postalops.store :as store]
            [postalops.operation :as op]
            [langgraph.graph :as g]))

(def ^:private operator
  {:actor-id "op-1" :actor-role :postal-ops-coordinator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach: facility-1 clears three ops that all auto-commit
  clean at phase 3 (log-item-record, schedule-route-operation, a
  low-cost coordinate-facility-order naming the registered+verified
  vendor-1); facility-1's security-concern flag ALWAYS escalates (per
  `always-escalate-ops`) even though clean, and is approved by a human
  postal-ops coordinator; facility-2 (also fully registered/verified/
  license-active) clears one more auto-commit to show a second facility
  with activity. Then three DISTINCT HARD-hold reasons, none ever
  reaching a human: facility-3 (registered but NOT verified) HARD-holds
  on `:facility-unverified`; facility-1's coordinate-facility-order
  naming vendor-2 (registered but NOT verified) HARD-holds on
  `:vendor-unverified`; and a proposal whose advisor drifted into
  out-of-scope mail-content territory (`:out-of-scope? true`) HARD-holds
  on `:scope-excluded`. facility-4 (registered + verified but whose
  carrier license is NOT currently active -- the postal-specific
  flagship check, distinct from a bare unverified facility) also
  HARD-holds on the same `:facility-unverified` rule, included to show
  that domain-specific nuance even though it shares a rule name with
  facility-3's hold. Returns the resulting store -- every field read by
  `render` below is real governor/store output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "f1-log" {:op :log-item-record :facility-id "facility-1"
                            :patch {:items-intake 420 :items-sorted 390 :items-delivered 375}})

    (exec! actor "f1-route" {:op :schedule-route-operation :facility-id "facility-1"
                              :patch {:route "rural-route-12" :date "2026-07-20" :dock "dock-2"}})

    (exec! actor "f1-order" {:op :coordinate-facility-order :facility-id "facility-1"
                              :patch {:item "conveyor belt maintenance" :quantity 1
                                      :estimated-cost 480.0 :vendor-id "vendor-1"}})

    ;; Two of facility-1's HARD holds run BEFORE its final security-concern
    ;; approve! below, so the "last coordination status" summary column
    ;; (which reads the chronologically-last ledger fact per facility) ends
    ;; on facility-1's clean approved-commit -- both holds are still fully
    ;; visible, unedited, in the audit-ledger table further down the page.
    (exec! actor "f1-order-badvendor" {:op :coordinate-facility-order :facility-id "facility-1"
                                        :patch {:item "replacement conveyor parts" :quantity 50
                                                :estimated-cost 300.0 :vendor-id "vendor-2"}})

    (exec! actor "f1-scope" {:op :log-item-record :facility-id "facility-1"
                              :out-of-scope? true :patch {}})

    (exec! actor "f1-security" {:op :flag-security-concern :facility-id "facility-1"
                                 :patch {:concern "unusual weight and a faint leaking substance detected during routine intake screening"
                                         :confidence 0.92}})
    (approve! actor "f1-security")

    (exec! actor "f2-log" {:op :log-item-record :facility-id "facility-2"
                            :patch {:items-intake 210 :items-sorted 205 :items-delivered 198}})

    (exec! actor "f3-log" {:op :log-item-record :facility-id "facility-3"
                            :patch {:items-intake 10}})

    (exec! actor "f4-log" {:op :log-item-record :facility-id "facility-4"
                            :patch {:items-intake 10}})
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger facility-id]
  (last (filter #(= (:facility-id %) facility-id) ledger)))

(defn- status-cell [ledger facility-id]
  (let [f (last-fact-for ledger facility-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (case rule
          :facility-unverified "<span class=\"critical\">HARD hold &middot; unverified/unlicensed facility</span>"
          :vendor-unverified "<span class=\"critical\">HARD hold &middot; unverified vendor</span>"
          :scope-excluded "<span class=\"critical\">HARD hold &middot; scope-excluded</span>"
          (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>")))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- facility-registration-badge [{:keys [registered? verified? license-active?]}]
  (cond
    (and registered? verified? license-active?)
    "<span class=\"ok\">registered &middot; verified &middot; license active</span>"

    (and registered? verified? (not license-active?))
    "<span class=\"warn\">verified &middot; license NOT active</span>"

    (and registered? (not verified?))
    "<span class=\"warn\">registered &middot; unverified</span>"

    :else "<span class=\"critical\">unregistered</span>"))

(defn- facility-row [ledger {:keys [facility-id name] :as facility}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc facility-id) (esc name)
          (facility-registration-badge facility)
          (status-cell ledger facility-id)))

(defn- ledger-row [{:keys [t op facility-id disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc facility-id)
          (esc (or (some->> basis (map name) (str/join ", ")) (some-> disposition name) ""))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own closed op contract (README
  ;; `Ops` table, `postalops.governor`/`postalops.phase`) -- documentation
  ;; of fixed behavior, not runtime telemetry, so it is legitimately
  ;; hand-described rather than derived from a live run.
  ["        <tr><td><code>:log-item-record</code></td><td><span class=\"ok\">phase-3 auto when clean</span></td></tr>"
   "        <tr><td><code>:schedule-route-operation</code></td><td><span class=\"ok\">phase-3 auto when clean</span></td></tr>"
   "        <tr><td><code>:coordinate-facility-order</code></td><td><span class=\"ok\">phase-3 auto when clean &amp; low cost</span> &middot; <span class=\"warn\">ALWAYS human approval above the cost threshold</span> &middot; vendor independently verified, never self-reported</td></tr>"
   "        <tr><td><code>:flag-security-concern</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto, any phase</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        facilities (store/all-facility-records db)
        facility-rows (str/join "\n" (map (partial facility-row ledger) facilities))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-5310 &middot; postal activities operations coordination</title><style>"
   (jp-go-dds.skin/dds+skin)
   "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Postal activities (ISIC 5310) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · never touches mail-content inspection, interception or contents-based refusal</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Sorting facilities &amp; delivery-route carriers</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>postalops.store</code> via <code>postalops.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Facility</th><th>Name</th><th>Registration status</th><th>Last coordination status</th></tr></thead>\n"
     "      <tbody>\n"
     facility-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (PostalOps Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. A facility that is registered and verified but whose carrier license is not currently active is treated exactly like an unverified facility. Mail-content inspection, interception authorization and contents-based delivery refusal are permanently out of scope — postal secrecy — see governor scope-exclusion.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Facility</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/coordination-log db)) "committed coordination records )")))
