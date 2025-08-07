package com.mleitz1.quarkus.gradle;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

/**
 * A Gradle plugin that encapsulates Quarkus build support.
 * This plugin simplifies the configuration of Quarkus builds
 * by providing sensible defaults and helper tasks.
 * <p>
 * It includes native environment detection and validation to ensure
 * the build environment meets the requirements for native image building.
 * <p>
 * The plugin provides several diagnostic tasks:
 * <ul>
 *   <li>displayQuarkusBuildOverview - Shows basic Quarkus build configuration</li>
 *   <li>displayQuarkusBuildDetail - Shows detailed Quarkus build configuration</li>
 *   <li>checkNativeEnvironment - Validates the environment for native image building</li>
 *   <li>validateNativeExecutable - Verifies the native executable after build</li>
 * </ul>
 * <p>
 * It also exposes several utility functions as project extensions that can be used
 * in build scripts to check the native build environment.
 */
public class QuarkusBuildHelperPlugin implements Plugin<Project> {
    /**
     * Task group name for all Quarkus diagnostic tasks created by this plugin.
     */
    private static final String QUARKUS_DIAGNOSTICS_TASK_GROUP = "Quarkus Diagnostics";

    /**
     * The plugin ID for the Quarkus Gradle plugin.
     */
    public static String QUARKUS_PLUGIN_ID = "io.quarkus";

    /**
     * Property resolver for accessing Quarkus-specific properties.
     */
    Mleitz1QuarkusPropertyResolver propertyResolver;

    NativeImageUtil nativeImageUtil = new NativeImageUtil();

    /**
     * Default constructor for the plugin.
     */
    public QuarkusBuildHelperPlugin() {
        // Default constructor implementation
    }

    /**
     * Applies the plugin to the specified project.
     * <p>
     * This method initializes the property resolver, registers utility functions as project extensions,
     * and creates diagnostic tasks for Quarkus builds.
     *
     * @param project The Gradle project to which this plugin is applied
     */
    @Override
    public void apply(Project project) {
        // Create an instance of the property resolver
        propertyResolver = new Mleitz1QuarkusPropertyResolver(project);

        // Make the property resolver available through project extensions
        project.getExtensions().getExtraProperties().set("mleitz1QuarkusPropertyResolver", propertyResolver);

        // Define various recon functions
        project.getExtensions().getExtraProperties().set("isGraalVM", (java.util.function.Supplier<Boolean>) this::isGraalVM);
        project.getExtensions().getExtraProperties().set("isMandrel", (java.util.function.Supplier<Boolean>) this::isMandrel);
        project.getExtensions().getExtraProperties().set("isNativeCapableJVM", (java.util.function.Supplier<Boolean>) this::isNativeCapableJVM);
        project.getExtensions().getExtraProperties().set("isNativeImageAvailable", (java.util.function.Supplier<Boolean>) this::isNativeImageAvailable);
        project.getExtensions().getExtraProperties().set("getNativeJVMType", (java.util.function.Supplier<String>) this::getNativeJVMType);
        project.getExtensions().getExtraProperties().set("getDetailedJVMInfo", (java.util.function.Supplier<Map<String, Object>>) this::getDetailedJVMInfo);
        project.getExtensions().getExtraProperties().set("validateNativeEnvironment", (java.util.function.Supplier<Boolean>) this::validateNativeEnvironment);
        project.getExtensions().getExtraProperties().set("isQuarkusPluginApplied", (java.util.function.Supplier<Boolean>) () -> project.getPlugins().hasPlugin(QUARKUS_PLUGIN_ID));

        createNewTasks(project);
        registerTasks(project);
    }

