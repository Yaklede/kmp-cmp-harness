import { afterEach, expect, test } from "bun:test";
import { cpSync, mkdtempSync, readFileSync, rmSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import YAML from "yaml";
import { dockRoot, inspectBundle, prepareRelease } from "../scripts/dock-package";

const roots: string[] = [];
afterEach(() => { for (const root of roots.splice(0)) rmSync(root, { recursive: true, force: true }); });
function fixture() {
  const root = mkdtempSync(join(tmpdir(), "ux-loop-package-"));
  roots.push(root); cpSync(dockRoot, join(root, "dock"), { recursive: true });
  return join(root, "dock");
}
function editManifest(root: string, change: (m: any) => void) {
  const path = join(root, "dock.yml");
  const manifest = YAML.parse(readFileSync(path, "utf8"));
  change(manifest); writeFileSync(path, YAML.stringify(manifest));
}

test("ships only portable instructions and a license with valid installed references", () => {
  const bundle = inspectBundle();
  expect(bundle.manifest.files).toHaveLength(11);
  expect(readFileSync(join(dockRoot, "files/constraints/LICENSE.txt"), "utf8"))
    .toBe(readFileSync(join(import.meta.dir, "../LICENSE"), "utf8"));
});
for (const key of ["requires", "tools", "dependencies", "permissions", "workdir", "install", "update", "doctor"]) {
  test(`rejects ${key} even when empty`, () => {
    const root = fixture(); editManifest(root, m => { m[key] = []; });
    expect(() => inspectBundle(root)).toThrow("Non-passive manifest field");
  });
}
test("rejects directory mappings that could pull in samples or build outputs", () => {
  const root = fixture(); editManifest(root, m => { m.files[0].from = "files"; });
  expect(() => inspectBundle(root)).toThrow("Only constraint documents");
});
test("rejects project build file destinations", () => {
  const root = fixture(); editManifest(root, m => { m.files[0].to = "build.gradle.kts"; });
  expect(() => inspectBundle(root)).toThrow("Unexpected install target");
});
test("rejects unlisted release content and symbolic links", () => {
  const root = fixture(); writeFileSync(join(root, "app.apk"), "sample");
  expect(() => inspectBundle(root)).toThrow("Unlisted file");
  rmSync(join(root, "app.apk")); symlinkSync("DOCK.md", join(root, "alias.md"));
  expect(() => inspectBundle(root)).toThrow("symlink");
});
test("rejects broken references in the installed directory layout", () => {
  const root = fixture();
  writeFileSync(join(root, "files/constraints/README.md"), "[missing](missing.md)\n");
  expect(() => inspectBundle(root)).toThrow("Broken installed link");
});
test("prepares a self-contained manifest directory without repository tooling", () => {
  const root = fixture(); const output = join(root, "../release");
  const first = prepareRelease(root, output);
  expect(inspectBundle(output).paths).toEqual(inspectBundle(root).paths);
  expect(first.every(f => !/examples|scripts|node_modules|gradle/.test(f.path))).toBe(true);
  expect(JSON.parse(readFileSync(`${output}.json`, "utf8")).status).toBe("PREPARED_NOT_PUBLISHED");
  expect(prepareRelease(root, join(root, "../release-again"))).toEqual(first);
  expect(() => prepareRelease(root, output)).toThrow();
});
