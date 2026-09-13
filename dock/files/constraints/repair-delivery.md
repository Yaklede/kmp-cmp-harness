# Repair loop and delivery

- Review failures against the original contract and unchanged acceptance criteria. Fix the cause;
  do not remove a failing assertion, weaken a threshold, mark a required check inapplicable, or
  approve a newly generated baseline merely to obtain PASS. Legitimate criterion changes need
  a recorded product or platform reason and the applicable decision authority.
- Distinguish implementation, environment, tool/adapter, and unresolved product failures. Repair
  only the responsible layer. When an unavailable device or tool blocks a check, record the exact
  limitation and continue independent authorized work.
- Bound automated retries. Unless the task specifies another budget, allow at most three repair
  attempts per failure; stop that loop after the same failure persists across two attempts without
  new evidence. Report the cause and next actionable requirement rather than retrying indefinitely.
- Separate building from judging acceptance, even when one agent performs both roles sequentially.
  The implementation step cannot grant itself product approval. This rule does not require another
  agent or a new approval for work already authorized by the user.
- Coordinate shared state when tools or agents run concurrently: one writer for the same checkout
  and one active input runner for the same device/session. Use isolated worktrees or serialize
  conflicting operations. Never overwrite unrelated user changes.
- After a fix, rerun affected checks and the project's required checks. Broaden testing when new
  failures or unresolved risks justify it; do not repeatedly run unchanged successful checks.
- Follow the target project's commit, branch, review, and delivery conventions. Commit coherent
  verified units when requested or established by the project. If the user authorized merge and
  push, perform them after validation and verify the remote result; a local commit is not that
  delivery. Do not choose `main`, force push, publish, or contact others solely because this dock
  is installed.

The final report must say what changed and why, checks and evidence with their scope, remaining
failures/decisions or unavailable checks, and the actual delivery status. Distinguish a prepared
package from a published release and instruction coverage from implemented runtime capabilities.
