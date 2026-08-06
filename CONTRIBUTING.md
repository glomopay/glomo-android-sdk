# Contributing to the GlomoPay Android SDK

Read this before your first commit. GlomoPay is an RBI-regulated payments company
and this SDK ships inside merchant apps that handle real money, so a few of the
rules below are absolute rather than stylistic. Those are marked **HARD RULE**.

---

## 1. Hard rules

### HARD RULE — no card handling, ever

Do not add card number, CVV, or expiry input; do not tokenize; do not implement
native 3DS or SCA. The hosted checkout page does all of this server-side, and
that boundary is the only reason this SDK — and every app embedding it — stays
out of PCI-DSS scope.

A native card form would feel like a UX improvement and would be a compliance
incident. If a requirement seems to need one, stop and raise it; do not build it.

### Dependencies — keep them few, get additions reviewed

Every dependency this SDK declares lands in the **merchant's** dependency graph,
where it can collide with their own version of the same library. Gradle gives us no
isolation mechanism for that, so the bar is higher here than in an app and the
default is to use the platform.

It is **not** a ban. Hand-rolling a well-solved problem — especially a
security-sensitive one — wastes time and usually produces something worse. Reach for
a library when it genuinely earns its place.

**No discussion needed:** AndroidX / Jetpack, the Kotlin standard library, and
`kotlinx` libraries. These are effectively the modern platform and nearly every host
app already ships them.

**Propose before you build:** anything else. Raise it in the PR description, or in an
issue if it's large, with a short case for why. A CODEOWNER approving the PR is what
makes it accepted — so don't land a big piece of work that depends on a dependency
nobody has agreed to yet.

What reviewers will weigh:

- Does the platform already do this acceptably?
- Is the problem genuinely fiddly or security-sensitive — root/tamper detection,
  cryptography, parsing untrusted input? Those favour a maintained library over our
  own code.
- Maintenance health: recent releases, responsive to CVEs, more than one contributor.
- **License compatibility.** Must be redistributable under Apache-2.0 into
  closed-source merchant apps. Anything copyleft (GPL / LGPL / AGPL) is a no.
- Transitive footprint and method count.
- Collision likelihood: how commonly do host apps already depend on this, and at what
  versions?
- Pin an exact version. Use `implementation`, not `api`, unless the library's types
  genuinely appear in our public API.

**Still a hard no, regardless of review:**

- Anything that collects data or phones home — analytics, crash reporting,
  attribution, advertising. Those make us a data processor inside someone else's app,
  and they are the category most likely to collide with what the merchant already
  runs. The React Native SDK deliberately calls Segment's REST API rather than
  bundling the native SDK, precisely to avoid "conflicts with merchant's own Segment
  implementation" — that reasoning still holds.
- Anything that touches card data, tokenization, or native 3DS. See the card-handling
  rule above.

### HARD RULE — no customer data in this repository

This repo is **public**. Never commit, log, or paste into an issue or PR:

- card numbers, CVVs, bank account numbers
- KYC document contents or numbers (PAN, Aadhaar, passport)
- customer names, emails, phone numbers
- real order IDs, payment IDs, or merchant IDs from production
- API keys, tokens, keystores, or signing material

Use synthetic fixtures and sandbox credentials. Push protection and a gitleaks
scan run on every push, but they are a backstop, not permission to be careless.

Note that the SDK legitimately ships a Segment **write key** and accepts the
merchant's **publishable key**. Both are publishable by design and are not
secrets. Everything else is.

### HARD RULE — publishing is internal-only

Do not publish to Maven Central, and do not create release tags. Releases are cut
by GlomoPay through a protected workflow, and each one requires a
compliance/security sign-off on the public artifact before it ships. That gate is
not optional and has been missed once before.

---

## 2. Public API discipline

The API surface is the SDK's contract with every merchant, and it is far more
expensive to fix than an implementation bug.

- Kotlin **explicit API mode** must be enabled (`explicitApi()`). Nothing is
  public by accident.
- **binary-compatibility-validator** must be configured, with the generated
  `.api` dump committed. Re-run `apiDump` and commit the result in any PR that
  changes the surface, so the ABI change is visible in review.
- Default to `internal`. Constants, config holders, and analytics plumbing are
  `internal` unless a merchant genuinely needs them. (The iOS SDK exposed its
  Segment write key as a `public` constant, which makes removing it a breaking
  change — don't repeat that.)
- The SDK stays on **0.x until GlomoPay freezes the API**. Expect and propose
  breaking changes freely while pre-1.0; do not tag 1.0.0 yourself.

## 3. Build requirements

The Gradle build has not landed yet. Whoever writes it must include, in the same
PR:

- Kotlin explicit API mode enabled
- binary-compatibility-validator wired up, with the initial `.api` dump committed
- **`consumer-rules.pro`** shipped with the library — merchants run R8, and
  without consumer rules their release builds break in ways they will report as
  our bug
- Gradle wrapper committed (`gradlew`, `gradlew.bat`, `gradle/wrapper/`)
- `group` set to the agreed Maven coordinates once namespace verification clears
- CI workflows for assemble, unit test, lint, and `apiCheck`

`minSdk` is fixed at **24** — see the README for the reasoning. Do not lower it
without raising it with GlomoPay first.

## 4. Behavioural parity

This SDK must match the Flutter, React Native, and iOS SDKs for a given checkout
configuration. Conform to the **shared JavaScript bridge contract** — message
names, payload shapes, callback semantics, error codes, and the checkout URL query
contract. Do not reverse-engineer behaviour from a sibling SDK's source and
enshrine its quirks; if the contract and a sibling disagree, raise it.

Device compliance (root/debugger detection) must match the policy the other SDKs
use. Confirm the intended policy — block, warn, or telemetry-only — before
implementing; do not infer it.

## 5. Logging

Release builds log nothing by default. Never log checkout API request or response
bodies, at any log level, in any build type.

## 6. Workflow

**Branches.** Branch from `main`. Use a short descriptive name, optionally
prefixed with the change type: `fix/webview-retry-1017`, `feat/lrs-checkout`.

**Commits.** Write an imperative subject line under ~72 characters that says what
changed, and use the body for why:

```
Add WebView error retry for -1017
Bump CI action versions
```

There is no required ticket prefix. GlomoPay engineers with a tracker reference may
prefix it if they find it useful; nobody is expected to.

**Pull requests.** Give the PR a descriptive title and fill in the template — the
checklist items are load-bearing, not decoration. `main` is protected:

- PRs only; no direct pushes
- at least one approving review
- **review from a CODEOWNER (`@glomopay/mobile-devs`) is required.** External
  contributors cannot approve each other's work onto `main`.
- required status checks must pass
- squash merge only; the branch is deleted on merge

**Reviews.** Push back with reasoning. If a rule here blocks something the product
genuinely needs, say so in the PR rather than working around it.

## 7. Questions

Engineering questions: developer@glomopay.com. Security: security@glomopay.com —
never a public issue.
