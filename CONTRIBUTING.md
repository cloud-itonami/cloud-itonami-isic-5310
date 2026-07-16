# Contributing

`cloud-itonami-isic-5310` accepts contributions to the OSS blueprint,
capability bindings, policy tests, documentation and operator model.

## Development

```bash
clojure -M:test
clojure -M:lint
```

## Rules
- Do not commit real customer, employee, carrier or mail-content data.
- Keep item-record logging, route-operation scheduling, facility-order
  coordination and security-concern flagging behind the
  PostalOpsGovernor.
- Treat postal-operations workflows as high-risk: add tests for
  facility/vendor verification, effect discipline, scope exclusion,
  escalation and audit logging.
- Never phrase a governor scope-exclusion term as a bare noun (e.g.
  "mail", "contents") -- phrase it as the finalization/authorization
  ACTION (e.g. "authorized the mail interception"), and add/extend the
  `default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  regression test for any new term. A bare-noun term will self-trip this
  actor's own legitimate `:flag-security-concern` happy path -- see
  `postalops.governor/scope-excluded-terms`'s docstring.
- Never add an op, to the closed allowlist or otherwise, that could
  directly finalize a mail-content-inspection decision, a
  mail-interception authorization, or a contents-based delivery-refusal
  determination -- postal secrecy places this structurally out of
  scope, permanently, not as a rollout milestone.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests
PRs should describe: what behavior changed, which policy invariant is
affected, how it was tested, whether operator or certification docs need
updates.
