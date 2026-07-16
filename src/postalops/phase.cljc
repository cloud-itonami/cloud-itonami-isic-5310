(ns postalops.phase
  "Phase 0->3 staged rollout for the ISIC-5310 'Postal activities'
  (universal-service mail delivery) operations-coordination actor.

    Phase 0  read-only              -- no writes, still governor-gated.
    Phase 1  assisted-logging       -- item-record logging allowed,
                                       every write needs human approval.
    Phase 2  assisted-coordination  -- adds route-operation scheduling
                                       and facility-order proposals,
                                       still approval-gated.
    Phase 3  supervised auto        -- governor-clean, high-confidence
                                       `:log-item-record`/
                                       `:schedule-route-operation`/
                                       `:coordinate-facility-order` may
                                       auto-commit. `:flag-security-
                                       concern` NEVER auto-commits, at
                                       any phase, and a high-cost
                                       `:coordinate-facility-order` still
                                       escalates even at phase 3 (the
                                       governor's own `high-stakes?`
                                       keeps it out of `:commit`).

  `:flag-security-concern` is deliberately ABSENT from every phase's
  `:auto` set, including phase 3 -- a permanent structural fact, not a
  rollout milestone still to come. Postal secrecy means this actor is
  never trusted to rule on mail contents itself, at any phase --
  flagging a suspicious/hazardous item at intake always needs a human to
  actually look at it. `postalops.governor`'s own `always-escalate-ops`
  enforces the same invariant independently -- two layers, not one,
  agree on this."
  (:require [postalops.governor :as governor]))

(def read-ops #{})
(def write-ops governor/allowed-ops)

;; NOTE the invariant: `:flag-security-concern` is a member of
;; `write-ops` (governor-gated like any write) but is NEVER a member of
;; any phase's `:auto` set below. Do not add it there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed
  to auto-commit when governor-clean>}."
  {0 {:label "read-only"              :writes #{}                                                                :auto #{}}
   1 {:label "assisted-logging"       :writes #{:log-item-record}                                                :auto #{}}
   2 {:label "assisted-coordination"  :writes #{:log-item-record :schedule-route-operation
                                                 :coordinate-facility-order}                                     :auto #{}}
   3 {:label "supervised-auto"        :writes write-ops
      :auto #{:log-item-record :schedule-route-operation :coordinate-facility-order}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE
    (:phase-approval), even if the governor was clean.
  - `:flag-security-concern` is never auto-eligible at any phase, so it
    always escalates once the governor clears it (or holds if the
    governor doesn't). A high-cost `:coordinate-facility-order` never
    reaches this function with `:commit` in the first place -- the
    governor's own `high-stakes?` already turned it into `:escalate`
    upstream in `verdict->disposition`."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a PostalOpsGovernor verdict to a base disposition before the
  phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
