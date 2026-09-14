import { expect, test } from "bun:test";
import { summarize } from "./gates.js";

test("valid logic result", () => {
  expect(summarize([{ id: "core", kind: "logic" }], [{ id: "core", status: "PASS", evidence: [
    { path: "core.json", kind: "logic", sourceHash: "source-a", fixtureHash: "fixture-a" },
  ] }], { sourceHash: "source-a", fixtureHash: "fixture-a" }).overall).toBe("PASS");
});
