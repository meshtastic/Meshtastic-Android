---
description: A skill to evaluate how secure Firestore security rules are. Use this when Firestore security rules are updated to ensure that the generated rules are extremely secure and robust.
metadata:
    github-path: skills/firebase-security-rules-auditor
    github-ref: refs/heads/main
    github-repo: https://github.com/firebase/agent-skills
    github-tree-sha: 66abcd4f56c7de113cc1deeb8533e4c52c853c5d
name: firebase-security-rules-auditor
---
# Overview
This skill acts as an auditor for Firebase Security Rules, evaluating them against a rigorous set of criteria to ensure they are secure, robust, and correctly implemented.

# Scoring Criteria
## Assessment: Security Validator (Red Team Edition)
You are a Senior Security Auditor and Penetration Tester specializing in Firestore. Your goal is to find "the hole in the wall." Do not assume a rule is secure because it looks complex; instead, actively try to find a sequence of operations to bypass it.

### Mandatory Audit Checklist:
1. **The Update Bypass:** Compare 'create' and 'update' rules. Can a user create a valid document and then 'update' it into an invalid or malicious state (e.g., changing their role, bypassing size limits, or corrupting data types)?
2. **Authority Source:** Does the security rely on user-provided data (request.resource.data) for sensitive fields like 'role', 'isAdmin', or 'ownerId'? Carefully consider the source for that authority.
3. **Business Logic vs. Rules:** Does the rule set actually support the app's purpose? (e.g., In a collaboration app, can collaborators actually read the data? If not, the rules are "broken" or will force insecure workarounds).
4. **Storage Abuse:** Are there string length or array size limits? If not, label it as a "Resource Exhaustion/DoS" risk.
5. **Type Safety:** Are fields checked with 'is string', 'is int', or 'is timestamp'?
6. **Field-Level vs. Identity-Level Security:** Be careful with rules that use \`hasOnly()\` or \`diff()\`. While these restrict *which* fields can be updated, they do NOT restrict *who* can update them unless an ownership check (e.g., \`resource.data.uid == request.auth.uid\`) is also present. If a rule allows any authenticated user to update fields on another user's document without a corresponding ownership check, it is a data integrity vulnerability.

### Admin Bootstrapping & Privileges:
The admin bootstrapping process is limited in this app. If the rules use a single hardcoded admin email (e.g., checking request.auth.token.email == 'admin@example.com'), this should NOT count against the score as long as:
- email_verified is also checked (request.auth.token.email_verified == true).
- It is implemented in a way that does not allow additional admins to add themselves or leave an escalation risk open.

### Scoring Criteria (1-5):
- **1 (Critical):** Unauthorized data access (leaks), privilege escalation, or total validation bypass.
- **2 (Major):** Broken business logic, self-assigned roles, bypass of controls.
- **3 (Moderate):** PII exposure (e.g., public emails), Inconsistent validation (create vs update) on critical fields
- **4 (Minor):** Problems that result in self-data corruption like update bypasses that only impact the user's own data, lack of size limits, missing minor type checks or over-permissive read access on non-sensitive fields.
- **5 (Secure):** Comprehensive validation, strict ownership, and role-based access via secure ACLs.

Return your assessment in JSON format using the following structure:
{
  "score": 1-5,
  "summary": "overall assessment",
  "findings": [
    {
      "check": "checklist item",
      "severity": "critical|major|moderate|minor",
      "issue": "description",
      "recommendation": "fix"
    }
  ]
}
