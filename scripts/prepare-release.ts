import { mkdirSync } from "node:fs";
import { resolve } from "node:path";
import { dockRoot, prepareRelease } from "./dock-package";

const output = resolve(process.argv[2] ?? "dist/opendock");
if (process.argv[2] === undefined) mkdirSync("dist", { recursive: true });
const inventory = prepareRelease(dockRoot, output);
console.log(`Prepared ${inventory.length} files in ${output}`);
console.log(`SHA-256 inventory: ${output}.json. No Hub submission was made.`);
