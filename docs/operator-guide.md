# Operator Guide

## First Deployment
1. Register operator, postal facilities/carriers and equipment/
   maintenance vendors; independently confirm each facility's business
   registration, identity verification AND active carrier-license
   status, and each vendor's registration, before seeding
   `postalops.store`.
2. Import existing item-record (intake/sort/delivery-status metadata),
   route-scheduling and facility-order history.
3. Run read-only item-record-logging and route-operation dry-runs
   (Phase 0-1).
4. Configure the rollout phase and the `coordinate-facility-order`
   cost-escalation threshold for human sign-off paths.
5. Publish a dry-run security-concern flag and audit export.

## Minimum Production Controls
- facility-registration/verification/license-active check before ANY
  proposal for that facility
- vendor-registration/verification check before ANY
  `:coordinate-facility-order` proposal
- governor gate on every proposal before commit
- human sign-off for `:flag-security-concern` (always) and high-cost
  `:coordinate-facility-order` proposals
- audit export for every commit, hold and approval
- backup manual back-office process
- never route mail-content data through this actor's proposal/logging
  path -- item-record logging is metadata only (sender/recipient/
  tracking/status)

## Certification
Certified operators must prove facility/vendor-verification discipline,
governor-bypass resistance, evidence-backed security-concern reporting,
and human review for every escalation-gated action -- and must never
attempt to route a mail-content-inspection, interception-authorization,
or contents-based-refusal decision through this actor, which is
structurally out of scope under postal secrecy.