    /**
     * Creates and registers the diagnostic tasks provided by this plugin.
     * <p>
     * This method creates the following tasks:
     * <ul>
     *   <li>displayQuarkusBuildOverview - Shows basic Quarkus build configuration</li>
     *   <li>displayQuarkusBuildDetail - Shows detailed Quarkus build configuration</li>
     *   <li>validateNativeExecutable - Verifies the native executable after build</li>
     *   <li>checkNativeEnvironment - Validates the environment for native image building</li>
     * </ul>
     *
     * @param project The Gradle project to which the tasks will be added
     */
    private void createNewTasks(Project project) {
        TaskContainer tasks = project.getTasks();

        // Register a task to display Quarkus property status
        tasks.register("displayQuarkusBuildOverview", task -> {
            task.setGroup(QUARKUS_DIAGNOSTICS_TASK_GROUP);
            task.setDescription("Displays the status of Quarkus build properties");

            task.doLast(t -> {
                System.out.println("\n=========================================================");
                System.out.println("QUARKUS BUILD - OVERVIEW");
                System.out.println("=========================================================");
                System.out.println("⚙️ quarkus.native.enabled: " + propertyResolver.getQuarkusNativeEnabledStatus());
                System.out.println("⚙️ quarkus.native.container-build: " + propertyResolver.getQuarkusNativeContainerBuildStatus());
                System.out.println("⚙️ quarkus.package.jar.enabled: " + propertyResolver.getQuarkusPackageJarEnabledStatus());
                System.out.println("⚙️ quarkus.native.remote-container-build: " + propertyResolver.getQuarkusNativeRemoteContainerBuildStatus());
                System.out.println("⚙️ Builder Image: " + propertyResolver.getQuarkusNativeBuilderImage());
                System.out.println("=========================================================\n");
            });
        });

        // Register a task to display the native build configuration
        tasks.register("displayQuarkusBuildDetail", task -> {
            task.setGroup(QUARKUS_DIAGNOSTICS_TASK_GROUP);
            task.setDescription("Displays Quarkus build detail - useful for troubleshooting NATIVE builds");

            task.doLast(t -> {
                System.out.println("\n=========================================================");
                System.out.println("QUARKUS BUILD - DETAIL");
                System.out.println("=========================================================");
                System.out.println("⚙️ Java Home: " + getJavaHome(project));
                System.out.println("⚙️ Java JDK Binary: " + getJavaJdkBinary(project));
                System.out.println("⚙️ Native Image Binary: " + nativeImageUtil.findNativeImageBinary(project));
                System.out.println("");
                System.out.println("⚙️ Native Build Enabled: " + propertyResolver.getQuarkusNativeEnabledStatus());
                System.out.println("⚙️ JAR Build Enabled: " + propertyResolver.getQuarkusPackageJarEnabledStatus());
                System.out.println("⚙️ Container Build: " + propertyResolver.getQuarkusNativeContainerBuildStatus());
                System.out.println("⚙️ Remote Container Build: " + propertyResolver.getQuarkusNativeRemoteContainerBuildStatus());
                System.out.println("");
                System.out.println("⚙️ Builder Image: " + propertyResolver.getQuarkusNativeBuilderImage());
                System.out.println("⚙️ Native Image Memory: " + propertyResolver.getQuarkusNativeNativeImageXmx());
                System.out.println("⚙️ Validate Native Environment: " + (validateNativeEnvironment() ? "✅ Valid" : "❌ Invalid"));
                System.out.println("⚙️ Native JVM Type: " + getNativeJVMType());
                System.out.println("⚙️ Native Image Available: " + (isNativeImageAvailable() ? "✅ Valid" : "❌ Invalid"));
                System.out.println("=========================================================\n");
            });
        });

        // Register a task to verify native executable after build
        tasks.register("validateNativeExecutable", task -> {
            task.setGroup(QUARKUS_DIAGNOSTICS_TASK_GROUP);
            task.setDescription("Verifies native executable details after the build is done");
            task.dependsOn("quarkusBuild");

            task.doLast(t -> {
                String nativeExecutablePath = project.getProjectDir().getAbsolutePath() + "/build/" + project.getName() + "-" + project.getVersion() + "-runner";
                File nativeExecutable = new File(nativeExecutablePath);
                System.out.println("\n=========================================================");
                System.out.println("QUARKUS BUILD - THE FINISH");
                System.out.println("=========================================================");

                if (nativeExecutable.exists()) {
                    System.out.println("✅ Native executable created successfully:");
                    System.out.println("   📁 Location: " + nativeExecutable.getAbsolutePath());
                    System.out.println("   📏 Size: " + String.format("%.2f MB", nativeExecutable.length() / 1024.0 / 1024.0));
                    System.out.println("   🚀 Run with: chmod +x " + nativeExecutablePath + " && " + nativeExecutablePath);
                } else {
                    System.out.println("❌ Native executable not found at expected location");
                    System.out.println("Missing: " + nativeExecutablePath);
                }
            });
        });

        // Register a task to check the native build environment
        tasks.register("checkNativeEnvironment", task -> {
            task.setGroup(QUARKUS_DIAGNOSTICS_TASK_GROUP);
            task.setDescription("Checks the build environment for native image building - useful for troubleshooting NATIVE builds");

            task.doLast(t -> {
                String jvmType = getNativeJVMType();
                Map<String, Object> detailedInfo = getDetailedJVMInfo();

                System.out.println("🔍 Native Build Environment Check:");
                System.out.println(" Current JVM: " + detailedInfo.get("vendor") + " " + detailedInfo.get("javaVersion"));
                System.out.println(" Runtime Name: " + detailedInfo.get("runtime"));
                System.out.println(" VM Name: " + detailedInfo.get("vmName"));
                System.out.println(" VM Version: " + detailedInfo.get("vmVersion"));
                System.out.println(" Java Home: " + detailedInfo.get("javaHome"));
                System.out.println(" Mandrel in Path: " + detailedInfo.get("mandrelInPath"));
                System.out.println(" Quarkus Plugin Applied: " + (project.getPlugins().hasPlugin(QUARKUS_PLUGIN_ID) ? "✅ Applied" : "❌ Not applied"));
                System.out.println(" GraalVM: " + (isGraalVM() ? "✅ Detected" : "❌ Not detected"));
                System.out.println(" Mandrel: " + (isMandrel() ? "✅ Detected" : "❌ Not detected"));
                System.out.println(" Native Capable: " + (isNativeCapableJVM() ? "✅ " + jvmType : "❌ Not detected"));
                System.out.println(" Native Image: " + (isNativeImageAvailable() ? "✅ Available" : "❌ Not available"));

                // Show release file content for debugging
                System.out.println("\n📄 Release File Content:");
                System.out.println(detailedInfo.get("releaseContent"));

                if (!isNativeCapableJVM() || !isNativeImageAvailable()) {
                    System.out.println("\n💡 To enable native builds:");
                    System.out.println("   Option 1 - GraalVM:");
                    System.out.println("     1. Install GraalVM from https://www.graalvm.org/downloads/");
                    System.out.println("     2. Set JAVA_HOME to point to GraalVM installation");
                    System.out.println("     3. Install native-image: gu install native-image");
                    System.out.println("   Option 2 - Mandrel:");
                    System.out.println("     1. Install Mandrel from https://github.com/graalvm/mandrel/releases");
                    System.out.println("     2. Set JAVA_HOME to point to Mandrel installation");
                    System.out.println("     3. native-image is included with Mandrel");
                    System.out.println("   4. Verify: native-image --version");
                    System.out.println("\n⚠️  This project requires GraalVM or Mandrel with native-image for building.");
                } else {
                    System.out.println("\n🎉 Native build environment is ready with " + jvmType + "!");
                }
            });
        });
    }

