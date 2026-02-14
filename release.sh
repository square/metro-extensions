#!/bin/bash
set -euo pipefail

REPO_URL="https://github.com/square/metro-extensions"
UNRELEASED_TEMPLATE="## [Unreleased]

### Added

### Changed

### Deprecated

### Removed

### Fixed

### Security

### Other Notes & Contributions"

# Ensure we're on the same commit as origin/main.
git fetch origin
LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse origin/main)
if [ "$LOCAL" != "$REMOTE" ]; then
  echo "Error: HEAD ($LOCAL) does not match origin/main ($REMOTE)."
  echo "Please checkout origin/main before releasing."
  exit 1
fi

# Ask for the release version.
CURRENT_VERSION=$(grep '^VERSION_NAME=' gradle.properties | sed 's/VERSION_NAME=//')
echo "Current version: $CURRENT_VERSION"
read -rp "Enter the version of the new release: " VERSION
if [ -z "$VERSION" ]; then
  echo "Error: No version entered."
  exit 1
fi

# Validate the version and warn if it doesn't look right.
WARNINGS=()

# Check semantic versioning format (MAJOR.MINOR.PATCH).
if ! echo "$VERSION" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$'; then
  WARNINGS+=("'$VERSION' does not follow semantic versioning (expected MAJOR.MINOR.PATCH).")
fi

# Check that the version is higher than the latest release in CHANGELOG.md.
LATEST=$(grep -oE '## \[[0-9]+\.[0-9]+\.[0-9]+\]' CHANGELOG.md | head -1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')
if [ -n "$LATEST" ]; then
  version_to_num() {
    IFS='.' read -r a b c <<< "$1"
    echo $(( a * 1000000 + b * 1000 + c ))
  }
  if [ "$(version_to_num "$VERSION")" -le "$(version_to_num "$LATEST")" ]; then
    WARNINGS+=("'$VERSION' is not higher than the latest release '$LATEST'.")
  fi
fi

if [ ${#WARNINGS[@]} -gt 0 ]; then
  echo ""
  for w in "${WARNINGS[@]}"; do
    echo "⚠️  Warning: $w"
  done
  echo ""
  read -rp "Continue anyway? [Y/n]: " CONFIRM
  case "$CONFIRM" in
    [Yy]|[Yy]es) ;;
    *)
      echo "Aborting."
      exit 1
      ;;
  esac
fi

# Back up CHANGELOG.md before modifying it so we can restore the user's uncommitted
# edits if they decline the changes.
CHANGELOG_BACKUP=$(mktemp)
cp CHANGELOG.md "$CHANGELOG_BACKUP"

# Update CHANGELOG.md: replace [Unreleased] header with the release version and date,
# then insert a fresh unreleased template above it.
TODAY=$(date +%Y-%m-%d)
sed -i '' "s/^## \[Unreleased\]/## [$VERSION] - $TODAY/" CHANGELOG.md

# Remove empty ### sections from the release. A section is empty if it has only blank lines
# before the next header (### or ##) or a link reference line.
awk '
  /^###/ { pending = $0; next }
  /^##/ || /^\[/ { pending = ""; print; next }
  pending && /^[[:space:]]*$/ { next }
  pending { print pending; print ""; pending = ""; print; next }
  { print }
' CHANGELOG.md > CHANGELOG.tmp && mv CHANGELOG.tmp CHANGELOG.md

# Insert a fresh unreleased template above the new release header.
# Split the file at the first "## [" header (the release we just renamed) and reassemble
# with the template in between.
HEADER=$(sed -n '1,/^## \[/{ /^## \[/!p; }' CHANGELOG.md)
REST=$(sed -n '/^## \[/,$p' CHANGELOG.md)
printf '%s\n\n%s\n\n\n%s\n' "$HEADER" "$UNRELEASED_TEMPLATE" "$REST" > CHANGELOG.md

# Update the links at the bottom of CHANGELOG.md.
# Update the [Unreleased] link and insert the new version link after it.
sed -i '' "s|\[Unreleased\]: .*|[Unreleased]: $REPO_URL/compare/$VERSION...HEAD|" CHANGELOG.md
sed -i '' "/^\[Unreleased\]/a\\
[$VERSION]: $REPO_URL/compare/$VERSION
" CHANGELOG.md

# Show the user what changed and ask for confirmation.
echo ""
echo "CHANGELOG.md has been updated. Please review the changes:"
echo ""
git diff CHANGELOG.md
echo ""
read -rp "Does CHANGELOG.md contain all entries for this release? [Y/n]: " CONFIRM
case "$CONFIRM" in
  [Yy]|[Yy]es) ;;
  *)
    echo "Aborting. Restoring CHANGELOG.md to its previous state."
    cp "$CHANGELOG_BACKUP" CHANGELOG.md
    rm -f "$CHANGELOG_BACKUP"
    exit 1
    ;;
esac
rm -f "$CHANGELOG_BACKUP"

# Compute the next snapshot version by bumping the patch number.
IFS='.' read -r MAJOR MINOR PATCH <<< "$VERSION"
NEXT_PATCH=$((PATCH + 1))
NEXT_VERSION="${MAJOR}.${MINOR}.${NEXT_PATCH}-SNAPSHOT"

echo ""
echo "📋 Release version:          $VERSION"
echo "📋 Next development version: $NEXT_VERSION"
echo ""

# Update gradle.properties to the release version.
sed -i '' "s/^VERSION_NAME=.*/VERSION_NAME=$VERSION/" gradle.properties

# Commit and tag the release.
git commit -am "Releasing $VERSION."
git tag "$VERSION"

# Update gradle.properties to the next snapshot version.
sed -i '' "s/^VERSION_NAME=.*/VERSION_NAME=$NEXT_VERSION/" gradle.properties

# Commit the snapshot version bump.
git commit -am "Prepare next development version."

echo ""
echo "✅ Done! Two commits created and tag '$VERSION' applied."
echo ""
echo "🚀 To publish the release, push the commits and tag:"
echo ""
echo "   git push && git push --tags"
echo ""
