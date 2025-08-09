package com.mleitz1.quarkus.gradle;

import org.gradle.api.Project;

public class BuildConfigurer {
    private static final String PROPERTY_NAME = "quarkus-build-helper-plugin.configure.buildType";

    public boolean isPluginGoingToConfigureNative(Project project) {
        String buildType = getBuildType(project);
        if (buildType == null) {
            return false;
        }

        if (buildType.equalsIgnoreCase("native")) {
            return true;
        }

        return false;
    }

    public boolean isPluginGoingToConfigureJar(Project project) {
        String buildType = getBuildType(project);
        if (buildType == null) {
            return false;
        }

        if (buildType.equalsIgnoreCase("jar")) {
            return true;
        }

        return false;
    }

    public void configureBuild(Project project) {
        String buildType = getBuildType(project);
        if (buildType == null) {
            return;
        }

        if (buildType.equalsIgnoreCase("jar")) {
            System.out.println("quarkus-build-helper CONFIGURED BUILDING JAR");

            System.setProperty("quarkus.package.jar.enabled", "true");
            System.setProperty("quarkus.package.jar.type", "uber-jar");
            System.setProperty("quarkus.native.enabled", "false");
        } else if (buildType.equalsIgnoreCase("native")) {
            System.out.println("quarkus-build-helper CONFIGURED BUILDING NATIVE");

            System.setProperty("quarkus.package.jar.enabled", "false");
            System.setProperty("quarkus.native.enabled", "true");
        }
    }

    private String getBuildType(Project project) {
        if (!project.hasProperty(PROPERTY_NAME)) {
            return null;
        }

        String buildType = project.findProperty(PROPERTY_NAME).toString().toLowerCase();
        if (!buildType.startsWith("jar") && !buildType.startsWith("native")) {
            System.out.println("Unknown quarkus-build-helper-plugin property value buildType='" + buildType + "'. Only values supported are jar and native");
            return null;
        }

        return buildType;
    }
}
