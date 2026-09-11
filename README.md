# cloud-itonami-isic-5310

Open Business Blueprint for **ISIC Rev.5 5310**: postal activities --
universal-service mail delivery (intake, sorting, routing, franking and
delivery of letters and small parcels under a postal-carrier license),
legally and constitutionally distinct from every freight/parcel-
transport sibling in this fleet: mail carries a strong PRIVACY/
confidentiality expectation in most jurisdictions (postal secrecy / the
inviolability of correspondence).

This repository publishes a postal sorting/routing-logistics
operations-COORDINATION actor -- mail-item intake/sort/delivery-status
metadata logging, sorting-facility/delivery-route scheduling, sorting-
equipment/facility-maintenance procurement coordination with registered
vendors, and suspected-security-concern flagging -- as an OSS business
that any qualified operator can fork, deploy, run, improve and sell, so
an independent postal operator never surrenders its operations data to
a closed back-office SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, in-mem/Datomic checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **PostalOpsAdvisor ⊣
PostalOpsGovernor**. This blueprint's own `:itonami.blueprint/governor`
keyword, `:postal-ops-governor`, is a distinct, independent build
(verified unique across the `cloud-itonami` org at build time).

> **Why an actor layer at all?** An LLM is great at drafting an
> item-record summary, a route-scheduling proposal, or a facility-order
> request -- but it has no license to actually rule on what is inside a
> sealed mail item, no way to independently confirm a sorting facility
> or delivery-route carrier is actually a registered/verified postal
> operator holding an active carrier license, or a facility-order vendor
> is actually a registered/verified counterparty, and no notion of when
> a "flag this concern" op quietly turns into a claim to have already
> determined what is inside an item. Letting it act directly invites an
> unlicensed carrier entering the ledger, an unverified vendor receiving
> a maintenance order, or -- worst of all -- a fabricated claim to have
> inspected mail contents or authorized an interception, exposing the
> operator to real legal liability under postal-secrecy law. This
> project seals the PostalOpsAdvisor into a single node and wraps it
> with an independent **PostalOpsGovernor**, a human **approval
> workflow**, and an immutable **audit ledger**.

## Scope: sorting/routing logistics coordination only, never mail-content authority

This actor is **operations coordination only**. It never performs or
authorizes:

- directly finalizing a mail-content-inspection decision (ruling on what
  is inside a sealed mail item)
- directly authorizing a mail interception (diverting, opening or
  withholding a mail item from its addressee outside ordinary delivery)
- directly finalizing a contents-based delivery-refusal determination
  (ruling an item undeliverable because of what it contains, as opposed
  to a routing/addressing/logistics reason)

The governor's `scope-exclusion-violations` check re-scans every
proposal for this failure mode independently of the advisor's own
framing, and treats it as a HARD, permanent block regardless of
confidence or how clean everything else is. This is a PRIVACY/
legal-authority exclusion (postal secrecy), distinct in kind from the
physical-safety exclusions used by sibling freight/transport actors in
this batch. Flagging a suspicious/hazardous item observed at intake for
a human to triage is exactly this actor's job -- `:flag-security-
concern` is never excluded by this check, and it is always an exterior-
observation flag, never a content determination.

### Actuation

**Every proposal this actor generates is `:effect :propose`, never a
direct actuation.** Two independent layers enforce this
(`postalops.governor`'s `effect-not-propose-violations` HARD check and
`postalops.phase`'s phase table, which never puts `:flag-security-
concern` in any phase's `:auto` set). A human postal operations
coordinator is always the one who actually responds to a flagged
security concern or confirms a high-cost facility order -- and no
proposal shape in this actor's closed allowlist can ever reach a mail-
content determination at all.

## The core contract

```
facility/carrier registration + operations-coordination request
        |
        v
   ┌───────────────────────┐   proposal      ┌────────────────────────────┐
   │ PostalOps-             │ ─────────────▶ │ PostalOpsGovernor            │  (independent system)
   │ Advisor (sealed)      │  + citations    │ facility-unverified          │
   └───────────────────────┘                 │  (registered+verified+       │
          │                 commit ◀┼ license-active) ·                     │
          │                         │ vendor-unverified ·                  │
    record + ledger        escalate ┼ effect-not-propose ·                 │
          │              (ALWAYS for│ scope-excluded (mail-content-        │
          │       :flag-security-   │ inspection/interception/contents-    │
          │       concern/high-cost │ based-refusal finalization) ·        │
          │       facility order)   │ op-not-allowed                       │
          │                         └────────────────────────────┘
          ▼
      human approval
