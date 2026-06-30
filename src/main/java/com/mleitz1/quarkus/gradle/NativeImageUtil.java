package com.mleitz1.quarkus.gradle;

import java.io.File;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.api.Project;

/**
 * Locates the native-image binary for troubleshooting local native builds.
 * Used by the helper when the user requests native + container=false (machine Graal).
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
}
