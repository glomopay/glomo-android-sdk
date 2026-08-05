## What and why

<!-- What changed, and why it needed changing. Link any relevant issue or discussion. -->

## How this was verified

<!-- Devices/emulators and Android versions tested. Attach the checkout flow if UI changed. -->

## Checklist

- [ ] No customer data in this PR — no real order IDs, payment IDs, customer names,
      emails, phone numbers, card numbers, bank account numbers, or KYC document
      contents in code, tests, fixtures, screenshots, or the description.
- [ ] No credentials — no API keys, tokens, keystores, or signing material.
      Sandbox keys only in tests.
- [ ] No new third-party dependencies. (See CONTRIBUTING.md — this is a hard rule.
      If you believe one is unavoidable, stop and open a discussion first.)
- [ ] Public API changes are intentional, and `apiDump` has been re-run and committed.
- [ ] No card entry, tokenization, or PAN handling added. (See CONTRIBUTING.md.)
- [ ] Release-build logging is off by default; no request or response bodies logged.
- [ ] CHANGELOG.md updated if merchant-visible behaviour changed.
