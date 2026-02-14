# Production Releases

1. Checkout `origin/main`.
2. Add entries for this release under the `[Unreleased]` section in `CHANGELOG.md`. The format is
   based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/). You do not need to commit
   these changes — the script will include them in the release commit.
3. Run `./release.sh`. The script will:
   - Prompt for the release version.
   - Update `CHANGELOG.md`: rename `[Unreleased]` to the new version with today's date, remove
     empty sections, insert a fresh unreleased template, and update the comparison links.
   - Show the diff for review and ask for confirmation.
   - Update `gradle.properties`, create the release commit and tag, and bump to the next snapshot.
4. Push the commits and tag. This will start a GitHub action that publishes the release to Maven
   Central and creates a new release on GitHub.
   ```
   git push && git push --tags
   ```

# Snapshot Releases

Snapshot releases are automatically created whenever a commit to the `main` branch is pushed.

# Manually uploading a release

Depending on the version in the `gradle.properties` file it will be either a production or snapshot release.
```
./gradlew clean publish --no-build-cache
```

# Installing in Maven Local

```
./gradlew publishToMavenLocal
```
