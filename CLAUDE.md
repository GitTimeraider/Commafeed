# CLAUDE.md

## Branches and pull requests

- Only open pull requests against the branch the user has selected for the session (e.g. `develop`). Never open a pull request against any other branch, and never against `master`.
- Do not push directly to the selected branch. Make changes on a separate feature branch created from the selected branch, and open the pull request from that feature branch into the selected branch.
- Never push to, merge into, or otherwise change `master` or any branch other than the selected branch and the feature branch you created for it.
