# Security Policy

This project handles postal sorting/routing operations and
suspected-security-concern workflows. Mail carries a strong
privacy/confidentiality expectation (postal secrecy) -- treat
vulnerabilities as potentially high impact even when the demo data is
synthetic.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real customer, employee or carrier data exposure
- authorization bypass
- PostalOpsGovernor bypass
- audit-ledger tampering
- over-disclosure in security-concern reports or exports (including any
  path that would leak mail contents, which this actor must never
  capture in the first place)
- tenant isolation failures

## Reporting

Use GitHub private vulnerability reporting when available for the repository.
If that is unavailable, contact the repository maintainers through the
cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on customer/employee/carrier data, policy enforcement or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real customer, employee and carrier data outside this
  repository, and never capture mail contents in this actor's data
  model at all.
- Run policy tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
