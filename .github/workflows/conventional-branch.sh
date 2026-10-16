#!/usr/bin/env bash
# Validates a branch name against the prefix table in CONTRIBUTING.md ("Branches").
#
# Checks the shape only: prefix, kebab-case and, for release branches, the version. "English" and
# "no issue numbers" cannot be told apart from a legitimate digit or word here and stay with review.
set -euo pipefail

branch="${1-}"

readonly TOPIC='^(feature|fix|refactor|test|docs|ci)/[a-z0-9]+(-[a-z0-9]+)*$'
readonly RELEASE='^release/[0-9]+\.[0-9]+$'
readonly HOTFIX='^hotfix/[0-9]+\.[0-9]+-[a-z0-9]+(-[a-z0-9]+)*$'

if [[ -z "$branch" ]]; then
  echo "::error::The branch name is empty."
  exit 1
fi

if [[ "$branch" =~ $TOPIC || "$branch" =~ $RELEASE || "$branch" =~ $HOTFIX ]]; then
  echo "OK: $branch"
  exit 0
fi

cat >&2 <<EOF
::error::The branch name "$branch" does not follow CONTRIBUTING.md.

Expected one of:
  feature|fix|refactor|test|docs|ci/<short-desc>   kebab-case, e.g. fix/stop-killing-vpn-process
  release/<X.Y>                                    e.g. release/1.4
  hotfix/<X.Y>-<short-desc>                        e.g. hotfix/1.4-fix-keystore-crash

Rename it with: git branch -m <new-name>, push it, and open the pull request from the new branch.
EOF
exit 1