    /**
     * Registers task dependencies between plugin tasks and Quarkus tasks.
     * <p>
     * This method sets up the following dependencies:
     * <ul>
     *   <li>quarkusGenerateCode depends on displayQuarkusBuildOverview</li>
     *   <li>quarkusBuild depends on displayQuarkusBuildDetail</li>
     *   <li>build is finalized by validateNativeExecutable</li>
     * </ul>
     * <p>
     * These dependencies ensure that diagnostic information is displayed at appropriate
     * points during the build process.
     *
     * @param project The Gradle project in which to register task dependencies
     */
    private void registerTasks(Project project) {
        // Use afterEvaluate to ensure all plugins are applied and tasks are created
        project.afterEvaluate(p -> {
            TaskContainer tasks = p.getTasks();

            // Only set up Quarkus task dependencies if the Quarkus plugin is present
            if (project.getPlugins().hasPlugin(QUARKUS_PLUGIN_ID)) {
                // Wire up overview task to quarkusGenerateCode if it exists
                Task quarkusGenerateCode = tasks.findByName("quarkusGenerateCode");
                Task displayOverview = tasks.findByName("displayQuarkusBuildOverview");

                if (displayOverview != null && quarkusGenerateCode != null) {
                    quarkusGenerateCode.dependsOn(displayOverview);
                }

                // Wire up detail task to quarkusBuild if it exists
                Task quarkusBuild = tasks.findByName("quarkusBuild");
                Task displayDetail = tasks.findByName("displayQuarkusBuildDetail");


                if (displayDetail != null && quarkusBuild != null) {
                    quarkusBuild.dependsOn(displayDetail);
                }
            }

            // Wire up validation to build task (always available)
            Task buildTask = tasks.findByName("build");
            Task validateNativeExecutable = tasks.findByName("validateNativeExecutable");

            if (buildTask != null && validateNativeExecutable != null) {
                buildTask.finalizedBy(validateNativeExecutable);
            }
        });
    }

