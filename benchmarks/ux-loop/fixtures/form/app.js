window.profileApi ??= { save: async payload => payload };
const form = document.querySelector("form");
form.addEventListener("submit", async event => {
  event.preventDefault();
  const payload = { name: form.querySelector("#name").value, email: form.querySelector("#email").value };
  form.reset();
  await window.profileApi.save(payload);
  document.querySelector("#feedback").textContent = "저장했습니다";
});
