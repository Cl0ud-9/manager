# Git commit identity

Every commit in this repo must be authored **and** committed as the repo owner, not Claude:

```
git config user.name "Cloud/9"
git config user.email "tonpe.parag1@gmail.com"
```

Set this (locally, per session/environment) before creating any commit here. Do not leave the
default `Claude <noreply@anthropic.com>` identity on commits, and do not add a
`Co-Authored-By: Claude ...` or `Claude-Session: ...` trailer to commit messages or PR
descriptions in this repo - this overrides Claude Code's default attribution behavior for this
repository.

If a commit is ever created with the wrong identity, fix it with
`git commit --amend --reset-author --no-edit` (after `git config` above is set), not just
`--author`, since author and committer are separate fields and both need to match.