    /**
     * Gets the path to the Java executable from the project's toolchain.
     * <p>
     * This method retrieves the Java executable path from the project's configured
     * Java toolchain. It uses the Gradle Toolchain API to get the launcher for the
     * configured toolchain and then extracts the path to the Java executable.
     * <p>
     * This is useful for diagnostic purposes and for determining which Java
     * installation is being used for the build.
     *
     * @param project the Gradle project
     * @return the absolute path to the Java executable
     */
    private String getJavaJdkBinary(Project project) {
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

        return launcher.getExecutablePath().getAsFile().getAbsolutePath();
    }

    /**
     * Gets the Java home directory from the project's toolchain.
     * <p>
     * This method retrieves the Java home directory from the project's configured
     * Java toolchain. It uses the Gradle Toolchain API to get the launcher for the
     * configured toolchain and then extracts the installation path.
     * <p>
     * The Java home directory is important for locating native-image and other
     * tools that are part of the JDK installation.
     *
     * @param project the Gradle project
     * @return the absolute path to the Java home directory
     */
    private String getJavaHome(Project project) {
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
        Directory javaHome = launcher.getMetadata().getInstallationPath();

        return javaHome.getAsFile().getAbsolutePath();
    }

    /**
     * Checks if the current JVM is GraalVM.
     * <p>
     * This method examines various system properties to determine if the current JVM
     * is GraalVM. It checks the following properties:
     * <ul>
     *   <li>java.vendor - Checks if it contains "graalvm"</li>
     *   <li>java.runtime.name - Checks if it contains "graalvm"</li>
     *   <li>java.vm.name - Checks if it contains "graalvm"</li>
     * </ul>
     *
     * @return true if running on GraalVM, false otherwise
     */
    public boolean isGraalVM() {
        String javaVendor = System.getProperty("java.vendor");
        String javaRuntimeName = System.getProperty("java.runtime.name");
        String javaVmName = System.getProperty("java.vm.name");

        return (javaVendor != null && javaVendor.toLowerCase().contains("graalvm")) ||
            (javaRuntimeName != null && javaRuntimeName.toLowerCase().contains("graalvm")) ||
            (javaVmName != null && javaVmName.toLowerCase().contains("graalvm"));
    }

    /**
     * Checks if the current JVM is Mandrel.
     * <p>
     * This method performs multiple checks to determine if the current JVM is Mandrel:
     * <ol>
     *   <li>Checks system properties for "mandrel" string:
     *     <ul>
     *       <li>java.vendor</li>
     *       <li>java.runtime.name</li>
     *       <li>java.vm.name</li>
     *       <li>java.vm.version</li>
     *     </ul>
     *   </li>
     *   <li>Checks if "mandrel" appears in the JAVA_HOME path</li>
     *   <li>Examines the content of the "release" file in JAVA_HOME for "mandrel" string</li>
     *   <li>Checks if the parent directory of JAVA_HOME contains "mandrel" in its name</li>
     * </ol>
     *
     * @return true if running on Mandrel, false otherwise
     */
    public boolean isMandrel() {
        // Check system properties first
        String javaVendor = System.getProperty("java.vendor");
        String javaRuntimeName = System.getProperty("java.runtime.name");
        String javaVmName = System.getProperty("java.vm.name");
        String javaVmVersion = System.getProperty("java.vm.version");

        if ((javaVendor != null && javaVendor.toLowerCase().contains("mandrel")) ||
            (javaRuntimeName != null && javaRuntimeName.toLowerCase().contains("mandrel")) ||
            (javaVmName != null && javaVmName.toLowerCase().contains("mandrel")) ||
            (javaVmVersion != null && javaVmVersion.toLowerCase().contains("mandrel"))) {
            return true;
        }

        // Check JAVA_HOME path for mandrel
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && javaHome.toLowerCase().contains("mandrel")) {
            return true;
        }

        // Check for Mandrel-specific files in JAVA_HOME
        Path releasePath = Path.of(javaHome, "release");
        if (Files.exists(releasePath)) {
            try {
                String releaseContent = Files.readString(releasePath);
                if (releaseContent.toLowerCase().contains("mandrel")) {
                    return true;
                }
            } catch (Exception e) {
                // Ignore file reading errors
            }
        }

