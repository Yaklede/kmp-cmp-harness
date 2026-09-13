import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { dockRoot, inspectBundle, instructionTargets, releaseVersion, sha256, upstreamCommit } from "./dock-package";

const source = process.argv[2];
assert(source, "Usage: bun run test:opendock /path/to/pinned/OpenDock (see docs/publishing.md)");
const upstream = resolve(source);
assert.equal(execFileSync("git", ["-C", upstream, "rev-parse", "HEAD"], { encoding: "utf8" }).trim(), upstreamCommit,
  "Use the reviewed upstream commit; update this pin deliberately with the compatibility checks");
const cli = join(upstream, "packages/cli");
const load = (path: string) => import(pathToFileURL(join(cli, "src", path)).href);
const { DockRef, parseManifestFile, manifestForRef } = await load("core/domain/manifest.ts");
const { createDeployArchive, readDeployReadme } = await load("deploy-package.ts");
const { validateManifestTaskCommands } = await load("core/runtime/task-command-validation.ts");
const { DockInstaller } = await load("core/app/dock-installer.ts");
const { OpenDockStateStore } = await load("core/domain/state-store.ts");
const tar = await import(pathToFileURL(createRequire(join(cli, "package.json")).resolve("tar")).href);
const bundle = inspectBundle();
const scratch = mkdtempSync(join(tmpdir(), "ux-loop-opendock-"));
let lifecycles = 0;

