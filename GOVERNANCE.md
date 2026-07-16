# Governance

`cloud-itonami-isic-5310` is an OSS open-business blueprint for postal
sorting/routing-logistics operations coordination (ISIC Rev.5 5310 --
postal activities).

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a proposal for an unverified/unregistered/license-inactive facility,
  or a facility order naming an unverified/unregistered vendor, can
  never commit.
- the PostalOpsGovernor remains independent of the advisor.
- hard policy violations (non-`:propose` effect, mail-content-
  inspection/interception/contents-based-refusal finalization content,
  an op outside the closed allowlist) cannot be overridden by human
  approval.
- every item-record log, route-operation schedule, facility-order
  coordination and security-concern flag is auditable.
- customer, employee and carrier data stays outside Git, and mail
  CONTENT is never captured by this actor at all -- only handling
  metadata (sender/recipient/tracking/status).

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or
license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is
a separate trust mark and should require security, audit and data-flow
review.

Certified operators can lose certification for:
- bypassing item-record, route-operation, facility-order or
  security-concern policy checks
- mishandling customer, employee or carrier data, or mail contents
- misrepresenting certification status
- failing to respond to security incidents or postal-secrecy complaints

## License
AGPL-3.0-or-later.
