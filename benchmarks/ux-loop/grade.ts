import assert from "node:assert/strict";
import { mkdirSync, readFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { pathToFileURL } from "node:url";

export type Check = { id: string; pass: boolean; detail: string };
type Case = [string, () => unknown | Promise<unknown>];
async function evaluate(cases: Case[]): Promise<Check[]> {
  const results: Check[] = [];
  for (const [id, run] of cases) {
    try { await run(); results.push({ id, pass: true, detail: "Assertions satisfied" }); }
    catch (error) { results.push({ id, pass: false, detail: String(error).slice(0, 1200) }); }
  }
  assert.equal(results.length, 10);
  return results;
}
const deferred = () => {
  let resolve!: (value: any) => void;
  const promise = new Promise<any>(yes => { resolve = yes; });
  return { promise, resolve };
};
const unknownError = () => Object.assign(new Error("Outcome unavailable"), { code: "UNKNOWN_OUTCOME" });

export async function gradeEditor(root: string) {
  const { createEditor } = await import(pathToFileURL(join(root, "editor.js")).href);
  return evaluate([
    ["trimmed_payload_preserved_draft", async () => {
      let payload; const editor = createEditor({ save: async p => { payload = p; return p; } });
      editor.setName("  Grace  "); await editor.submit();
      assert.deepEqual(payload, { displayName: "Grace" });
      assert.equal(editor.getState().name, "  Grace  "); assert.equal(editor.getState().status, "saved");
    }],
    ["observable_pending_state", async () => {
      const wait = deferred(); const editor = createEditor({ save: () => wait.promise });
      const submit = editor.submit(); assert.equal(editor.getState().status, "saving");
      wait.resolve({ displayName: "Ada" }); await submit;
    }],
    ["duplicate_submit_suppression", async () => {
      const wait = deferred(); let calls = 0;
      const editor = createEditor({ save: () => { calls++; return wait.promise; } });
      const a = editor.submit(); const b = editor.submit(); wait.resolve({ displayName: "Ada" });
      await Promise.all([a, b]); assert.equal(calls, 1);
    }],
    ["failure_preserves_input_and_explicit_retry", async () => {
      let calls = 0;
      const editor = createEditor({ save: async p => { if (++calls === 1) throw new Error("Offline"); return p; } });
      editor.setName("New draft"); await editor.submit();
      assert.equal(editor.getState().name, "New draft"); assert.equal(editor.getState().status, "error");
      assert(editor.getState().message.trim()); await editor.submit();
      assert.equal(calls, 2); assert.equal(editor.getState().status, "saved");
    }],
    ["late_response_does_not_overwrite_edits", async () => {
      const wait = deferred(); const editor = createEditor({ save: () => wait.promise });
      const request = editor.submit(); editor.setName("Edited while saving");
      wait.resolve({ displayName: "Ada" }); await request;
      assert.equal(editor.getState().name, "Edited while saving");
    }],
    ["blank_input_stops_request", async () => {
      let calls = 0; const editor = createEditor({ save: async p => { calls++; return p; } });
      editor.setName(" \t "); await editor.submit();
      assert.equal(calls, 0); assert.equal(editor.getState().status, "error");
      assert(editor.getState().message.trim());
    }],
    ["snapshot_cannot_mutate_store", () => {
      const editor = createEditor({ save: async p => p }); const snapshot = editor.getState();
      try { snapshot.name = "Outside mutation"; } catch { /* frozen snapshots are valid */ }
      assert.equal(editor.getState().name, "Ada");
    }],
    ["unknown_outcome_does_not_resubmit", async () => {
      let saves = 0; const editor = createEditor({ save: async () => { saves++; throw unknownError(); } });
      await editor.submit(); assert.equal(editor.getState().status, "unknown");
      await editor.submit(); assert.equal(saves, 1);
    }],
    ["reconcile_uses_lookup_and_blocks_submit", async () => {
      const wait = deferred(); let saves = 0; let lookups = 0;
      const editor = createEditor({ save: async () => { saves++; throw unknownError(); },
        lookup: () => { lookups++; return wait.promise; } });
      await editor.submit(); const lookup = editor.reconcile();
      assert.equal(editor.getState().status, "checking"); await editor.submit();
      wait.resolve({ status: "saved" }); await lookup;
      assert.equal(saves, 1); assert.equal(lookups, 1); assert.equal(editor.getState().status, "saved");
    }],
    ["lookup_failure_and_pending_remain_unknown", async () => {
      let lookups = 0; const editor = createEditor({ save: async () => { throw unknownError(); },
        lookup: async () => { if (++lookups === 1) throw new Error("Offline"); return { status: "pending" }; } });
      editor.setName("Keep me"); await editor.submit(); await editor.reconcile();
      assert.equal(editor.getState().status, "unknown"); await editor.reconcile();
      assert.equal(lookups, 2); assert.equal(editor.getState().status, "unknown");
      assert.equal(editor.getState().name, "Keep me");
    }],
  ]);
}

export async function gradeGates(root: string) {
  const { summarize } = await import(pathToFileURL(join(root, "gates.js")).href);
  const context = { sourceHash: "commit-current", fixtureHash: "fixture-current" };
  const required = [{ id: "core", kind: "logic" }, { id: "ios", kind: "native" }];
  const result = (id: string, kind = "logic", status = "PASS") => ({ id, status, evidence: [
    { path: `${id}.json`, kind, ...context },
  ] });
  const nonPass = (output: any) => {
    assert.notEqual(output.overall, "PASS");
    assert(output.gates.some(g => g.status !== "PASS" && typeof g.reason === "string" && g.reason.trim()));
  };
  return evaluate([
    ["current_matching_evidence_passes", () => assert.equal(summarize(required,
      [result("core"), result("ios", "native")], context).overall, "PASS")],
    ["missing_required_gate_is_not_run", () => {
      const out = summarize(required, [result("core")], context);
      assert.equal(out.overall, "NOT_RUN"); assert.equal(out.gates.find(g => g.id === "ios").status, "NOT_RUN");
    }],
    ["source_and_fixture_identity_checked", () => {
      for (const key of ["sourceHash", "fixtureHash"]) {
        const item = result("core"); item.evidence[0][key] = "old";
        nonPass(summarize([required[0]], [item], context));
      }
    }],
    ["synthetic_evidence_cannot_pass_runtime", () => {
      for (const kind of ["browser", "native"]) nonPass(summarize([{ id: "screen", kind }], [result("screen", "logic")], context));
    }],
    ["empty_artifacts_do_not_pass", () => {
      const item = result("core"); item.evidence[0].path = " ";
      nonPass(summarize([required[0]], [item], context));
      nonPass(summarize([required[0]], [{ ...item, evidence: [] }], context));
    }],
    ["duplicate_and_invalid_status_fail", () => {
      assert.equal(summarize([required[0]], [result("core"), result("core")], context).overall, "FAIL");
      assert.equal(summarize([required[0]], [result("core", "logic", "looks-good")], context).overall, "FAIL");
    }],
    ["undefined_scope_requires_review", () => {
      assert.equal(summarize([], [], context).overall, "REVIEW");
      assert.equal(summarize([required[0]], [result("core", "logic", "NOT_APPLICABLE")], context).overall, "REVIEW");
    }],
    ["nonpass_status_and_precedence_preserved", () => {
      for (const status of ["FAIL", "REVIEW", "UNSUPPORTED", "NOT_RUN"]) {
        const out = summarize(required, [result("core"), result("ios", "native", status)], context);
        assert.equal(out.overall, status); assert.equal(out.gates[1].status, status);
      }
      assert.equal(summarize(required, [result("core", "logic", "UNSUPPORTED"), result("ios", "native", "FAIL")], context).overall, "FAIL");
    }],
    ["required_order_and_extra_result_handling", () => {
      const out = summarize(required, [result("extra"), result("core")], context);
      assert.deepEqual(out.gates.map(g => g.id), ["core", "ios"]); assert.notEqual(out.overall, "PASS");
      assert.deepEqual(summarize(required, [result("ios", "native"), result("core")], context).gates.map(g => g.id), ["core", "ios"]);
    }],
    ["input_data_remains_unchanged", () => {
      const inputs = structuredClone([required, [result("core"), result("ios", "native")], context]);
      const before = JSON.stringify(inputs); summarize(...inputs); assert.equal(JSON.stringify(inputs), before);
    }],
  ]);
}

export async function gradeForm(root: string, evidenceDir: string) {
  const { chromium } = await import("playwright");
  const server = Bun.serve({ hostname: "127.0.0.1", port: 0, fetch(request) {
    const name = new URL(request.url).pathname;
    if (name === "/") return new Response(readFileSync(join(root, "index.html")), { headers: { "content-type": "text/html" } });
    if (name === "/app.js") return new Response(readFileSync(join(root, "app.js")), { headers: { "content-type": "text/javascript" } });
    return new Response("Not found", { status: 404 });
  } });
  const browser = await chromium.launch({ headless: true });
  mkdirSync(evidenceDir, { recursive: true });
  const each = (name: string, fn: (page: any) => Promise<void>): Case => [name, async () => {
    const page = await browser.newPage({ viewport: { width: 390, height: 560 } });
    page.setDefaultTimeout(2500);
    await page.addInitScript(() => {
      const w = window as any;
      w.calls = []; w.mode = "success";
      w.profileApi = { save: async payload => {
        w.calls.push(payload);
        if (w.mode === "error") throw new Error("Service unavailable");
        if (w.mode === "pending") return new Promise(resolve => { w.completeSave = resolve; });
        return payload;
      } };
    });
    try {
      await page.goto(`http://127.0.0.1:${server.port}`);
      await fn(page);
    } finally {
      await page.screenshot({ path: join(evidenceDir, `${name}.png`), fullPage: true }).catch(() => {});
      await page.close();
    }
  }];
  const fill = async (page: any, email = "ada@example.test", name = "Ada") => {
    await page.locator("#name").fill(name); await page.locator("#email").fill(email);
  };
  const clickSave = (page: any) => page.getByRole("button", { name: "저장", exact: true }).click();
  try {
    return await evaluate([
      each("accessible_field_names", async page => {
        assert.equal(await page.getByRole("textbox", { name: "이름", exact: true }).count(), 1);
        assert.equal(await page.getByRole("textbox", { name: "이메일", exact: true }).count(), 1);
      }),
      each("visible_submit_sends_values", async page => {
        await fill(page); await clickSave(page); await page.waitForFunction(() => (window as any).calls.length > 0);
        assert.deepEqual(await page.evaluate(() => (window as any).calls), [{ name: "Ada", email: "ada@example.test" }]);
      }),
      each("pending_action_disabled", async page => {
        await page.evaluate(() => { (window as any).mode = "pending"; }); await fill(page); await clickSave(page);
        assert(await page.locator('button[type="submit"]').isDisabled());
        assert.equal(await page.evaluate(() => (window as any).calls.length), 1);
      }),
      each("server_error_retains_input_and_live_feedback", async page => {
        await page.evaluate(() => { (window as any).mode = "error"; }); await fill(page); await clickSave(page);
        await page.waitForFunction(() => [...document.querySelectorAll('[role="alert"], [aria-live]')].some(n => n.textContent?.trim()));
        assert.equal(await page.locator("#name").inputValue(), "Ada");
        assert.equal(await page.locator("#email").inputValue(), "ada@example.test");
        assert(!(await page.locator('button[type="submit"]').isDisabled()));
      }),
      each("invalid_email_has_associated_feedback", async page => {
        await fill(page, "not-an-email"); await clickSave(page);
        assert.equal(await page.evaluate(() => (window as any).calls.length), 0);
        const feedback = await page.locator("#email").evaluate(input => {
          const ids = (input.getAttribute("aria-describedby") ?? input.getAttribute("aria-errormessage") ?? "").split(/\s+/);
          return ids.some(id => { const node = document.getElementById(id); return node && node.textContent?.trim() && node.getBoundingClientRect().height > 0; });
        });
        assert(feedback, "Invalid email needs associated visible feedback");
      }),
      each("blank_name_stops_submission", async page => {
        await fill(page, "ada@example.test", "   "); await clickSave(page);
        assert.equal(await page.evaluate(() => (window as any).calls.length), 0);
      }),
      each("narrow_layout_has_no_horizontal_overflow", async page => {
        await page.setViewportSize({ width: 320, height: 480 });
        assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), "Horizontal overflow at 320px");
      }),
      each("large_text_controls_reachable_and_sized", async page => {
        await page.setViewportSize({ width: 320, height: 480 });
        await page.evaluate(() => { document.documentElement.style.fontSize = "32px"; });
        assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
        for (const selector of ["#name", "#email", 'button[type="submit"]']) {
          const control = page.locator(selector); await control.scrollIntoViewIfNeeded();
          const box = await control.boundingBox(); assert(box && box.width >= 44 && box.height >= 44, selector);
        }
        await fill(page); await clickSave(page); assert.equal(await page.evaluate(() => (window as any).calls.length), 1);
      }),
      each("keyboard_focus_order_and_activation", async page => {
        await page.keyboard.press("Tab"); assert.equal(await page.evaluate(() => document.activeElement?.id), "name");
        await page.keyboard.type("Ada"); await page.keyboard.press("Tab");
        assert.equal(await page.evaluate(() => document.activeElement?.id), "email");
        await page.keyboard.type("ada@example.test"); await page.keyboard.press("Tab");
        assert.equal(await page.evaluate(() => document.activeElement?.tagName), "BUTTON");
        await page.keyboard.press("Enter"); await page.waitForFunction(() => (window as any).calls.length > 0);
        assert.equal(await page.evaluate(() => (window as any).calls.length), 1);
      }),
      each("heading_and_brand_intent_preserved", async page => {
        assert.equal(await page.getByRole("heading", { name: "프로필 수정", exact: true }).count(), 1);
        const color = await page.locator('button[type="submit"]').evaluate(button => getComputedStyle(button).backgroundColor);
        assert.equal(color, "rgb(33, 86, 223)");
      }),
    ]);
  } finally { await browser.close(); server.stop(true); }
}

export async function grade(task: string, root: string, evidenceDir: string): Promise<Check[]> {
  // Browser startup failures are evaluator infrastructure errors, not product failures.
  if (task === "form") return await gradeForm(root, evidenceDir);
  try {
    if (task === "editor") return await gradeEditor(root);
    if (task === "gates") return await gradeGates(root);
    throw new Error(`Unknown task: ${task}`);
  } catch (error) {
    return Array.from({ length: 10 }, (_, index) => ({ id: `unavailable-${index + 1}`, pass: false, detail: String(error) }));
  }
}

if (import.meta.main) {
  const [, , task, root, out] = process.argv;
  assert(task && root && out, "Usage: bun grade.ts task project evidenceDir");
  console.log(JSON.stringify(await grade(task, resolve(root), resolve(out))));
}
