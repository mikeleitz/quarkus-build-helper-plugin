# Quarkus Build Helper Plugin

A Gradle plugin that helps make it easier to configure and build native executables with Quarkus.

This plugin provides diagnostic tools and validation for Quarkus native builds, helping you:

- Verify that your environment is properly set up for native image building
- Understand your current Quarkus build configuration
- Diagnose issues with native image builds
- Validate native executables after they're built

The plugin is non-invasive and doesn't modify your existing Quarkus configuration - it only provides information and validation.


## Features

- **Environment Validation**: Automatically checks if your environment is properly set up for native image building
- **Build Configuration Diagnostics**: Provides tasks to display your current Quarkus build configuration
- **Native Executable Validation**: Verifies native executables after they're built
- **Non-invasive**: The plugin is read-only and won't modify your existing Quarkus configuration
- **Detailed Error Messages**: Provides detailed error messages with instructions when native build requirements aren't met
- **Toolchain Integration**: Works with Gradle's toolchain API to find the correct Java installation

## Usage

### Adding the plugin to your build

```gradle
plugins {
    id 'com.mleitz1.quarkus.quarkus-build-helper' version '0.1.0'
}
```

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
