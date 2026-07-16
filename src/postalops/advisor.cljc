(ns postalops.advisor
  "PostalOpsAdvisor -- the *contained intelligence node* for the
  ISIC-5310 'Postal activities' (universal-service mail delivery --
  intake, sorting, routing, franking and delivery of letters and small
  parcels under a postal-carrier license) operations-coordination actor.

  It drafts exactly four kinds of back-office proposal from a closed
  allowlist: mail-item intake/sort/delivery-status METADATA logging
  (sender/recipient/tracking -- NEVER content), sorting-facility/
  delivery-route scheduling, sorting-equipment/facility-maintenance
  procurement coordination, and suspected-security-concern flagging (a
  physical-safety intake-screening observation, NOT a content
  inspection). CRITICAL: it is a smart-but-untrusted advisor. It returns
  a *proposal* (with a rationale + the fields it cited), never a
  committed record and NEVER a direct actuation -- every proposal's
  `:effect` is always `:propose`. Every output is censored downstream by
  `postalops.governor` before anything touches the SSoT.

  This advisor NEVER drafts a direct finalization of a mail-content-
  inspection decision, a mail-interception authorization, or a
  contents-based delivery-refusal determination -- those are permanently
  out of scope for this actor (postal secrecy / the inviolability of
  correspondence), not merely un-implemented. `postalops.governor`'s
  `scope-exclusion-violations` independently re-scans every proposal for
  exactly this failure mode (a compromised or confused advisor drifting
  into scope it must never touch) and HARD-holds it, regardless of
  confidence or op.

  Like every sibling actor's advisor, this is a deterministic mock so the
  actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:op          kw             ; echoes the request op
     :facility-id str
     :summary     str            ; human-facing draft / finding
     :rationale   str            ; why -- SCANNED by the scope-exclusion gate
     :cites       [str ..]       ; facts/sources the advisor used -- SCANNED too
     :effect      :propose       ; ALWAYS :propose -- never a direct actuation
     :value       map            ; the draft payload a human/system would review
     :confidence  0..1}")

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

;; ----------------------------- proposal generators -----------------------------

(defn- propose-item-record
  "Draft a mail-item intake/sort/delivery-status metadata log entry --
  sender/recipient/tracking counts and status only, NEVER the contents
  of any item. Pure logging of observed handling events -- never a
  content-inspection finalization."
  [_db {:keys [facility-id patch]}]
  {:op         :log-item-record
   :facility-id facility-id
   :summary    (str facility-id " の郵便物受付/区分/配達状況を記録: " (pr-str (keys patch)))
   :rationale  "受付・区分・配達処理のメタデータ観察記録のみ(送り主/宛先/追跡情報のみ、内容物は含まない)。内容物の検査や判断は行わない。"
   :cites      [facility-id]
   :effect     :propose
   :value      (merge {:facility-id facility-id} patch)
   :confidence 0.93})

(defn- propose-route-operation
  "Draft a sorting-facility/delivery-route scheduling proposal (a
  logistics/dispatch-window entry, never a direct actuation)."
  [_db {:keys [facility-id patch]}]
  {:op         :schedule-route-operation
   :facility-id facility-id
   :summary    (str facility-id " の区分/配達ルート予定を提案: " (pr-str (keys patch)))
   :rationale  "区分施設/配達ルートのスケジュール調整提案のみ。最終的な配達実行は人間/既存運行管理システムが確定する。"
   :cites      [facility-id]
   :effect     :propose
   :value      (merge {:facility-id facility-id} patch)
   :confidence 0.88})

(defn- propose-facility-order
  "Draft a sorting-equipment/facility-maintenance procurement
  coordination request naming a registered vendor -- never a finalized
  purchase order; a human always confirms procurement."
  [_db {:keys [facility-id patch]}]
  {:op         :coordinate-facility-order
   :facility-id facility-id
   :summary    (str facility-id " 向け区分設備/施設保守の発注調整を提案: " (pr-str (keys patch)))
   :rationale  "区分設備/施設保守のための発注調整提案のみ。確定発注は人間が行う。"
   :cites      [facility-id]
   :effect     :propose
   :value      (merge {:facility-id facility-id} patch)
   :confidence 0.90})

(defn- propose-security-concern
  "Surface an observed exterior anomaly at intake (unusual weight,
  leaking substance, suspicious protrusion) per standard postal-security
  screening protocol, for HUMAN triage. This op ALWAYS escalates in
  `postalops.governor` -- never auto-committed at any phase -- regardless
  of how confident the advisor is that the concern is real. Deliberately
  reports the exterior OBSERVATION only, never a content-inspection
  finalization/interception-authorization/contents-based-refusal
  action, so the default rationale never trips the governor's
  `scope-excluded-terms` (see that var's docstring)."
  [_db {:keys [facility-id patch]}]
  {:op         :flag-security-concern
   :facility-id facility-id
   :summary    (str facility-id " の不審物/危険物懸念フラグ: " (pr-str (:concern patch "unknown")))
   :rationale  "配達物の外形異常(重量超過・液漏れ・不審な突起物等)を標準郵便セキュリティプロトコルに従って報告するのみ。最終判断は常に人間が行う。"
   :cites      [facility-id]
   :effect     :propose
   :value      (merge {:facility-id facility-id} patch)
   :confidence (or (:confidence patch) 0.85)})

;; ----------------------------- default mock advisor -----------------------------

(defn infer
  "Mock advisor: routes to the correct proposal generator."
  [_db {:keys [op out-of-scope?] :as request}]
  (let [proposal (case op
                   :log-item-record (propose-item-record _db request)
                   :schedule-route-operation (propose-route-operation _db request)
                   :coordinate-facility-order (propose-facility-order _db request)
                   :flag-security-concern (propose-security-concern _db request)
                   {})]
    ;; Test hook: allow injecting scope-excluded content to exercise the
    ;; governor's scope-exclusion block end-to-end. Must be cleared before
    ;; production use.
    (if out-of-scope?
      (update proposal :rationale str " -- actually opened the mail item to inspect its contents and authorized the mail interception")
      proposal)))

(defn trace
  "Audit fact for a proposal generated by this advisor."
  [_request proposal]
  {:t       :advisor-proposal
   :op      (:op proposal)
   :facility-id (:facility-id proposal)
   :summary (:summary proposal)
   :confidence (:confidence proposal)})

(defn mock-advisor
  "The deterministic default advisor for offline demo/test."
  []
  (reify Advisor
    (-advise [_ _store request]
      (infer nil request))))
