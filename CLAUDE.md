## Build Efficiency

- Do not run Gradle, NDK, or full project builds after every individual edit.
- Batch related changes together before building.
- Use Serena for symbol navigation, references, implementations, and targeted code analysis before making changes.
- Prefer static analysis, targeted inspection, and existing logs when they are sufficient to validate a change.
- Run a build when:
  1. A batch of related changes is complete.
  2. Compilation is necessary to validate an assumption.
  3. A previous build exposed an error that needs verification.
  4. The user explicitly requests a build.
- Prefer the smallest relevant Gradle task over a full build when possible.
- After fixing build errors, rebuild only the affected target first when practical.
- Do not skip necessary validation just to save time.