```

**The PostalOpsAdvisor never commits a proposal the PostalOpsGovernor
would reject, and a security-concern flag or a high-cost facility order
never commits without a human sign-off.** Hard violations (an
unregistered/unverified/license-inactive facility; an unregistered/
unverified facility-order vendor; a non-`:propose` effect; content
touching mail-content-inspection/interception/contents-based-refusal
finalization; an op outside the closed allowlist) force **hold** and
*cannot* be approved past.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
may perform physical domain work** (here: mail sorting, palletizing,
conveyor handling) under human/robot sorting-facility operations gated
by facility policy. This actor itself does not dispatch robot/hardware
actions -- it is strictly the operations-coordination layer
(item-record logging, route-scheduling coordination, facility-order
coordination, security-concern flagging) any physical-dispatch layer
could eventually feed proposals into, always gated the same way by the
independent PostalOpsGovernor.

## Features

- **Closed proposal-op allowlist**: `log-item-record`,
  `schedule-route-operation`, `coordinate-facility-order`,
  `flag-security-concern` (all `:effect :propose`). CRITICAL: no op that
  directly finalizes a mail-content-inspection decision, a
  mail-interception authorization, or a contents-based delivery-refusal
  determination is EVER a member of this allowlist.
- **Four HARD governor checks** (permanent, un-overridable):
  1. **Postal-facility/carrier unverified** (POSTAL-SPECIFIC PRIMARY
     GATE) -- the target sorting facility or delivery-route carrier's
     registration must exist AND be independently `:registered?`,
     `:verified?` AND `:license-active?`. A facility with a lapsed or
     pending carrier license (registered and verified but not currently
     license-active) is treated exactly like an unverified facility.
  2. **Vendor unverified** -- for `:coordinate-facility-order` only, the
     named equipment/maintenance vendor must exist AND be independently
     registered/verified.
  3. **Effect is :propose** -- any other `:effect` value is rejected.
  4. **Scope exclusion** (POSTAL-SECRECY / PRIVACY DIMENSION) --
     directly finalizing a mail-content-inspection decision, a
     mail-interception authorization, or a contents-based delivery-
     refusal determination, and an op outside the closed allowlist, are
     both permanently blocked. Distinct in kind from a physical-safety
     exclusion: this actor structurally never rules on mail contents at
     all, safety-motivated or otherwise.
- **Two ESCALATE (SOFT) gates**, either forces human sign-off:
  - `:flag-security-concern` -- ALWAYS escalates, regardless of
    confidence or phase. A physical-safety intake-screening flag (e.g.
    unusual weight, leaking substance, suspicious protrusion) is never
    auto-commit eligible and never itself finalizes a mail-content
    determination -- it only surfaces the exterior observation for a
    human, per standard postal-security protocol.
  - `:coordinate-facility-order` above a cost threshold -- a
    large-value procurement proposal always needs a human sign-off.
  - (LLM confidence below the floor also escalates, as with every
    sibling actor.)
- **Staged rollout** (Phase 0→3):
  - Phase 0: read-only
  - Phase 1: item-record logging only (approval-gated)
  - Phase 2: + route-operation scheduling, facility-order proposals
    (approval-gated)
  - Phase 3: auto-commits clean, high-confidence, low-cost proposals
    (security concerns and high-cost facility orders always escalate)
- **Append-only audit ledger** -- every decision is an immutable log
  entry.
- **langgraph-clj StateGraph** -- one request = one supervised run;
  human-in-the-loop via `interrupt-before`.

### Development

```bash
# Install dependencies (if inside the superproject, use :dev alias for local overrides)
kbb -M:dev -P

# Run tests
kbb -M:test

# Run linter
kbb -M:lint

# Run demo
kbb -M:run
```

### Test suite

- `test/postalops/governor_test.cljk` -- unit tests of governor hard
  checks, scope exclusion, and the self-trip regression test
- `test/postalops/advisor_test.cljk` -- advisor proposal shape and
  consistency, including a dedicated check that the default
  security-concern text never itself uses content-inspection/
  interception vocabulary
- `test/postalops/phase_test.cljk` -- rollout phase logic
- `test/postalops/governor_contract_test.cljk` -- full graph
  integration, audit trail
- `test/postalops/store_contract_test.cljk` -- Store protocol and
  MemStore implementation

### Modules

- `postalops.store` -- SSoT (MemStore, String-keyed facility/vendor
  directories, append-only ledger)
- `postalops.advisor` -- contained intelligence node (mock + real-LLM
  seam)
- `postalops.governor` -- independent compliance layer
- `postalops.phase` -- staged rollout (0→3)
- `postalops.operation` -- langgraph-clj StateGraph
- `postalops.sim` -- demo driver

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`5310`).

## Business-process coverage (honest)

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Mail-item intake/sort/delivery-status metadata logging (`:log-item-record`, sender/recipient/tracking only, never content) | Real mail-processing-equipment/OCR-sorter integration |
| Sorting-facility/delivery-route scheduling coordination (`:schedule-route-operation`) | Direct route-optimization/carrier-dispatch-system integration |
| Sorting-equipment/facility-maintenance procurement coordination with a registered, verified vendor, HARD-gated on vendor verification and a double-actuation-free single-proposal shape (`:coordinate-facility-order`) | Real procurement-system integration |
| Suspected-security-concern flagging (exterior observation only), ALWAYS human-gated (`:flag-security-concern`) | Directly finalizing any mail-content-inspection decision, mail-interception authorization, or contents-based delivery-refusal determination -- permanently out of scope under postal secrecy, not a gap |
| Immutable audit ledger for every log/schedule/order/flag decision | Franking/postage-payment-system integration -- a follow-up slice, not in this R0 |

Extending coverage is additive: add the next op (e.g. an
undeliverable-as-addressed-return-to-sender-logistics check) as its own
governed op with its own HARD checks and tests, following the SAME "an
independent governor re-verifies against the actor's own records before
any real-world act" pattern this repo's flagship checks already
establish -- and never adding an op that could touch mail-content
authority.

## Maturity

`:implemented` -- `PostalOpsAdvisor` + `PostalOpsGovernor` run as real,
tested code (see `Development` above), following the SAME
governed-actor architecture as every prior actor across this fleet, with
its own distinct, independently-named governor and its own
postal-specific facility/carrier-license verification gate plus a
postal-secrecy-specific scope exclusion in place of the physical-safety
exclusions used by sibling freight/transport actors.

## License

Code and implementation templates are AGPL-3.0-or-later.
