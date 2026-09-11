(ns postalops.governor
  "PostalOpsGovernor -- the independent compliance layer that earns the
  PostalOpsAdvisor the right to commit. The advisor has no notion of
  whether a target sorting facility or delivery-route carrier is
  actually a registered, independently verified postal operator holding
  an active carrier license, whether a named equipment/maintenance
  vendor is itself a registered/verified counterparty, whether its own
  proposed `:effect` secretly claims a direct actuation instead of a
  mere proposal, or whether it has silently drifted into a permanently
  out-of-scope decision area (finalizing a mail-content-inspection
  decision, a mail-interception authorization, or a contents-based
  delivery-refusal determination), so this MUST be a separate system
  able to *reject* a proposal and fall back to HOLD.

  Postal activities is legally and constitutionally distinct from a
  freight/parcel-transport sibling actor in this batch: mail carries a
  strong PRIVACY/confidentiality expectation in most jurisdictions
  (postal secrecy / the inviolability of correspondence -- e.g.
  constitutional or statutory protections against opening, reading or
  disclosing the contents of sealed mail without independent legal
  authority). This actor's closed op allowlist therefore NEVER includes
  any op that directly finalizes a mail-content-inspection decision, a
  mail-interception authorization, or a contents-based delivery-refusal
  determination -- that territory is a HARD, permanent, un-overridable
  block, never auto-commit eligible and never escalate-then-approve
  eligible either (there is no proposal shape in the closed allowlist
  that could ever legitimately reach it). `:flag-security-concern` (the
  only op that may touch adjacent territory at all, and only as a
  PHYSICAL-SAFETY intake-screening OBSERVATION -- unusual weight,
  leaking substance, suspicious protrusion, per standard postal-security
  protocol, NEVER a content-inspection finalization) always escalates to
  a human. This is a PRIVACY/legal-authority exclusion, distinct in kind
  from the physical-safety exclusions used by sibling freight/transport
  actors this batch (e.g. a cargo-safety-inspection-finalization
  exclusion) -- postal secrecy bars the actor from ever ruling on mail
  CONTENTS at all, safety or otherwise; only a security-observation
  FLAG, never a content determination, is in scope.

  This actor's scope is deliberately narrow -- SORTING/ROUTING LOGISTICS
  COORDINATION ONLY (mail-item intake/sort/delivery-status metadata
  logging, sorting-facility/delivery-route scheduling, sorting-
  equipment/facility-maintenance procurement coordination, suspected-
  security-concern flagging). It NEVER performs or authorizes:
    - directly finalizing a mail-content-inspection decision (ruling on
      what is inside a sealed mail item)
    - directly authorizing a mail interception (diverting, opening or
      withholding a mail item from its addressee outside ordinary
      delivery)
    - directly finalizing a contents-based delivery-refusal
      determination (ruling an item undeliverable because of what it
      contains, as opposed to a routing/addressing/logistics reason)

  Four HARD checks, ALL permanent, un-overridable by any human approval:

    1. Postal-facility/carrier unverified -- the target sorting facility
                                     or delivery-route carrier record
                                     must exist AND be independently
                                     confirmed `:registered?`,
                                     `:verified?` AND `:license-active?`
                                     in the store before ANY proposal for
                                     it may commit or even escalate.
                                     Never trusts a proposal's own claim
                                     about the facility -- re-derived
                                     from the facility's own record, the
                                     same 'ground truth, not self-report'
                                     discipline every sibling actor's
                                     governor uses. A facility that is
                                     registered and verified but whose
                                     carrier license is NOT currently
                                     active (e.g. pending renewal) is
                                     treated exactly like an unverified
                                     facility -- the postal-specific
                                     adaptation of every sibling actor's
                                     'verified counterparty' gate.
    2. Vendor unverified          -- for `:coordinate-facility-order`
                                     ONLY, the proposal's own drafted
                                     `:value` must name a `:vendor-id`
                                     that resolves to an independently
                                     `:registered?`/`:verified?`
                                     equipment/maintenance vendor record.
                                     A missing vendor-id, or one that
                                     resolves to an unregistered or
                                     unverified vendor, is a HARD block.
    3. Effect not :propose        -- every proposal's `:effect` MUST be
                                     `:propose`. Any other effect value
                                     is, by construction, a claim to
                                     directly actuate/commit outside
                                     governance -- HARD block, not merely
                                     low-confidence.
    4. Scope exclusion            -- ANY proposal (regardless of op)
                                     whose op, summary, rationale, cites
                                     or draft value touches directly
                                     finalizing a mail-content-inspection
                                     decision, a mail-interception
                                     authorization, or a contents-based
                                     delivery-refusal determination is a
                                     HARD, PERMANENT block -- this
                                     actor's charter excludes that
                                     territory structurally (postal
                                     secrecy), not as a rollout
                                     milestone. Evaluated UNCONDITIONALLY
                                     on every proposal. An op outside the
                                     closed four-op allowlist is the SAME
                                     failure mode (an advisor proposing
                                     something it was never authorized to
                                     propose) and is folded into this
                                     same check. `:flag-security-concern`
                                     itself is never excluded by this
                                     check -- surfacing a physical-safety
                                     intake-screening observation for a
                                     human is exactly this actor's job;
                                     only FINALIZING/authorizing/ruling-
                                     on the underlying mail-content
                                     question is excluded (see
                                     `scope-excluded-terms` below --
                                     phrased as the finalization/
                                     authorization ACTION, never a bare
                                     noun like 'mail', 'contents' or
                                     'inspect', so the default mock
                                     advisor's own `:flag-security-
                                     concern` rationale never self-trips
                                     this check).

  Two ESCALATE (SOFT) gates, either forces human sign-off:
    - LLM confidence below the floor.
    - The op is `:flag-security-concern` -- ALWAYS escalates to a human,
      regardless of confidence, regardless of how clean the proposal
      otherwise is. `postalops.phase` independently agrees:
      `:flag-security-concern` is never a member of any phase's `:auto`
      set either -- two layers, not one. This op may NEVER become
      auto-commit-eligible -- it is the surfacing step for a
      suspicious/hazardous-item-at-intake observation, never a content
      determination, and postal secrecy means there is no phase at
      which this actor is ever trusted to rule on mail contents itself.
    - A `:coordinate-facility-order` whose drafted `:value` names an
      `:estimated-cost` above `facility-order-cost-threshold` -- a
      large-value equipment/maintenance procurement proposal always
      needs a human sign-off, even when the governor and phase would
      otherwise allow auto-commit."
  (:require [kotoba.lang.text :as str]
            [postalops.store :as store]))

(def confidence-floor 0.6)

(def facility-order-cost-threshold
  "Example single-facility sorting-equipment/maintenance-procurement
  threshold (USD-equivalent units, domain-illustrative -- not a
  universal cross-domain constant). A `:coordinate-facility-order`
  proposal citing an `:estimated-cost` above this value ALWAYS escalates
  to human sign-off, regardless of confidence or rollout phase."
  1500.0)

(def allowed-ops
  "The closed proposal-op allowlist -- an op outside this set is a scope
  violation by construction (see `scope-exclusion-violations`). CRITICAL:
  no op that directly finalizes a mail-content-inspection decision, a
  mail-interception authorization, or a contents-based delivery-refusal
  determination is EVER a member of this set -- such an op would be, by
  construction, a permanent scope violation, not merely un-implemented.
  This actor coordinates SORTING/ROUTING LOGISTICS ONLY."
  #{:log-item-record :schedule-route-operation
    :coordinate-facility-order :flag-security-concern})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not.
  `:flag-security-concern` can never be promoted to auto-commit-eligible
  at any phase -- see `postalops.phase`."
  #{:flag-security-concern})

(def scope-excluded-terms
  "Case-insensitive substrings that mark a proposal as touching a
  permanently out-of-scope decision area -- directly finalizing a
  mail-content-inspection decision, a mail-interception authorization,
  or a contents-based delivery-refusal determination. Scanned across the
  proposal's op/summary/rationale/cites/value, never trusting the
  advisor's own framing of its intent.

  CRITICAL: every term here is phrased as the finalization/authorization
  ACTION (e.g. 'finalized the mail-content-inspection decision',
  'authorized the mail interception'), never a bare noun like 'mail',
  'contents' or 'inspect' -- a bare noun would accidentally match inside
  this actor's own legitimate `:flag-security-concern` default proposal
  text (whose whole job is to talk about a suspicious/hazardous item
  observed at intake) and self-block the happy path. See
  `postalops.governor-test/default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  for the regression test."
  ["finalize the mail-content-inspection decision" "finalized the mail-content-inspection decision" "finalizes the mail-content-inspection decision"
   "authorize the mail interception" "authorized the mail interception" "authorizing the mail interception"
   "authorize interception of the mail item" "authorized interception of the mail item" "authorizing interception of the mail item"
   "inspect the contents of the mail item" "inspected the contents of the mail item" "inspecting the contents of the mail item"
   "open the mail item to inspect its contents" "opened the mail item to inspect its contents" "opening the mail item to inspect its contents"
   "determine delivery refusal based on contents" "determined delivery refusal based on contents" "determining delivery refusal based on contents"
   "refuse delivery based on the item's contents" "refused delivery based on the item's contents" "refusing delivery based on the item's contents"
   "rule the item undeliverable based on its contents" "ruled the item undeliverable based on its contents" "ruling the item undeliverable based on its contents"
   "finalize the contents-based delivery refusal" "finalized the contents-based delivery refusal" "finalizes the contents-based delivery refusal"
   "郵便物の内容を検査すると確定した" "郵便物の内容検査を確定した" "内容物検査の最終判断を下した"
   "開封して中身を確認した" "開封して内容物を確認した"
   "内容物に基づき配達を拒否すると確定した" "内容に基づき配達拒否を確定した"
   "通信の秘密を侵害して開封した" "傍受を許可した" "傍受を承認した"])

;; ----------------------------- checks -----------------------------

(defn- facility-unverified-violations
  "The target postal facility (sorting depot or delivery-route carrier)
  must exist AND be independently `:registered?`, `:verified?` AND
  `:license-active?` in the store -- never trust the proposal's own
  `:facility-id` claim without a facility lookup. A facility that is
  registered and verified but whose carrier license is not currently
  active (e.g. pending renewal) is STILL a HARD hold."
  [{:keys [facility-id]} st]
  (let [f (store/facility-record st facility-id)]
    (when-not (and f (:registered? f) (:verified? f) (:license-active? f))
      [{:rule :facility-unverified
        :detail (str facility-id " は未登録・未検証、またはキャリアライセンスが無効な郵便施設/配達キャリア -- いかなる提案も進められない")}])))

(defn- vendor-unverified-violations
  "For `:coordinate-facility-order` ONLY, the proposal's own drafted
  `:value` must name a `:vendor-id` that resolves to an independently
  `:registered?`/`:verified?` equipment/maintenance vendor record. A
  missing vendor-id, or one that resolves to an unregistered/unverified
  vendor, is a HARD block -- never trust the proposal's own vendor claim
  without a store lookup, the SAME 'ground truth, not self-report'
  discipline as `facility-unverified-violations`, reapplied to the
  maintenance/equipment counterparty."
  [proposal st]
  (when (= :coordinate-facility-order (:op proposal))
    (let [vendor-id (get-in proposal [:value :vendor-id])
          v (and vendor-id (store/vendor-record st vendor-id))]
      (when-not (and v (:registered? v) (:verified? v))
        [{:rule :vendor-unverified
          :detail (str (or vendor-id "(vendor-id missing)")
                        " は未登録または未検証の設備/保守ベンダー -- 発注調整提案を進められない")}]))))

(defn- effect-not-propose-violations
  "`:effect` must ALWAYS be `:propose` -- any other value is a claim to
  directly actuate/commit outside governance."
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- text-blob
  "Flatten every advisor-authored field on a proposal into one lower-cased
  blob the scope-exclusion scan checks."
  [proposal]
  (str/lower (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- scope-exclusion-violations
  "HARD, PERMANENT block: a proposal outside the closed op allowlist, or
  one whose content touches directly finalizing a mail-content-
  inspection decision, a mail-interception authorization, or a
  contents-based delivery-refusal determination, regardless of
  confidence or how clean every other check is. Evaluated
  UNCONDITIONALLY on every proposal -- postal secrecy means this actor
  structurally never rules on mail contents, at any phase, under any
  approval."
  [proposal]
  (let [op (:op proposal)
        blob (text-blob proposal)]
    (cond
      (not (contains? allowed-ops op))
      [{:rule :op-not-allowed
        :detail (str (pr-str op) " は許可された操作(closed allowlist)に含まれない")}]

      (some #(str/includes? blob %) scope-excluded-terms)
      [{:rule :scope-excluded
        :detail "郵便物の内容検査・傍受の許可・内容に基づく配達拒否の確定など、通信の秘密に触れる確定行為(mail-content-inspection/interception/contents-based-refusal finalization)は永久に禁止"}])))

(defn- high-cost-facility-order?
  "A `:coordinate-facility-order` proposal citing an `:estimated-cost`
  above `facility-order-cost-threshold` -- always needs human sign-off
  (SOFT escalate, not a hard block: the order itself is in scope, only
  its size requires a human)."
  [proposal]
  (and (= :coordinate-facility-order (:op proposal))
       (some-> proposal :value :estimated-cost (> facility-order-cost-threshold))))

(defn check
  "Censors a PostalOpsAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal store]
  (let [facility-id (or (:facility-id proposal) (:facility-id request))
        hard (into []
                   (concat (facility-unverified-violations {:facility-id facility-id} store)
                           (vendor-unverified-violations proposal store)
                           (effect-not-propose-violations proposal)
                           (scope-exclusion-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (or (always-escalate-ops (:op proposal))
                              (high-cost-facility-order? proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :facility-id (:facility-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
