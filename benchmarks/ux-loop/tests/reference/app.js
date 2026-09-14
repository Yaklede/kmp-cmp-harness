window.profileApi ??= { save: async payload => payload };
const form = document.querySelector("form");
const name = document.querySelector("#name");
const email = document.querySelector("#email");
const feedback = document.querySelector("#feedback");
const button = document.querySelector("button");
form.addEventListener("submit", async event => {
  event.preventDefault();
  if (button.disabled) return;
  if (!name.value.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value)) {
    feedback.textContent = "이름과 이메일을 확인하세요"; return;
  }
  button.disabled = true; feedback.textContent = "저장 중";
  try { await window.profileApi.save({ name: name.value, email: email.value }); feedback.textContent = "저장했습니다"; }
  catch { feedback.textContent = "저장하지 못했습니다. 다시 시도하세요"; }
  finally { button.disabled = false; }
});
