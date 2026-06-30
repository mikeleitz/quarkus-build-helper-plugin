# Quarkus Build Helper Plugin

A focused Gradle plugin whose only job is to let you control **two things** from the command line and then help you **troubleshoot** when they don't work:

1. Build a native executable or a jar (`buildType=native|jar`)
2. Do the native build in a container or use the machine's local GraalVM/Mandrel (`containerBuild=true|false`)

It works by reading `-P` properties (the only mechanism the author found that acts early enough) and translating them into the Quarkus properties via `System.setProperty` before the Quarkus Gradle plugin evaluates the build.

The diagnostic output is specifically designed to answer: *"I asked for native on my laptop (native + container=false), why isn't it working?"*

By default the plugin is non-invasive. Configuration and the related troubleshooting only activate when you supply the helper properties.


## What it focuses on

- Forcing **native vs jar** + **container vs local Graal** from the CLI via `-P`
- **Troubleshooting output** that explains *why* the combination you asked for isn't possible in your current environment (especially "I asked for native on the laptop but there's no Graal here").

Everything else (heavy Graal detection, auto validation that throws, lots of project extensions, always-on task wiring) has been removed or made conditional so the plugin stays out of your way unless you're using the two configuration axes.

## Usage

### Adding the plugin to your build

```gradle
plugins {
    id 'com.mleitz1.quarkus.quarkus-build-helper-plugin' version '0.1.5'
}
```

### The two supported configuration axes (via -P)

Use these Gradle project properties on the command line. They are read very early.

```bash
# 1. What to build
-Pquarkus-build-helper-plugin.configure.buildType=native
-Pquarkus-build-helper-plugin.configure.buildType=jar

# 2. Where to build it (only meaningful with native)
-Pquarkus-build-helper-plugin.configure.containerBuild=true     # container
-Pquarkus-build-helper-plugin.configure.containerBuild=false    # use this machine's Graal/Mandrel
# You can also use the words "container" / "local" as values.
```

Common combinations:

```bash
# Native image using a container (CI friendly, no local Graal needed)
./gradlew quarkusBuild -Pquarkus-build-helper-plugin.configure.buildType=native -Pquarkus-build-helper-plugin.configure.containerBuild=true

# Native image using whatever Graal/Mandrel is on the machine (laptop dev)
./gradlew quarkusBuild -Pquarkus-build-helper-plugin.configure.buildType=native -Pquarkus-build-helper-plugin.configure.containerBuild=false

# Fast uber-jar (no native)
./gradlew quarkusBuild -Pquarkus-build-helper-plugin.configure.buildType=jar
```

When you use these, the plugin will:

* Set the corresponding `quarkus.*` system properties early enough for Quarkus to respect them.
* Print a short confirmation at configuration time.
* Produce focused troubleshooting output (via auto-wired tasks or by running the display* tasks) that tells you whether your environment can actually deliver what you asked for.

#### Why a plugin + -P properties?

This was the only reliable way to influence the Quarkus build type and container setting from the command line before Quarkus evaluates its configuration. Using `-D` system properties directly did not take effect early enough. The plugin is applied before the Quarkus plugin processes the build, so the forced values are visible.

### Basic configuration

The plugin reads configuration from standard Quarkus properties. You can set these properties in your `gradle.properties` file, in the command line, or in your build script:

```gradle
// In build.gradle
quarkus {
    // Enable native image building
    quarkus.native.enabled = true

    // Use container for building (useful if your local environment doesn't have GraalVM)
    quarkus.native.container-build = false

    // Disable JAR creation (native only)
    quarkus.package.jar.enabled = false

    // Set memory for native image builder
    quarkus.native.native-image-xmx = "4g"
}
```

### Tasks (troubleshooting-oriented)

- `displayQuarkusBuildOverview` — What you requested + quick reality check for that exact request (e.g. "native local").
- `displayQuarkusBuildDetail` — More data (toolchain Java home, native-image candidate path, effective properties after our forcing).
- `checkNativeEnvironment` — Dumps the same detail + extra diagnosis when you requested local native.
- `validateNativeExecutable` — Confirms a `-runner` binary appeared (only auto-attached for local native requests).

Run them explicitly any time, or they are auto-wired (overview before generate, detail before quarkusBuild) **only when** you are using the configure properties.

Example focused workflow:

```bash
./gradlew quarkusBuild \
  -Pquarkus-build-helper-plugin.configure.buildType=native \
  -Pquarkus-build-helper-plugin.configure.containerBuild=false

# If it fails, re-run the helper tasks for the diagnosis that explains why your local Graal request didn't work:
./gradlew displayQuarkusBuildDetail
```

## Requirements

- Java 17 or later
- Gradle 7.0 or later
- Quarkus 3.0 or later

## License

This project is licensed under the terms of the license found in the LICENSE file in the root directory of this project.
