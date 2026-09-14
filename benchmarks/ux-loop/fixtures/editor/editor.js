export function createEditor(api, initialName = "Ada") {
  let state = { name: initialName, status: "idle", message: "" };
  return {
    getState() { return state; },
    setName(name) { state.name = name; },
    async submit() {
      state.status = "saving";
      try {
        const result = await api.save({ displayName: state.name });
        state = { name: result.displayName, status: "saved", message: "Saved" };
      } catch {
        state.status = "error";
        state.message = "Please try again";
      }
    },
    async reconcile() { return this.submit(); },
  };
}
