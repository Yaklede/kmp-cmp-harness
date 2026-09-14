export function createEditor(api, initialName = "Ada") {
  const state = { name: initialName, status: "idle", message: "" };
  const busy = () => ["saving", "checking"].includes(state.status);
  return {
    getState: () => ({ ...state }),
    setName(name) { state.name = name; },
    async submit() {
      if (busy() || state.status === "unknown") return;
      const displayName = state.name.trim();
      if (!displayName) { state.status = "error"; state.message = "Enter a name"; return; }
      state.status = "saving"; state.message = "Saving";
      try { await api.save({ displayName }); state.status = "saved"; state.message = "Saved"; }
      catch (error) { state.status = error.code === "UNKNOWN_OUTCOME" ? "unknown" : "error"; state.message = "Check or retry"; }
    },
    async reconcile() {
      if (state.status !== "unknown") return;
      state.status = "checking"; state.message = "Checking";
      try { state.status = (await api.lookup()).status === "saved" ? "saved" : "unknown"; }
      catch { state.status = "unknown"; }
      state.message = state.status === "saved" ? "Saved" : "Check again";
    },
  };
}
