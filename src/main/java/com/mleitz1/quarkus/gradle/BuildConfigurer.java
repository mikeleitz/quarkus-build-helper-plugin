package com.mleitz1.quarkus.gradle;

import org.gradle.api.Project;

/**
 * Handles the two configuration axes this plugin cares about:
 * 1. buildType: native or jar
 * 2. containerBuild: true (container) or false (use machine's Graal/Mandrel)
 *
 * When these Gradle properties are supplied, the plugin forces the corresponding
 * Quarkus behavior and produces troubleshooting-oriented output.
 */
public class BuildConfigurer {

    private static final String BUILD_TYPE_PROP = "quarkus-build-helper-plugin.configure.buildType";
    private static final String CONTAINER_BUILD_PROP = "quarkus-build-helper-plugin.configure.containerBuild";

    /**
     * Represents what the user explicitly requested via Gradle properties.
     */
    public static class RequestedConfig {
        public final String buildType;        // "native", "jar", or null if not requested
        public final Boolean containerBuild;  // true, false, or null if not requested

        public RequestedConfig(String buildType, Boolean containerBuild) {
            this.buildType = buildType;
            this.containerBuild = containerBuild;
        }

        public boolean isNative() {
            return "native".equals(buildType);
        }

        public boolean isJar() {
            return "jar".equals(buildType);
        }

        public boolean hasBuildType() {
            return buildType != null;
        }

        public boolean hasContainerPreference() {
            return containerBuild != null;
        }

        /**
         * Human description of the requested mode, useful for troubleshooting output.
         */
        public String describe() {
            if (!hasBuildType()) {
                return "no explicit build type requested";
            }
            if (isNative()) {
                if (Boolean.TRUE.equals(containerBuild)) {
                    return "native (container build)";
                } else if (Boolean.FALSE.equals(containerBuild)) {
                    return "native (local machine Graal/Mandrel)";
                }
                return "native";
            } else if (isJar()) {
                return "jar (uber-jar)";
            }
            return buildType;
        }

        /**
         * Returns a short label for what local vs container means in this request.
         */
        public String getLocationLabel() {
            if (!isNative() || !hasContainerPreference()) {
                return "";
            }
            return Boolean.TRUE.equals(containerBuild) ? "container" : "local";
        }
    }

    public RequestedConfig getRequestedConfig(Project project) {
        String bt = getBuildType(project);
        Boolean cb = getContainerBuild(project);
        if (bt == null && cb == null) {
            return null;
        }
        return new RequestedConfig(bt, cb);
    }

    public boolean isPluginGoingToConfigureNative(Project project) {
        RequestedConfig cfg = getRequestedConfig(project);
        return cfg != null && cfg.isNative();
    }

    public boolean isPluginGoingToConfigureJar(Project project) {
        RequestedConfig cfg = getRequestedConfig(project);
        return cfg != null && cfg.isJar();
    }

    public void configureBuild(Project project) {
        RequestedConfig cfg = getRequestedConfig(project);
        if (cfg == null) {
            return;
        }

        // Apply build type settings
        if (cfg.isJar()) {
            System.out.println("quarkus-build-helper: CONFIGURED FOR JAR (uber-jar)");
            System.setProperty("quarkus.package.jar.enabled", "true");
            System.setProperty("quarkus.package.jar.type", "uber-jar");
            System.setProperty("quarkus.native.enabled", "false");
        } else if (cfg.isNative()) {
            String loc = cfg.hasContainerPreference()
                ? (Boolean.TRUE.equals(cfg.containerBuild) ? " (container)" : " (local Graal)")
                : "";
            System.out.println("quarkus-build-helper: CONFIGURED FOR NATIVE" + loc);

            System.setProperty("quarkus.package.jar.enabled", "false");
            System.setProperty("quarkus.native.enabled", "true");
        }

        // Apply container-build preference if the user asked for one.
        // This is mainly relevant for native, but we honor it if provided.
        if (cfg.hasContainerPreference()) {
            System.setProperty("quarkus.native.container-build", String.valueOf(cfg.containerBuild));
            // Explicitly turn off remote container unless user somehow wants it; keep simple.
            // Users who want remote can set quarkus.native.remote-container-build themselves.
            if (!Boolean.TRUE.equals(System.getProperty("quarkus.native.remote-container-build"))) {
                // Only set false if not already explicitly true via other means; avoid fighting user.
            }
        }
    }

    private String getBuildType(Project project) {
        if (!project.hasProperty(BUILD_TYPE_PROP)) {
            return null;
        }
        String raw = project.findProperty(BUILD_TYPE_PROP).toString().trim().toLowerCase();
        if (raw.startsWith("native")) {
            return "native";
        }
        if (raw.startsWith("jar")) {
            return "jar";
        }
        System.out.println("quarkus-build-helper: Unknown value for " + BUILD_TYPE_PROP + "='" + raw + "'. Use 'native' or 'jar'.");
        return null;
    }

    private Boolean getContainerBuild(Project project) {
        if (!project.hasProperty(CONTAINER_BUILD_PROP)) {
            return null;
        }
        String raw = project.findProperty(CONTAINER_BUILD_PROP).toString().trim().toLowerCase();
        if ("true".equals(raw) || "yes".equals(raw) || "container".equals(raw)) {
            return true;
        }
        if ("false".equals(raw) || "no".equals(raw) || "local".equals(raw)) {
            return false;
        }
        System.out.println("quarkus-build-helper: Unknown value for " + CONTAINER_BUILD_PROP + "='" + raw + "'. Use 'true'/'false' (or 'container'/'local').");
        return null;
    }
}
