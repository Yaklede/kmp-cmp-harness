import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { copyFileSync, lstatSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, posix, resolve } from "node:path";
import YAML from "yaml";

export const dockRoot = resolve(import.meta.dir, "../dock");
export const releaseVersion = "0.1.0";
export const upstreamCommit = "8c41853fc6af8130d06fc9be713d462104438b87";
export const instructionTargets = ["AGENTS.md", "CLAUDE.md", "GEMINI.md"];
const prefix = ".harness/ux-loop/";
const requiredRules = ["README.md", "project.md", "design-ux.md", "architecture.md",
  "verification.md", "repair-delivery.md", "kmp-cmp.md", "LICENSE.txt"];
const passiveFields = new Set(["opendock", "name", "summary", "readme", "tags", "files"]);

export type Mapping = { from: string; to: string };
export type Bundle = { manifest: { files: Mapping[]; readme: string }; paths: string[] };
export const sha256 = (bytes: Uint8Array | string) => createHash("sha256").update(bytes).digest("hex");

function safeRelative(path: unknown): asserts path is string {
  assert(typeof path === "string" && /^[A-Za-z0-9_./-]+$/.test(path), "Expected a plain relative path");
  assert(!path.startsWith("/") && !path.split("/").some(p => p === ".." || p === "." || !p), "Unsafe path");
}

function regularFile(root: string, path: string) {
  safeRelative(path);
  let current = root;
  const parts = path.split("/");
  for (const [index, part] of parts.entries()) {
    current = join(current, part);
    const stat = lstatSync(current);
    assert(!stat.isSymbolicLink(), `Symlinks are not package content: ${path}`);
    if (index === parts.length - 1) {
      assert(stat.isFile() && stat.nlink === 1, `Expected a regular, unlinked file: ${path}`);
      assert(stat.size > 0 && stat.size <= 65536, `Invalid document size: ${path}`);
    } else assert(stat.isDirectory(), `Expected directory: ${current}`);
  }
  const bytes = readFileSync(current);
  assert(Buffer.from(bytes.toString("utf8")).equals(bytes) && !bytes.includes(0), `Expected UTF-8 text: ${path}`);
  return bytes.toString("utf8");
}

function listFiles(root: string, relative = ""): string[] {
  return readdirSync(join(root, relative), { withFileTypes: true }).flatMap(entry => {
    const path = posix.join(relative, entry.name);
    assert(!entry.isSymbolicLink(), `Unexpected symlink: ${path}`);
    return entry.isDirectory() ? listFiles(root, path) : [path];
  }).sort();
}

// This validates our stricter instructions-only publishing policy. OpenDock's actual
// schema, packager, and file lifecycle are tested separately against pinned upstream code.
export function inspectBundle(root = dockRoot): Bundle {
  const manifest = YAML.parse(regularFile(root, "dock.yml"));
  assert(manifest && typeof manifest === "object" && !Array.isArray(manifest), "Invalid manifest");
  for (const key of Object.keys(manifest)) assert(passiveFields.has(key), `Non-passive manifest field: ${key}`);
  assert.equal(manifest.opendock, 1);
  for (const field of ["name", "summary", "readme"]) assert(typeof manifest[field] === "string" && manifest[field].trim());
  assert.equal(manifest.readme, "DOCK.md");
  assert(Array.isArray(manifest.tags) && manifest.tags.length > 0 && manifest.tags.length <= 12);
  assert(manifest.tags.every((t: unknown) => typeof t === "string" && /^[a-z0-9][a-z0-9-]{0,31}$/.test(t)));
  assert.equal(new Set(manifest.tags).size, manifest.tags.length);
  assert(Array.isArray(manifest.files) && manifest.files.length > 0);
  const expectedTargets = new Set([...instructionTargets, ...requiredRules.map(p => prefix + p)]);
  const destinations = new Set<string>();
  const sources = new Set<string>(["dock.yml", manifest.readme]);
  const contentByTarget = new Map<string, string>();
  for (const file of manifest.files) {
    assert(file && Object.keys(file).sort().join(",") === "from,to", "Only explicit file mappings are allowed");
    safeRelative(file.from); safeRelative(file.to);
    assert(file.from.startsWith("files/") && (file.from.endsWith(".md") || file.from === "files/constraints/LICENSE.txt"), "Only constraint documents may ship");
    assert(expectedTargets.has(file.to), `Unexpected install target: ${file.to}`);
    assert(!destinations.has(file.to), `Duplicate destination: ${file.to}`);
    const text = regularFile(root, file.from);
    assert(!/<!-- OPENDOCK:(START|END)/.test(text), "OpenDock generates its own markers");
    destinations.add(file.to); sources.add(file.from); contentByTarget.set(file.to, text);
  }
  assert.deepEqual([...destinations].sort(), [...expectedTargets].sort(), "Missing required entry point or rule");
  for (const target of instructionTargets) {
    assert.equal(contentByTarget.get(target), contentByTarget.get("AGENTS.md"));
    assert(contentByTarget.get(target)!.includes(`${prefix}README.md`));
  }
  for (const [target, text] of contentByTarget) {
    // Resolve document links in the installed layout, not in this repository's layout.
    for (const match of text.matchAll(/\[[^\]]+\]\(([^)]+)\)/g)) {
      const link = match[1];
      if (/^https?:\/\//.test(link) || link.startsWith("#")) continue;
      const resolved = posix.normalize(posix.join(posix.dirname(target), link.split("#")[0]));
      assert(destinations.has(resolved), `Broken installed link: ${target} -> ${link}`);
    }
  }
  regularFile(root, manifest.readme);
  const paths = [...sources].sort();
  assert.deepEqual(listFiles(root), paths, "Unlisted file in dock directory; keep tooling/examples outside it");
  return { manifest, paths };
}

export function prepareRelease(root: string, output: string) {
  const bundle = inspectBundle(root);
  // Refuse to erase an existing directory; callers choose a new output or remove only
  // their previous generated release explicitly.
  mkdirSync(output, { recursive: false });
  for (const path of bundle.paths) {
    const target = join(output, path);
    mkdirSync(dirname(target), { recursive: true });
    copyFileSync(join(root, path), target);
  }
  inspectBundle(output);
  const inventory = bundle.paths.map(path => ({ path, sha256: sha256(readFileSync(join(output, path))) }));
  writeFileSync(`${output}.json`, JSON.stringify({
    version: releaseVersion, format: "opendock/v1", installedFiles: bundle.manifest.files.length,
    platforms: ["macos", "windows", "linux"], inventory,
    status: "PREPARED_NOT_PUBLISHED",
  }, null, 2) + "\n");
  return inventory;
}
