# Quarkus Build Helper Plugin

A Gradle plugin that helps make it easier to configure and build native executables with Quarkus.

This plugin provides diagnostic tools and validation for Quarkus native builds, helping you:

- Verify that your environment is properly set up for native image building
- Understand your current Quarkus build configuration
- Diagnose issues with native image builds
- Validate native executables after they're built

By default, the plugin is non-invasive and does not modify your existing Quarkus configuration. However, you can opt-in to have it enforce the build type (jar or native) via a Gradle property (see Build type configuration below).


## Features

- **Environment Validation**: Automatically checks if your environment is properly set up for native image building
- **Build Configuration Diagnostics**: Provides tasks to display your current Quarkus build configuration
- **Native Executable Validation**: Verifies native executables after they're built
- **Optional Build-Type Enforcement**: Opt-in property to ensure your build runs as jar or native
- **Detailed Error Messages**: Provides detailed error messages with instructions when native build requirements aren't met
- **Toolchain Integration**: Works with Gradle's toolchain API to find the correct Java installation

## Usage

### Adding the plugin to your build

```gradle
plugins {
    id 'com.mleitz1.quarkus.quarkus-build-helper' version '0.1.3'
}
```

### Build type configuration (opt-in)

You can instruct this plugin to enforce the Quarkus build type via a Gradle property. Set one of the following on the command line:

- Native image build:
  -Pquarkus-build-helper-plugin.configure.buildType=native

- JAR build (uber-jar):
  -Pquarkus-build-helper-plugin.configure.buildType=jar

Effects when set:
- buildType=native: the plugin will ensure quarkusBuild attempts to create a native image (quarkus.native.enabled=true, quarkus.package.jar.enabled=false).
- buildType=jar: the plugin will ensure a JAR build runs as an uber-jar (quarkus.package.jar.enabled=true, quarkus.package.jar.type=uber-jar, quarkus.native.enabled=false).

Examples:
```bash
# Force a native image build
./gradlew quarkusBuild -Pquarkus-build-helper-plugin.configure.buildType=native

# Force a JAR (uber-jar) build
./gradlew quarkusBuild -Pquarkus-build-helper-plugin.configure.buildType=jar
```

If the property is omitted, the plugin will not modify your Quarkus build configuration.

#### Why offer setting this property?

This is the only way I could specify a build type via the ./gradlew command. I want to build a jar for dev but always a native image for prod.

The above examples, seem impossible to achieve via the build command without this. You can't inject or modify these in gradle.build because it's already too late.

The plugin approach works because plugins are applied early in the build cycle before it hits the error condition.

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

### Available configuration options

| Property                           | Description                                             | Default                                                |
|------------------------------------|---------------------------------------------------------|--------------------------------------------------------|
| quarkus.native.enabled             | Whether to enable native image building                 | false                                                  |
| quarkus.native.container-build     | Whether to build the native image in a container        | false                                                  |
| quarkus.native.remote-container-build | Whether to build the native image in a remote container | false                                               |
| quarkus.package.jar.enabled        | Whether to build a JAR file (set to false for native-only) | true                                               |
| quarkus.native.builder-image       | The builder image to use for container builds           | quay.io/quarkus/ubi-quarkus-native-image:22.0.1-java17 |
| quarkus.native.native-image-xmx    | The maximum heap size for the native image builder      | 4g                                                     |
| quarkus.native.additionalBuildArgs | Additional arguments to pass to the native-image command | none                                                  |

### Tasks

- `displayQuarkusBuildOverview`: Displays a basic overview of the Quarkus build configuration
- `displayQuarkusBuildDetail`: Displays detailed information about the Quarkus build configuration
- `checkNativeEnvironment`: Validates the environment for native image building
- `validateNativeExecutable`: Verifies the native executable after build

### Task Examples

Check if your environment is ready for native builds:
```bash
./gradlew checkNativeEnvironment
```

Display your current Quarkus build configuration:
```bash
./gradlew displayQuarkusBuildDetail
```

Build a native executable and validate it:
```bash
./gradlew build
# The validateNativeExecutable task will run automatically after build
```

You can also run the validation task directly:
```bash
./gradlew validateNativeExecutable
```

## Requirements

- Java 17 or later
- Gradle 7.0 or later
- Quarkus 3.0 or later

## License

This project is licensed under the terms of the license found in the LICENSE file in the root directory of this project.
