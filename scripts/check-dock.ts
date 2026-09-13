import { dockRoot, inspectBundle } from "./dock-package";

const bundle = inspectBundle();
console.log(`PASS: ${bundle.manifest.files.length} instruction/license mappings; no runtime or tasks. ${dockRoot}`);
