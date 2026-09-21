#!/usr/bin/env python3
"""Run focused Atlas production Kotlin/JUnit tests without Gradle or Android stubs.

Uses only existing Gradle-cached jars, or an explicitly supplied --json-jar.
The app declares org.json:json:20260814. Nothing is downloaded by this runner.
Android-facing files are parsed with real Kotlin PSI, not type-checked.

HardwareRepairExecutorTest is not executed: HardwareRepairExecutor depends on
HardwareControlArbiter -> SharedHardwareOwnershipStore -> android.system.
That transaction implementation is parsed only, never stripped or faked.
AtlasAdaptiveExecutorTest executes the real adaptive executor through its
existing AtlasRepairPort test seam, not the Android-linked transaction engine.
"""

import argparse
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "manager/app/src/main/java/nd/max"
TEST = ROOT / "manager/app/src/test/java/nd/max"
JSON_VERSION = "20260814"

SYNTAX_CHECK = """
import java.nio.file.*;
import org.jetbrains.kotlin.cli.jvm.compiler.*;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.psi.KtPsiFactory;
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer;
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil;
class CheckAtlasKotlinSyntax {
    public static void main(String[] paths) throws Exception {
        var disposable = Disposer.newDisposable();
        try {
            var env = KotlinCoreEnvironment.createForProduction(disposable,
                new CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES);
            var factory = new KtPsiFactory(env.getProject(), false);
            var valid = factory.createFile("Valid.kt", "fun valid(): Int = 1");
            var invalid = factory.createFile("Invalid.kt", "fun invalid( {");
            if (!PsiTreeUtil.findChildrenOfType(valid, PsiErrorElement.class).isEmpty()
                || PsiTreeUtil.findChildrenOfType(invalid, PsiErrorElement.class).isEmpty()) {
                throw new AssertionError("Kotlin PSI positive/negative self-test failed");
            }
            System.out.println("Kotlin PSI self-test: valid accepted, invalid rejected");
            int errors = 0;
            for (var name : paths) {
                var path = Path.of(name);
                var file = factory.createFile(path.getFileName().toString(), Files.readString(path));
                for (var error : PsiTreeUtil.findChildrenOfType(file, PsiErrorElement.class)) {
                    System.err.println(name + ": " + error.getErrorDescription()
                        + " at offset " + error.getTextOffset());
                    errors++;
                }
            }
            if (errors != 0) throw new AssertionError(errors + " Kotlin syntax errors");
            System.out.println("Kotlin syntax: " + paths.length
                + " files, no parser errors (not type checking)");
        } finally { Disposer.dispose(disposable); }
    }
}
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--work-dir", type=Path, default=Path("/tmp/kilo"),
                        help="existing directory for temporary output (default: /tmp/kilo)")
    parser.add_argument("--json-jar", type=Path,
                        help=f"external org.json:json:{JSON_VERSION} jar instead of the Gradle cache")
    parser.add_argument("--syntax-only", action="store_true",
                        help="run Kotlin PSI parsing only; no JUnit execution or org.json required")
    args = parser.parse_args()
    args.work_dir = args.work_dir.expanduser().resolve()
    if not args.work_dir.is_dir():
        parser.error(f"--work-dir must be an existing directory: {args.work_dir}")
    cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")).expanduser().resolve() / "caches/modules-2/files-2.1"

    def jar(group, artifact, version):
        matches = sorted((cache / group / artifact / version).glob(f"*/{artifact}-{version}.jar"))
        if not matches:
            hint = "; provide --json-jar /path/to/json-20260814.jar" if group == "org.json" else ""
            parser.error(f"Missing cached {group}:{artifact}:{version}{hint}; no dependency was downloaded")
        return str(matches[0])

    stdlib = jar("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.10")
    coroutines = jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.10.2")
    annotations = jar("org.jetbrains", "annotations", "13.0")
    compiler = jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.3.10")
    reflect = jar("org.jetbrains.kotlin", "kotlin-reflect", "1.6.10")
    compiler_cp = os.pathsep.join([compiler, stdlib, coroutines, annotations, reflect])
    java = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if os.environ.get("JAVA_HOME") else "java"

    runtime_cp = None
    if not args.syntax_only:
        junit = jar("junit", "junit", "4.13.2")
        hamcrest = jar("org.hamcrest", "hamcrest-core", "1.3")
        json_jar = args.json_jar.expanduser().resolve() if args.json_jar else Path(jar("org.json", "json", JSON_VERSION))
        try:
            with zipfile.ZipFile(json_jar) as archive:
                required = {"org/json/JSONObject.class", "org/json/JSONArray.class", "org/json/JSONTokener.class"}
                if not required.issubset(archive.namelist()):
                    parser.error(f"--json-jar does not contain the required org.json classes: {json_jar}")
                metadata = "META-INF/maven/org.json/json/pom.properties"
                if metadata in archive.namelist():
                    properties = dict(line.split("=", 1) for line in archive.read(metadata).decode().splitlines()
                                      if "=" in line and not line.startswith("#"))
                    if properties.get("version") != JSON_VERSION:
                        parser.error(f"Expected org.json:json:{JSON_VERSION}, got {properties.get('version')}")
                else:
                    print("org.json jar has no Maven version metadata; version not independently verified", flush=True)
        except (OSError, zipfile.BadZipFile) as error:
            parser.error(f"Cannot read --json-jar {json_jar}: {error}")
        print(f"org.json:json:{JSON_VERSION} declared by app; using {json_jar}", flush=True)
        runtime_cp = os.pathsep.join([stdlib, coroutines, annotations, junit, hamcrest, str(json_jar)])

    sources = [
        SOURCE / "core/atlas/AtlasModels.kt",
        SOURCE / "core/atlas/AtlasDeviceIdentity.kt",
        SOURCE / "core/atlas/AtlasFreshness.kt",
        SOURCE / "core/atlas/AtlasEvidenceStore.kt",
        SOURCE / "core/atlas/AtlasFileStoreIo.kt",
        SOURCE / "core/atlas/AtlasControlIntent.kt",
        SOURCE / "core/atlas/AtlasRoutePlanner.kt",
        SOURCE / "core/hardware/ControlOwnership.kt",
        SOURCE / "core/hardware/HardwareRepairModels.kt",
        SOURCE / "core/hardware/AtlasAdaptiveExecutor.kt",
        SOURCE / "core/hardware/AtlasRouteMemory.kt",
        TEST / "core/hardware/AtlasRouteMemoryTest.kt",
        TEST / "core/hardware/AtlasAdaptiveExecutorTest.kt",
        TEST / "core/atlas/AtlasRoutePlannerTest.kt",
        TEST / "core/hardware/AtlasRouteMemoryWiringTest.kt",
    ]
    tests = [
        "nd.max.core.hardware.AtlasRouteMemoryTest",
        "nd.max.core.hardware.AtlasAdaptiveExecutorTest",
        "nd.max.core.atlas.AtlasRoutePlannerTest",
        "nd.max.core.hardware.AtlasRouteMemoryWiringTest",
    ]
    syntax_sources = [
        SOURCE / "core/hardware/AtlasRouteMemoryFactory.kt",
        SOURCE / "core/di/DataModule.kt",
        SOURCE / "AppMonitor.kt",
        SOURCE / "core/hardware/AtlasAdaptiveExecutor.kt",
        SOURCE / "core/hardware/HardwareRepairExecutor.kt",
        SOURCE / "core/hardware/HardwareControlArbiter.kt",
        SOURCE / "core/hardware/SharedHardwareOwnershipStore.kt",
        TEST / "core/hardware/AtlasAdaptiveExecutorTest.kt",
        TEST / "core/hardware/HardwareRepairExecutorTest.kt",
    ]
    for source in syntax_sources + ([] if args.syntax_only else sources):
        if not source.is_file():
            parser.error(f"Required source not found: {source}")

    print("NOT EXECUTED: HardwareRepairExecutorTest; "
          "production dependency chain reaches android.system via SharedHardwareOwnershipStore. "
          "No platform stubs or source extraction used.", flush=True)
    with tempfile.TemporaryDirectory(prefix="atlas-jvm-", dir=args.work_dir) as output:
        syntax = Path(output) / "CheckAtlasKotlinSyntax.java"
        syntax.write_text(SYNTAX_CHECK, encoding="utf-8")
        subprocess.run([
            java, f"-Djava.io.tmpdir={output}", "-cp", compiler_cp, str(syntax),
            *map(str, syntax_sources),
        ], cwd=ROOT, check=True)
        if args.syntax_only:
            print("Syntax-only mode: no production type checking or JUnit execution", flush=True)
            return
        print(f"Compiling {len(sources)} real Kotlin source/test files; running {len(tests)} JUnit suites", flush=True)
        print("JUnit suites: " + ", ".join(tests), flush=True)
        subprocess.run([
            java, f"-Djava.io.tmpdir={output}", "-cp", compiler_cp, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", runtime_cp,
            "-d", output, *map(str, sources),
        ], cwd=ROOT, check=True)
        subprocess.run([
            java, f"-Djava.io.tmpdir={output}", "-cp", output + os.pathsep + runtime_cp,
            "org.junit.runner.JUnitCore", *tests,
        ], cwd=ROOT, check=True)
        print("Verified only the listed JVM suites and Kotlin syntax; "
              "Android compilation and device recovery remain unverified.", flush=True)


if __name__ == "__main__":
    main()
