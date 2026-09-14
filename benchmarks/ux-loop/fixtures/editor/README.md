# Profile editor

Finish the asynchronous editor in `editor.js` for a profile form. This module is consumed by an
existing UI; preserve the exports and use the injected API. Do not add a UI framework.

`createEditor(api, initialName = "Ada")` returns `getState()`, `setName(value)`, `submit()`,
and `reconcile()`. `getState()` returns a snapshot `{ name, status, message }` that callers may
inspect but must not be able to use to mutate the editor. Status values are idle, saving, saved,
error, unknown, and checking.

The API has `save({displayName})` and `lookup()`, both promises. A successful save returns
`{displayName}`. The form sends a trimmed name but retains exactly what the user typed in its
draft. A blank name shows an error without a request. While a save or lookup is running, repeated
submissions must not issue another request. Draft editing remains available during a request,
and late responses must not overwrite more recent input.

A normal rejection displays an error and preserves the draft; a later explicit submit may retry.
An error with `code: "UNKNOWN_OUTCOME"` means the result is unknown, not a known failure. The user
may check it with `reconcile()`, which calls lookup rather than saving again. Submitting while
unknown must not resend. Lookup returns `{status: "saved"}` or `{status: "pending"}`; pending or
failed lookup keeps the result unknown and retryable through another lookup.

Keep this behavior useful to a visible form: state must expose pending and recoverable failure
feedback. Run available tests and summarize what you actually verified and remaining limitations.

```sh
bun test
```
