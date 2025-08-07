package com.mleitz1.quarkus.gradle;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.api.Project;

/**
 * Utility class for working with GraalVM native-image in Gradle builds.
 * <p>
 * This class provides methods to locate and interact with the native-image binary
 * that is used for building native executables with Quarkus. It supports finding
 * the native-image binary in various locations:
 * <ul>
 *   <li>In the configured Java toolchain</li>
 *   <li>In the JAVA_HOME directory</li>
 *   <li>In the system PATH</li>
 * </ul>
 * <p>
 * It also provides methods to get information about the native-image installation,
 * such as its version and availability.
 */
public class NativeImageUtil {
    /**
     * Finds the path to the native-image binary that will be used for native compilation.
     * <p>
     * This method searches for the native-image executable in the following order:
     * <ol>
     *   <li>In the bin directory of the configured Java toolchain</li>
     *   <li>In the bin directory of JAVA_HOME (${JAVA_HOME}/bin/native-image)</li>
     *   <li>In any directory listed in the PATH environment variable</li>
     * </ol>
     * <p>
     * The method automatically handles platform differences, looking for native-image.exe
     * on Windows systems and native-image on other platforms.
     *
     * @param project the Gradle project instance
     * @return the absolute path to the native-image binary, or null if not found
     */
    public String findNativeImageBinary(Project project) {
        String nativeImageExe = System.getProperty("os.name").toLowerCase().contains("windows") ? "native-image.exe" : "native-image";

        // Method 1: Check in the configured Java toolchain
        String toolchainNativeImage = findNativeImageInToolchain(project, nativeImageExe);
        if (toolchainNativeImage != null) {
            return toolchainNativeImage;
        }

        // Method 2: Check in JAVA_HOME/bin
        String javaHome = System.getProperty("java.home");
        File nativeImagePath = new File(javaHome, "bin/" + nativeImageExe);
        if (nativeImagePath.exists() && nativeImagePath.canExecute()) {
            return nativeImagePath.getAbsolutePath();
        }

        // Method 3: Check in PATH
        String pathNativeImage = findNativeImageInPath(nativeImageExe);
        if (pathNativeImage != null) {
            return pathNativeImage;
        }

        return null;
    }

    /**
     * Searches for native-image in the configured Java toolchain.
     *
     * @param project the Gradle project instance
     * @param nativeImageExe the native-image executable name (platform-specific)
     * @return the absolute path to native-image, or null if not found
     */
    private String findNativeImageInToolchain(Project project, String nativeImageExe) {
        try {
            // Get the Java toolchain service
            JavaToolchainService toolchainService = project.getExtensions()
                .getByType(JavaToolchainService.class);

            // Get the Java toolchain spec from the project
            JavaToolchainSpec toolchainSpec = project.getExtensions()
                .getByType(JavaPluginExtension.class)
                .getToolchain();

            // Get the launcher for the configured toolchain
            Provider<JavaLauncher> launcherProvider = toolchainService.launcherFor(toolchainSpec);
            JavaLauncher launcher = launcherProvider.get();

            // Get the toolchain's Java home
            Directory javaHome = launcher.getMetadata().getInstallationPath();
            File nativeImagePath = new File(javaHome.getAsFile(), "bin/" + nativeImageExe);

            if (nativeImagePath.exists() && nativeImagePath.canExecute()) {
                return nativeImagePath.getAbsolutePath();
            }
        } catch (Exception e) {
            // Log the exception but continue with fallback methods
            project.getLogger().debug("Could not find native-image in toolchain: " + e.getMessage());
        }

        return null;
    }

    /**
     * Searches for native-image in the system PATH.
     *
     * @param nativeImageExe the native-image executable name (platform-specific)
     * @return the absolute path to native-image, or null if not found
     */
    private String findNativeImageInPath(String nativeImageExe) {
        String pathVar = System.getenv("PATH");
        if (pathVar != null) {
            String[] pathDirs = pathVar.split(File.pathSeparator);
            for (String dir : pathDirs) {
                File nativeImageInPath = new File(dir, nativeImageExe);
                if (nativeImageInPath.exists() && nativeImageInPath.canExecute()) {
                    return nativeImageInPath.getAbsolutePath();
                }
            }
        }
        return null;
    }

    /**
     * Gets comprehensive information about the native-image setup.
     *
     * @param project the Gradle project instance
     * @return a NativeImageInfo object containing all relevant information
     */
    public NativeImageInfo getNativeImageInfo(Project project) {
        String nativeImagePath = findNativeImageBinary(project);
        boolean isAvailable = nativeImagePath != null;
        String version = null;

        if (isAvailable) {
            version = getNativeImageVersion(nativeImagePath);
        }

        return new NativeImageInfo(isAvailable, nativeImagePath, version);
    }

    /**
     * Gets the version of the native-image binary.
     *
     * @param nativeImagePath the absolute path to the native-image binary
     * @return the version string, or null if unable to determine
     */
    private String getNativeImageVersion(String nativeImagePath) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(nativeImagePath, "--version");
            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    // Extract version from output like "native-image 21.0.1 2023-10-17"
                    return line.trim();
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // Unable to get version, return null
        }

        return null;
    }

    /**
     * Immutable data class to hold information about a native-image installation.
     * <p>
     * This class encapsulates details about a native-image binary, including:
     * <ul>
     *   <li>Whether the native-image binary is available in the current environment</li>
     *   <li>The absolute path to the native-image binary (if available)</li>
     *   <li>The version of the native-image binary (if available)</li>
     * </ul>
     * <p>
     * This information is useful for diagnostics and for determining if the
     * environment is properly set up for native image building.
     */
    public static class NativeImageInfo {
        private final boolean available;
        private final String path;
        private final String version;

        /**
         * Creates a new NativeImageInfo instance.
         *
         * @param available whether the native-image binary is available
         * @param path the absolute path to the native-image binary, or null if not available
         * @param version the version of the native-image binary, or null if not available
         */
        public NativeImageInfo(boolean available, String path, String version) {
            this.available = available;
            this.path = path;
            this.version = version;
        }

        /**
         * Checks if the native-image binary is available.
         *
         * @return true if the native-image binary is available, false otherwise
         */
        public boolean isAvailable() {
            return available;
        }

        /**
         * Gets the absolute path to the native-image binary.
         *
         * @return the absolute path to the native-image binary, or null if not available
         */
        public String getPath() {
            return path;
        }

        /**
         * Gets the version of the native-image binary.
         *
         * @return the version of the native-image binary, or null if not available
         */
        public String getVersion() {
            return version;
        }

        @Override
        public String toString() {
            return "NativeImageInfo{" +
                "available=" + available +
                ", path='" + path + '\'' +
                ", version='" + version + '\'' +
                '}';
        }
    }
}