try {
  for (const platform of ["macos", "windows", "linux"]) {
    const ref = DockRef.parse(`fixture-owner/ux-loop@${releaseVersion}`);
    const manifest = manifestForRef(parseManifestFile(join(dockRoot, "dock.yml")), ref);
    validateManifestTaskCommands(manifest, platform);
    assert.equal(readDeployReadme(dockRoot, manifest), readFileSync(join(dockRoot, "DOCK.md"), "utf8"));
    const archive = await createDeployArchive(dockRoot, manifest, releaseVersion, platform,
      readFileSync(join(dockRoot, "dock.yml"), "utf8"));
    const bytes = Buffer.from(archive.data_base64, "base64");
    assert.equal(sha256(bytes), archive.checksum);
    const archivePath = join(scratch, `${platform}.tgz`);
    writeFileSync(archivePath, bytes);
    const entries: string[] = [];
    await tar.t({ file: archivePath, onReadEntry: (entry: any) => {
      assert.equal(entry.type, "File"); entries.push(entry.path);
    } });
    // The Hub readme is sent separately by deploy. Only manifest + mapped sources go in the archive.
    assert.deepEqual(entries.sort(), ["dock.yml", ...new Set(bundle.manifest.files.map(f => f.from))].sort());
    const extracted = join(scratch, platform); mkdirSync(extracted);
    await tar.x({ file: archivePath, cwd: extracted });
    for (const file of bundle.manifest.files) {
      assert.equal(readFileSync(join(extracted, file.from), "utf8"), readFileSync(join(dockRoot, file.from), "utf8"));
    }

    const projects: Record<string, Record<string, string>> = {
      empty: {},
      kmp: { "mobile/settings.gradle.kts": 'rootProject.name = "existing-mobile"\n', "mobile/src/Marker.kt": "// untouched source\n" },
      web: { "package.json": '{"name":"existing-web","private":true}\n', "src/app.ts": "export const app = 1;\n" },
      python: { "pyproject.toml": '[project]\nname = "existing-service"\n', "service.py": "print('unchanged')\n" },
    };
    for (const [kind, files] of Object.entries(projects)) {
      const projectDir = join(scratch, `${platform}-${kind}`); mkdirSync(projectDir);
      // Include CRLF, no trailing newline, and user content already in the namespaced rule file.
      const originals: Record<string, string> = kind === "empty" ? {} : {
        "AGENTS.md": "# Team rules\r\nKeep existing architecture.\r\n",
        "CLAUDE.md": "Team Claude instructions without trailing newline",
        "GEMINI.md": "Team Gemini instructions\n\n",
        ".harness/ux-loop/project.md": "Local project notes outside the dock block.\n",
        ...files,
      };
      for (const [path, content] of Object.entries(originals)) {
        mkdirSync(dirname(join(projectDir, path)), { recursive: true });
        writeFileSync(join(projectDir, path), content);
      }
      // Exercise the real installer against the actual archive, with only remote resolution substituted.
      // This does not test registry login, signatures, review, download, or native OS behavior.
      const resolver = (root: string) => (requested: any, selectedPlatform: string) => ({
        manifest: manifestForRef(parseManifestFile(join(root, "dock.yml")), requested),
        version: requested.requested(), platform: selectedPlatform, root,
        checksum: archive.checksum, signature: "local-fixture-only",
      });
      const installer = new DockInstaller();
      const options = { dockRef: ref, projectDir, runTasks: true, platform, resolve: resolver(extracted) };
      const result = await installer.install(options);
      assert.equal(result.filesReviewRequired, 0); assert.deepEqual(result.steps, []);
      const record = new OpenDockStateStore(projectDir).readLock().docks[0];
      assert.equal(record.files.length, bundle.manifest.files.length);
      assert.deepEqual(record.runtimes, []); assert.deepEqual(record.tools, []); assert.deepEqual(record.dependencies, []);
      for (const mapping of bundle.manifest.files) {
        const installed = readFileSync(join(projectDir, mapping.to), "utf8");
        assert(installed.includes(readFileSync(join(extracted, mapping.from), "utf8").trimEnd()));
        assert.equal(installed.match(/<!-- OPENDOCK:START/g)?.length, 1);
        if (originals[mapping.to]) assert(installed.startsWith(originals[mapping.to]));
      }
      // Reinstall must be idempotent; a new version must replace the same block, not append another.
      const before = instructionTargets.map(p => readFileSync(join(projectDir, p), "utf8"));
      await installer.install(options);
      assert.deepEqual(instructionTargets.map(p => readFileSync(join(projectDir, p), "utf8")), before);
      const updatedRoot = join(scratch, `${platform}-${kind}-v2`);
      cpSync(extracted, updatedRoot, { recursive: true });
      const updatedRule = join(updatedRoot, "files/constraints/project.md");
      writeFileSync(updatedRule, readFileSync(updatedRule, "utf8") + "\nFixture version update.\n");
      const updateOptions = { ...options, phase: "update", dockRef: DockRef.parse("fixture-owner/ux-loop@0.1.1"), resolve: resolver(updatedRoot) };
      await installer.install(updateOptions);
      const targetRule = join(projectDir, ".harness/ux-loop/project.md");
      const approved = readFileSync(targetRule, "utf8");
      assert(approved.includes("Fixture version update."));
      assert.equal(approved.match(/<!-- OPENDOCK:START/g)?.length, 1);

      // A local edit inside an owned block must be protected on both update and uninstall.
      const edited = approved.replace("Fixture version update.", "User-edited rule.");
      writeFileSync(targetRule, edited);
      await assert.rejects(() => installer.install(updateOptions), /checksum mismatch/);
      assert.throws(() => installer.uninstall({ projectDir, dockId: ref.id() }), /checksum mismatch/);
      assert.equal(readFileSync(targetRule, "utf8"), edited);
      writeFileSync(targetRule, approved);

      installer.uninstall({ projectDir, dockId: ref.id() });
      for (const [path, content] of Object.entries(originals)) assert.equal(readFileSync(join(projectDir, path), "utf8"), content);
      for (const mapping of bundle.manifest.files) if (!(mapping.to in originals)) assert(!existsSync(join(projectDir, mapping.to)));
      assert.deepEqual(new OpenDockStateStore(projectDir).readLock().docks, []);
      assert(!existsSync(join(projectDir, ".opendock/bin")));
      assert(!existsSync(join(projectDir, ".opendock/tools")));
      lifecycles++;
    }
    console.log(`PASS: official ${platform} packaging + empty/KMP/web/Python project lifecycles`);
  }
  console.log(`PASS: ${lifecycles} local-resolver lifecycle cases against OpenDock ${upstreamCommit}. No Hub submission.`);
} finally {
  rmSync(scratch, { recursive: true, force: true });
}