        // Check for mandrel in the lib/modules file (if it exists)
        File modulesFile = new File(javaHome, "lib/modules");
        if (modulesFile.exists()) {
            try {
                // For Mandrel, the module file might contain mandrel-specific entries
                // This is a fallback check
                File javaHomeParent = new File(javaHome).getParentFile();
                if (javaHomeParent != null && javaHomeParent.getName().toLowerCase().contains("mandrel")) {
                    return true;
                }
            } catch (Exception e) {
                // Ignore errors
            }
        }

        return false;
    }

    /**
     * Checks if the current JVM is capable of native image building.
     * <p>
     * A JVM is considered "native capable" if it is either GraalVM or Mandrel.
     * These are the only JVM distributions that support building native executables
     * using the GraalVM Native Image technology required by Quarkus native builds.
     * <p>
     * This method combines the results of {@link #isGraalVM()} and {@link #isMandrel()}
     * to determine if the current JVM can support native image building.
     *
     * @return true if the JVM is GraalVM or Mandrel, false otherwise
     * @see #isGraalVM()
     * @see #isMandrel()
     */
    public boolean isNativeCapableJVM() {
        return isGraalVM() || isMandrel();
    }

    /**
     * Checks if the native-image tool is available in the current environment.
     * <p>
     * This method checks for the presence of the native-image executable in two locations:
     * <ol>
     *   <li>In the bin directory of JAVA_HOME (${JAVA_HOME}/bin/native-image)</li>
     *   <li>In any directory listed in the PATH environment variable</li>
     * </ol>
     * <p>
     * The method automatically handles platform differences, looking for native-image.exe
     * on Windows systems and native-image on other platforms.
     *
     * @return true if native-image is available, false otherwise
     */
    public boolean isNativeImageAvailable() {
        String javaHome = System.getProperty("java.home");
        String nativeImageExe = System.getProperty("os.name").toLowerCase().contains("windows") ? "native-image.exe" : "native-image";

        // Check in JAVA_HOME/bin
        File nativeImagePath = new File(javaHome, "bin/" + nativeImageExe);
        if (nativeImagePath.exists()) {
            return true;
        }

        // Check in PATH
        String pathVar = System.getenv("PATH");
        if (pathVar != null) {
            String[] pathDirs = pathVar.split(File.pathSeparator);
            for (String dir : pathDirs) {
                File nativeImageInPath = new File(dir, nativeImageExe);
                if (nativeImageInPath.exists()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Gets the type of native-capable JVM.
     * <p>
     * This method identifies the specific type of native-capable JVM that is currently running.
     * It returns a string indicating whether the JVM is GraalVM, Mandrel, or an unknown type.
     * This information is useful for diagnostic purposes and for providing user feedback
     * about the build environment.
     * <p>
     * The method uses {@link #isGraalVM()} and {@link #isMandrel()} to determine the JVM type.
     *
     * @return "GraalVM" if running on GraalVM, "Mandrel" if running on Mandrel, or "Unknown" otherwise
     * @see #isGraalVM()
     * @see #isMandrel()
     */
    public String getNativeJVMType() {
        if (isGraalVM()) {
            return "GraalVM";
        } else if (isMandrel()) {
            return "Mandrel";
        } else {
            return "Unknown";
        }
    }

    /**
     * Gets detailed information about the current JVM environment.
     * <p>
     * This method collects various system properties and environment information
     * related to the JVM and returns them in a map. The map contains the following keys:
     * <ul>
     *   <li>"vendor" - The Java vendor (java.vendor property)</li>
     *   <li>"runtime" - The Java runtime name (java.runtime.name property)</li>
     *   <li>"vmName" - The Java VM name (java.vm.name property)</li>
     *   <li>"vmVersion" - The Java VM version (java.vm.version property)</li>
     *   <li>"javaHome" - The Java home directory (java.home property)</li>
     *   <li>"javaVersion" - The Java version (java.version property)</li>
     *   <li>"mandrelInPath" - Boolean indicating if "mandrel" is in the Java home path</li>
     *   <li>"releaseContent" - Content of the release file in Java home, if available</li>
     * </ul>
     *
     * @return a map containing detailed JVM information
     */
    public Map<String, Object> getDetailedJVMInfo() {
        String javaHome = System.getProperty("java.home");
        Map<String, Object> info = new HashMap<>();

        info.put("vendor", System.getProperty("java.vendor"));
        info.put("runtime", System.getProperty("java.runtime.name"));
        info.put("vmName", System.getProperty("java.vm.name"));
        info.put("vmVersion", System.getProperty("java.vm.version"));
        info.put("javaHome", javaHome);
        info.put("javaVersion", System.getProperty("java.version"));

        // Check if JAVA_HOME contains mandrel in the path
        info.put("mandrelInPath", javaHome != null && javaHome.toLowerCase().contains("mandrel"));

        // Try to read the release file
        Path releasePath = Path.of(javaHome, "release");
        if (Files.exists(releasePath)) {
            try {
                String content = Files.readString(releasePath);
                info.put("releaseContent", content);
            } catch (Exception e) {
                info.put("releaseContent", "Could not read release file: " + e.getMessage());
            }
        } else {
            info.put("releaseContent", "No release file found");
        }

        return info;
    }

    /**
     * Validates that the current environment meets the requirements for native image building.
     * <p>
     * This method checks if the current JVM is capable of native image building (GraalVM or Mandrel)
     * and if the native-image tool is available. If either condition is not met, it throws a
     * detailed GradleException with information about the current environment and instructions
     * on how to set up the environment correctly.
     *
     * @return true if the environment is valid for native image building
     * @throws GradleException if the environment does not meet the requirements for native image building,
     *                        with detailed information about the current environment and how to fix it
     */
    public boolean validateNativeEnvironment() {
        boolean nativeCapableJVM = isNativeCapableJVM();
        boolean nativeImageAvailable = isNativeImageAvailable();

        if (!nativeCapableJVM || !nativeImageAvailable) {
            String jvmType = getNativeJVMType();
            Map<String, Object> detailedInfo = getDetailedJVMInfo();

            StringBuilder errorMessage = new StringBuilder();
            errorMessage.append("\n        ❌ NATIVE BUILD REQUIREMENTS NOT MET\n");
            errorMessage.append("\n        This project is configured for NATIVE-ONLY builds and requires:\n");
            errorMessage.append("\n        ");
            errorMessage.append(!nativeCapableJVM ? "❌ Native-capable JVM: Not detected" : "✅ Native-capable JVM: " + jvmType + " detected");
            errorMessage.append("\n        ");
            errorMessage.append(!nativeImageAvailable ? "❌ Native Image: Not available" : "✅ Native Image: Available");
            errorMessage.append("\n");
            errorMessage.append("\n        Current Environment:");
            errorMessage.append("\n        - Java Vendor: ").append(detailedInfo.get("vendor"));
            errorMessage.append("\n        - Java Runtime: ").append(detailedInfo.get("runtime"));
            errorMessage.append("\n        - Java VM Name: ").append(detailedInfo.get("vmName"));
            errorMessage.append("\n        - Java VM Version: ").append(detailedInfo.get("vmVersion"));
            errorMessage.append("\n        - Java Version: ").append(detailedInfo.get("javaVersion"));
            errorMessage.append("\n        - Java Home: ").append(detailedInfo.get("javaHome"));
            errorMessage.append("\n        - Mandrel in Path: ").append(detailedInfo.get("mandrelInPath"));
            errorMessage.append("\n");
            errorMessage.append("\n        Release File Content:");
            errorMessage.append("\n        ").append(detailedInfo.get("releaseContent"));
            errorMessage.append("\n");
            errorMessage.append("\n        Detection Results:");
            errorMessage.append("\n        - isGraalVM(): ").append(isGraalVM());
            errorMessage.append("\n        - isMandrel(): ").append(isMandrel());
            errorMessage.append("\n        - isNativeCapableJVM(): ").append(isNativeCapableJVM());
            errorMessage.append("\n        - isNativeImageAvailable(): ").append(isNativeImageAvailable());
            errorMessage.append("\n");
            errorMessage.append("\n        To fix this:");
            errorMessage.append("\n        1. Install GraalVM from https://www.graalvm.org/downloads/");
            errorMessage.append("\n           OR");
            errorMessage.append("\n           Install Mandrel from https://github.com/graalvm/mandrel/releases");
            errorMessage.append("\n        2. Set JAVA_HOME to point to GraalVM/Mandrel installation");
            errorMessage.append("\n        3. Install native-image: gu install native-image (GraalVM) or use built-in (Mandrel)");
            errorMessage.append("\n        4. Verify with: native-image --version");

            throw new GradleException(errorMessage.toString());
        }

        return true;
    }
}
