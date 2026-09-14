# Profile form

Finish the profile-editing form shown in `reference.svg`. Retain the heading `프로필 수정`, the
two fields `이름` and `이메일`, and the blue `저장` action (brand color #2156df). The form should
work in a 320–420 px wide viewport and when the root font size is doubled. Scrolling is allowed.
Do not change the reference design file or generate a new application.

The injected `window.profileApi.save({name, email})` returns a promise; use it without replacing it.
The fallback API in app.js is for local preview only. A nonblank name and syntactically valid email
are required. Invalid input must produce visible feedback associated with the invalid field and
must not call save. On submission expose pending feedback and prevent duplicate submissions.
On a server failure retain both fields, provide visible assistive-technology-readable feedback,
and allow another explicit submit. Success should show confirmation.

Provide accessible field names matching the visible labels, a keyboard-operable save button, and
focusable controls in name/email/save order. Our project's touch target standard is at least 44 px
in both dimensions for interactive controls. At narrow width and large text there must be no
horizontal document overflow; controls must remain reachable with normal scrolling.

The source is plain HTML and browser JavaScript. You can run `bun test` for the public smoke check.
Chromium and a Playwright package are available in the common test environment (see prompt). If
you use a browser check, report its actual scope. Do not claim a screen-reader or device audit.
