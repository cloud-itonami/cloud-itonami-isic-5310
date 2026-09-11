(ns postalops.store
  "SSoT for the ISIC-5310 'Postal activities' (universal-service mail
  delivery -- intake, sorting, routing, franking, and delivery of letters
  and small parcels under a postal-carrier license) operations-
  COORDINATION actor, behind a `Store` protocol so the backend is a swap,
  not a rewrite -- the same seam every `cloud-itonami-isic-*` actor in
  this fleet uses.

  Postal activities is legally and constitutionally distinct from a
  freight/parcel-transport sibling actor (e.g. ISIC 4923 road freight
  transport, ISIC 5210 warehousing): mail carries a strong privacy/
  confidentiality expectation in most jurisdictions (postal secrecy /
  the inviolability of correspondence). This actor's primary gate is a
  POSTAL-FACILITY/CARRIER-LICENSE verification check -- a sorting
  facility or delivery-route carrier must be independently registered,
  verified, AND hold an active postal-carrier license before ANY
  coordination proposal targeting it may commit or even escalate. See
  `postalops.governor`'s `facility-unverified-violations`.

  This actor coordinates the back-office SORTING/ROUTING LOGISTICS of a
  postal operator: mail-item intake/sort/delivery-status metadata
  logging (sender/recipient/tracking -- NEVER content), sorting-
  facility/delivery-route scheduling, sorting-equipment/facility
  maintenance procurement with registered vendors, and suspected-
  security-concern flagging (a physical-safety intake screening
  observation -- unusual weight, leaking substance, suspicious
  protrusion -- handled per standard postal-security protocol, NEVER a
  content-inspection finalization). It NEVER finalizes a mail-content-
  inspection decision, a mail-interception authorization, or a
  contents-based delivery-refusal determination -- see
  `postalops.governor`'s `scope-exclusion-violations`, a HARD,
  permanent, un-overridable block. This is a PRIVACY/legal-authority
  exclusion, distinct from the physical-safety exclusions used by
  sibling freight/transport actors in this batch.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/
  demo (no deps). A `facilities` directory keyed by `:facility-id`
  STRING and a `vendors` directory keyed by `:vendor-id` STRING (never
  keywords -- consistent keying from the start).

  A registered/verified/license-active postal facility (or carrier)
  record must exist before ANY proposal targeting that facility may
  ever commit or escalate -- `postalops.governor`'s
  `facility-unverified-violations` re-derives this from the facility's
  own `:registered?`/`:verified?`/`:license-active?` fields, never from
  proposal self-report. A `:coordinate-facility-order` proposal
  additionally names a registered equipment/maintenance vendor via its
  own `:vendor-id`; the SAME 'ground truth, not self-report' discipline
  applies via `vendor-unverified-violations`.

  The ledger stays append-only: which facility a proposal targeted,
  which operation, on what basis, committed/held/escalated and approved
  by whom is always a query over an immutable log.")

(defprotocol Store
  (facility-record [s facility-id] "Registered postal facility/carrier record, or nil.
    Facility map: {:facility-id .. :name .. :registered? bool :verified? bool
                   :license-active? bool}.")
  (all-facility-records [s])
  (vendor-record [s vendor-id] "Registered equipment/maintenance vendor record, or nil.
    Vendor map: {:vendor-id .. :name .. :registered? bool :verified? bool}.")
  (all-vendor-records [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (coordination-log [s] "the append-only committed coordination-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-facility-records [s facilities] "replace/seed the facility directory (map facility-id->facility)")
  (with-vendor-records [s vendors] "replace/seed the vendor directory (map vendor-id->vendor)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained facility/vendor directory covering both the
  happy path and the governor's own hard checks, so the actor + tests
  run offline."
  []
  {:facilities
   {"facility-1" {:facility-id "facility-1" :name "Riverside Sorting & Delivery Depot"
                  :registered? true :verified? true :license-active? true}
    "facility-2" {:facility-id "facility-2" :name "Northgate Regional Sorting Hub"
                  :registered? true :verified? true :license-active? true}
    "facility-3" {:facility-id "facility-3" :name "Downtown Satellite Drop Point (in intake)"
                  :registered? true :verified? false :license-active? false}
    "facility-4" {:facility-id "facility-4" :name "New Rural Route Carrier Awaiting License Renewal"
                  :registered? true :verified? true :license-active? false}}
   :vendors
   {"vendor-1" {:vendor-id "vendor-1" :name "Northgate Sorting-Equipment Maintenance Co."
                :registered? true :verified? true}
    "vendor-2" {:vendor-id "vendor-2" :name "Unverified Conveyor Parts Broker"
                :registered? true :verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (facility-record [_ facility-id] (get-in @a [:facilities facility-id]))
  (all-facility-records [_] (sort-by :facility-id (vals (:facilities @a))))
  (vendor-record [_ vendor-id] (get-in @a [:vendors vendor-id]))
  (all-vendor-records [_] (sort-by :vendor-id (vals (:vendors @a))))
  (ledger [_] (:ledger @a))
  (coordination-log [_] (:coordination-log @a))
  (commit-record! [_ record]
    (swap! a update :coordination-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-facility-records [s facilities] (when (seq facilities) (swap! a assoc :facilities facilities)) s)
  (with-vendor-records [s vendors] (when (seq vendors) (swap! a assoc :vendors vendors)) s))

(defn seed-db
  "A MemStore seeded with the demo facility/vendor directory. The
  deterministic default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :coordination-log []))))

(defn mem-store
  "A MemStore seeded with explicit `facilities`/`vendors` maps
  (facility-id/vendor-id string -> record map) -- the primary test/dev
  entry point. Either may be empty (an unregistered-everywhere
  facility)."
  ([facilities] (mem-store facilities {}))
  ([facilities vendors]
   (->MemStore (atom {:facilities (or facilities {}) :vendors (or vendors {})
                       :ledger [] :coordination-log []}))))
