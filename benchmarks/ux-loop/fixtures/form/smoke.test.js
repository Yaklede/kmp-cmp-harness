import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
test("HTML keeps the profile form entry point", () => {
  const html = readFileSync(new URL("./index.html", import.meta.url), "utf8");
  expect(html).toContain("프로필 수정");
  expect(html).toContain("app.js");
});
