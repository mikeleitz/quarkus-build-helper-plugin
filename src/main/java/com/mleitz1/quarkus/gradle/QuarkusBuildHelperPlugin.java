package com.mleitz1.quarkus.gradle;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Quarkus Build Helper Gradle plugin.
 *
 * Primary purpose: allow controlling the two key dimensions via -P properties early in the build:
 *   1. buildType = native | jar
 *   2. containerBuild = true (container) | false (use the machine's Graal/Mandrel)
 *
 * When you request a combination (e.g. native + local), the plugin configures Quarkus
 * (via System properties) and produces troubleshooting output to explain why it may
 * or may not be succeeding in your environment.
 *
 * The diagnostic tasks are designed to answer: "I asked for X, why didn't I get it?"
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
     * Property reader for accessing Quarkus-specific properties (used for status display).
     */
    QuarkusPropertyReader propertyResolver;

    NativeImageUtil nativeImageUtil = new NativeImageUtil();

    BuildConfigurer buildConfigurer = new BuildConfigurer();

    /**
     * The configuration the user explicitly requested via -P properties, if any.
     * This drives both the forced settings and the troubleshooting output.
     */
    BuildConfigurer.RequestedConfig requestedConfig;

    boolean isConfiguring() {
        return requestedConfig != null;
    }

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
        propertyResolver = new QuarkusPropertyReader(project);

        // Read what (if anything) the user requested via -P properties.
        // This is the only reliable early hook for controlling native vs jar and container vs local.
        requestedConfig = buildConfigurer.getRequestedConfig(project);

        // Minimal exposure. The resolver can be used from build scripts if needed for custom logic.
        project.getExtensions().getExtraProperties().set("quarkusBuildPropertyResolver", propertyResolver);
        project.getExtensions().getExtraProperties().set("isQuarkusPluginApplied", (java.util.function.Supplier<Boolean>) () -> project.getPlugins().hasPlugin(QUARKUS_PLUGIN_ID));

        // Apply the requested configuration (sets System properties that Quarkus sees early).
        // Uses -P because -D system properties were not effective for the user at the right time.
        if (isConfiguring()) {
            buildConfigurer.configureBuild(project);
        }

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

        // Register a task to display requested config + quick troubleshooting
        tasks.register("displayQuarkusBuildOverview", task -> {
            task.setGroup(QUARKUS_DIAGNOSTICS_TASK_GROUP);
            task.setDescription("Shows what was requested (native/jar + container/local) and basic reality check for troubleshooting");

            task.doLast(t -> {
                printTroubleshootingOverview(project);
            });
        });

        // Register a task to display detailed troubleshooting info focused on the two use cases
        tasks.register("displayQuarkusBuildDetail", task -> {
            task.setGroup(QUARKUS_DIAGNOSTICS_TASK_GROUP);
            task.setDescription("Detailed environment info to troubleshoot why native+local or other requests may be failing");

            task.doLast(t -> {
                printTroubleshootingDetail(project);
            });
        });

        // Register a task to verify native executable after build (manual or auto-attached only for local native requests)
        tasks.register("validateNativeExecutable", task -> {
            task.setGroup(QUARKUS_DIAGNOSTICS_TASK_GROUP);
            task.setDescription("Verifies that the native executable was produced (useful after a successful local native build)");

            // Only pull in quarkusBuild automatically if this makes sense for the request
            if (requestedConfig != null && requestedConfig.isNative()) {
                task.dependsOn("quarkusBuild");
            }

            task.doLast(t -> {
                String nativeExecutablePath = project.getProjectDir().getAbsolutePath() + "/build/" + project.getName() + "-" + project.getVersion() + "-runner";
                File nativeExecutable = new File(nativeExecutablePath);
                System.out.println("\n=========================================================");
                System.out.println("QUARKUS BUILD HELPER - NATIVE EXECUTABLE CHECK");
                System.out.println("=========================================================");

                if (nativeExecutable.exists()) {
                    System.out.println("✅ Native executable created successfully:");
                    System.out.println("   📁 Location: " + nativeExecutable.getAbsolutePath());
                    System.out.println("   📏 Size: " + String.format("%.2f MB", nativeExecutable.length() / 1024.0 / 1024.0));
                    System.out.println("   🚀 Run with: chmod +x " + nativeExecutablePath + " && " + nativeExecutablePath);
                } else {
                    System.out.println("❌ Native executable not found at expected location");
                    System.out.println("   Missing: " + nativeExecutablePath);
                    System.out.println("   This often means the native build step did not run or failed silently.");
                    if (requestedConfig != null && requestedConfig.isNative() && Boolean.FALSE.equals(requestedConfig.containerBuild)) {
                        System.out.println("   You requested local native — double-check the environment output above.");
                    }
                }
            });
        });

        // Register a task to check the native build environment - now request-aware
        tasks.register("checkNativeEnvironment", task -> {
            task.setGroup(QUARKUS_DIAGNOSTICS_TASK_GROUP);
            task.setDescription("Diagnoses the environment specifically for the requested build (native local vs container etc.)");

            task.doLast(t -> {
                printTroubleshootingDetail(project);

                // If the user explicitly requested local native, give a very direct diagnosis
                if (requestedConfig != null && requestedConfig.isNative() && Boolean.FALSE.equals(requestedConfig.containerBuild)) {
                    System.out.println("--- SPECIFIC DIAGNOSIS FOR YOUR REQUEST: native + local ---");
                    if (!isNativeCapableJVM()) {
                        System.out.println("❌ No GraalVM or Mandrel detected as the running JVM.");
                        System.out.println("   The JVM in your Gradle toolchain / JAVA_HOME / PATH is: " + getNativeJVMType());
                        System.out.println("   This is the most common reason local native builds fail when you ask for container=false.");
                    } else if (!isNativeImageAvailable()) {
                        System.out.println("❌ Graal/Mandrel detected but native-image tool not found.");
                        System.out.println("   Make sure the 'native-image' executable is in the bin dir or on PATH.");
                    } else {
                        System.out.println("✅ Local native capability detected. If quarkusBuild is still failing,");
                        System.out.println("   check memory settings, additionalBuildArgs, or Quarkus version incompatibilities.");
                    }
                    System.out.println("---------------------------------------------------------------");
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

            // Only auto-wire diagnostic output when the user is actively using the configure properties.
            // This keeps the plugin non-surprising when it's only on the classpath for occasional troubleshooting.
            if (isConfiguring() && project.getPlugins().hasPlugin(QUARKUS_PLUGIN_ID)) {
                Task quarkusGenerateCode = tasks.findByName("quarkusGenerateCode");
                Task displayOverview = tasks.findByName("displayQuarkusBuildOverview");
                if (displayOverview != null && quarkusGenerateCode != null) {
                    quarkusGenerateCode.dependsOn(displayOverview);
                }

                Task quarkusBuild = tasks.findByName("quarkusBuild");
                Task displayDetail = tasks.findByName("displayQuarkusBuildDetail");
                if (displayDetail != null && quarkusBuild != null) {
                    quarkusBuild.dependsOn(displayDetail);
                }
            }

            // Only auto-attach the native executable validator when the user explicitly asked for a (local) native build.
            // Jar builds and container builds don't need this the same way.
            Task buildTask = tasks.findByName("build");
            Task validateNativeExecutable = tasks.findByName("validateNativeExecutable");
            if (buildTask != null && validateNativeExecutable != null) {
                if (requestedConfig != null && requestedConfig.isNative() && Boolean.FALSE.equals(requestedConfig.containerBuild)) {
                    buildTask.finalizedBy(validateNativeExecutable);
                }
            }
        });
    }

    /**
     * Prints focused troubleshooting output for the two supported use cases.
     * Emphasizes "what you asked for" vs "what the environment actually has".
     */
    private void printTroubleshootingOverview(Project project) {
        System.out.println("\n=========================================================");
        System.out.println("QUARKUS BUILD HELPER - CONFIG + TROUBLESHOOTING");
        System.out.println("=========================================================");

        if (!isConfiguring()) {
            System.out.println("No build configuration requested via -P flags.");
            System.out.println("Use -Pquarkus-build-helper-plugin.configure.buildType=native|jar");
            System.out.println("and optionally -Pquarkus-build-helper-plugin.configure.containerBuild=true|false");
            System.out.println("=========================================================\n");
            return;
        }

        System.out.println("REQUESTED: " + requestedConfig.describe());

        // Show what we forced
        if (requestedConfig.isNative()) {
            System.out.println("  - quarkus.native.enabled = true");
            System.out.println("  - quarkus.package.jar.enabled = false");
            if (requestedConfig.hasContainerPreference()) {
                System.out.println("  - quarkus.native.container-build = " + requestedConfig.containerBuild);
            }
        } else if (requestedConfig.isJar()) {
            System.out.println("  - quarkus.package.jar.enabled = true");
            System.out.println("  - quarkus.package.jar.type = uber-jar");
            System.out.println("  - quarkus.native.enabled = false");
        }

        System.out.println("");

        // Reality check tailored to the request
        if (requestedConfig.isNative() && Boolean.FALSE.equals(requestedConfig.containerBuild)) {
            // User wants native on the machine's Graal
            boolean hasLocalNative = isNativeCapableJVM() && isNativeImageAvailable();
            String jvmType = getNativeJVMType();
            String nativeBin = nativeImageUtil.findNativeImageBinary(project);

            System.out.println("LOCAL NATIVE REALITY CHECK (container=false):");
            System.out.println("  Current JVM type: " + jvmType);
            System.out.println("  Native-image capable (Graal/Mandrel): " + (isNativeCapableJVM() ? "✅ " + jvmType : "❌ No"));
            System.out.println("  native-image binary visible: " + (nativeBin != null ? "✅ " + nativeBin : "❌ Not found"));
            System.out.println("  (Searched: Gradle toolchain, JAVA_HOME/bin, PATH)");

            if (!hasLocalNative) {
                System.out.println("");
                System.out.println("❌ PROBLEM: You requested NATIVE using the machine's Graal instance,");
                System.out.println("   but this JVM does not appear to be a GraalVM or Mandrel with native-image.");
                System.out.println("   The build will likely fail or fall back incorrectly.");
                System.out.println("");
                System.out.println("   What you can do:");
                System.out.println("   - Switch to container build: add -Pquarkus-build-helper-plugin.configure.containerBuild=true");
                System.out.println("   - Point Gradle toolchain or JAVA_HOME at a GraalVM/Mandrel install");
                System.out.println("   - Ensure 'native-image' is on your PATH from a proper Graal install");
            } else {
                System.out.println("");
                System.out.println("✅ Environment looks capable for local native build.");
            }

        } else if (requestedConfig.isNative() && Boolean.TRUE.equals(requestedConfig.containerBuild)) {
            System.out.println("CONTAINER NATIVE REQUESTED:");
            System.out.println("  Quarkus will use a builder image inside a container (Docker/Podman).");
            System.out.println("  Local Graal/Mandrel on the machine is not required.");
            System.out.println("  Ensure your container runtime is running and can pull the builder image.");

            // Light hint
            boolean dockerish = isCommandAvailable("docker") || isCommandAvailable("podman");
            System.out.println("  Container runtime detected in PATH: " + (dockerish ? "✅ (docker or podman found)" : "❓ (no docker/podman in PATH - may still work if in PATH for daemon)"));

        } else if (requestedConfig.isJar()) {
            System.out.println("JAR BUILD REQUESTED:");
            System.out.println("  No special JVM requirements. Standard JDK is sufficient.");
            System.out.println("  Current JVM: " + getNativeJVMType() + " @ " + System.getProperty("java.home"));
        }

        System.out.println("=========================================================\n");
    }

    private void printTroubleshootingDetail(Project project) {
        System.out.println("\n=========================================================");
        System.out.println("QUARKUS BUILD HELPER - DETAILED TROUBLESHOOT");
        System.out.println("=========================================================");

        System.out.println("Toolchain / Java:");
        System.out.println("  Java Home (toolchain): " + getJavaHome(project));
        System.out.println("  Java Binary (toolchain): " + getJavaJdkBinary(project));
        System.out.println("  Effective JAVA_HOME (process): " + System.getProperty("java.home"));
        System.out.println("  Native Image candidate: " + nativeImageUtil.findNativeImageBinary(project));
        System.out.println("");

        if (isConfiguring()) {
            System.out.println("Your request: " + requestedConfig.describe());
        }

        System.out.println("Quarkus-relevant properties (after any configuration by this plugin):");
        // When the plugin configured via System.setProperty, the resolver may report "mismatch"
        // because the original gradle property (if any) differs. We surface the System value as truth here.
        System.out.println("  quarkus.native.enabled: " + effectivePropStatus("quarkus.native.enabled"));
        System.out.println("  quarkus.native.container-build: " + effectivePropStatus("quarkus.native.container-build"));
        System.out.println("  quarkus.package.jar.enabled: " + effectivePropStatus("quarkus.package.jar.enabled"));
        System.out.println("  quarkus.native.remote-container-build: " + propertyResolver.getQuarkusNativeRemoteContainerBuildStatus());
        System.out.println("  builder-image: " + propertyResolver.getQuarkusNativeBuilderImage());
        System.out.println("  native-image-xmx: " + propertyResolver.getQuarkusNativeNativeImageXmx());
        System.out.println("");

        if (requestedConfig != null && requestedConfig.isNative() && Boolean.FALSE.equals(requestedConfig.containerBuild)) {
            System.out.println("LOCAL NATIVE CAPABILITY:");
            System.out.println("  GraalVM detected: " + (isGraalVM() ? "✅" : "❌"));
            System.out.println("  Mandrel detected: " + (isMandrel() ? "✅" : "❌"));
            System.out.println("  Native capable JVM: " + (isNativeCapableJVM() ? "✅ " + getNativeJVMType() : "❌"));
            System.out.println("  native-image available: " + (isNativeImageAvailable() ? "✅" : "❌"));
            System.out.println("");
            if (!isNativeCapableJVM() || !isNativeImageAvailable()) {
                System.out.println("This is why a local native build will fail for your request.");
            }
        }

        System.out.println("Native JVM type seen by this process: " + getNativeJVMType());
        System.out.println("=========================================================\n");
    }

    /**
     * Very light check if a command name appears to be available in PATH.
     * Used only for helpful hints (docker/podman), not hard requirements.
     */
    private boolean isCommandAvailable(String cmd) {
        String path = System.getenv("PATH");
        if (path == null) return false;
        String exe = System.getProperty("os.name").toLowerCase().contains("windows") ? cmd + ".exe" : cmd;
        for (String dir : path.split(java.io.File.pathSeparator)) {
            if (new java.io.File(dir, exe).canExecute()) return true;
        }
        return false;
    }

    /**
     * Reports the effective value for a key Quarkus prop, preferring System property
     * (which the plugin sets when configuring) and noting when this plugin forced it.
     */
    private String effectivePropStatus(String name) {
        String sys = System.getProperty(name);
        if (sys != null) {
            boolean val = Boolean.parseBoolean(sys);
            String note = isConfiguring() ? " (set by build-helper)" : "";
            return (val ? "✅ true" : "❌ false") + note;
        }
        return propertyResolver.getPropertyStatus(name);  // fallback
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

    // NOTE: Old heavy "validateNativeEnvironment" that threw GradleException has been removed.
    // Troubleshooting is now done via the diagnostic tasks (displayQuarkusBuildOverview, displayQuarkusBuildDetail, checkNativeEnvironment)
    // which explain exactly why the user's requested combination (e.g. native + container=false) is not working,
    // without forcibly failing unrelated builds.
}
