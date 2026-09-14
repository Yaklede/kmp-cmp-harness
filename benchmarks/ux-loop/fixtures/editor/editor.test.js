import { expect, test } from "bun:test";
import { createEditor } from "./editor.js";

test("initial name and successful save", async () => {
  const editor = createEditor({ save: async p => p });
  expect(editor.getState().name).toBe("Ada");
  editor.setName("Grace");
  await editor.submit();
  expect(editor.getState().status).toBe("saved");
});
