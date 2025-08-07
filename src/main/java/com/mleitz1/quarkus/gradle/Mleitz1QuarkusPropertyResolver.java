package com.mleitz1.quarkus.gradle;

import org.gradle.api.Project;

/**
 * Utility class for resolving Quarkus properties from both system properties and Gradle properties.
 * This class provides methods to check the status of various Quarkus native build properties
 * and handles potential mismatches between system and Gradle property values.
 */
public class Mleitz1QuarkusPropertyResolver {

    private final Project project;

    /**
     * Creates a new instance of the property resolver.
     *
     * @param project the Gradle project
     */
    public Mleitz1QuarkusPropertyResolver(Project project) {
        this.project = project;
    }

    /**
     * Gets the status of the quarkus.native.enabled property.
     *
     * @return a status string indicating if the property is enabled, disabled, or has a mismatch
     */
    public String getQuarkusNativeEnabledStatus() {
        return getPropertyStatus("quarkus.native.enabled");
    }

    /**
     * Gets the status of the quarkus.native.container-build property.
     *
     * @return a status string indicating if the property is enabled, disabled, or has a mismatch
     */
    public String getQuarkusNativeContainerBuildStatus() {
        return getPropertyStatus("quarkus.native.container-build");
    }

    /**
     * Gets the status of the quarkus.package.jar.enabled property.
     *
     * @return a status string indicating if the property is enabled, disabled, or has a mismatch
     */
    public String getQuarkusPackageJarEnabledStatus() {
        return getPropertyStatus("quarkus.package.jar.enabled");
    }

    /**
     * Gets the status of the quarkus.native.remote-container-build property.
     *
     * @return a status string indicating if the property is enabled, disabled, or has a mismatch
     */
    public String getQuarkusNativeRemoteContainerBuildStatus() {
        return getPropertyStatus("quarkus.native.remote-container-build");
    }

    /**
     * Gets the status of a property by checking both system and Gradle properties.
     *
     * @param propertyName the name of the property to check
     * @return a status string indicating if the property is enabled, disabled, or has a mismatch
     */
    protected String getPropertyStatus(String propertyName) {
        Boolean systemPropertyValue = getSystemPropertyValue(propertyName);
        Boolean gradlePropertyValue = getGradlePropertyValue(propertyName);

        if (systemPropertyValue == null) {
            // System property is undefined, use Gradle property
            return gradlePropertyValue ? "✅ Enabled" : "❌ Disabled";
        } else {
            // System property is defined, check for mismatch
            if (systemPropertyValue.equals(gradlePropertyValue)) {
                return systemPropertyValue ? "✅ Enabled" : "❌ Disabled";
            } else {
                return String.format("⚠️ Mismatch: System=%s, Gradle=%s",
                    formatBooleanValue(systemPropertyValue),
                    formatBooleanValue(gradlePropertyValue));
            }
        }
    }

    /**
     * Formats a boolean value as "enabled" or "disabled".
     *
     * @param value the boolean value to format
     * @return a string representation of the boolean value
     */
    protected String formatBooleanValue(Boolean value) {
        return value ? "enabled" : "disabled";
    }

    /**
     * Gets the value of a system property.
     *
     * @param propertyName the name of the property to get
     * @return the boolean value of the property, or null if not defined
     */
    protected Boolean getSystemPropertyValue(String propertyName) {
        String value = System.getProperty(propertyName);
        if (value == null) {
            return null;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * Gets the value of a Gradle property.
     *
     * @param propertyName the name of the property to get
     * @return the boolean value of the property, defaulting to false if not defined
     */
    protected Boolean getGradlePropertyValue(String propertyName) {
        Object value = project.findProperty(propertyName);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    /**
     * Gets the string value of a Gradle property.
     *
     * @param propertyName the name of the property to get
     * @return the string value of the property, or null if not defined
     */
    protected String getGradlePropertyStringValue(String propertyName) {
        Object value = project.findProperty(propertyName);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    /**
     * Gets the value of the quarkus.native.builder-image property.
     *
     * @return the value of the property, or a default value if not defined
     */
    public String getQuarkusNativeBuilderImage() {
        String value = getGradlePropertyStringValue("quarkus.native.builder-image");
        return value != null ? value : "Not specified";
    }

    /**
     * Gets the value of the quarkus.native.native-image-xmx property.
     *
     * @return the value of the property, or a default value if not defined
     */
    public String getQuarkusNativeNativeImageXmx() {
        String value = getGradlePropertyStringValue("quarkus.native.native-image-xmx");
        return value != null ? value : "Not specified";
    }

    /**
     * Gets the value of the quarkus.native.additionalBuildArgs property.
     *
     * @return the value of the property, or null if not defined
     */
    public String getQuarkusNativeAdditionalBuildArgs() {
        return getGradlePropertyStringValue("quarkus.native.additionalBuildArgs");
    }

    /**
     * Gets the status of a string property by checking both system and Gradle properties.
     *
     * @param propertyName the name of the property to check
     * @return a status string indicating the property value or a mismatch
     */
    protected String getStringPropertyStatus(String propertyName) {
        String systemPropertyValue = System.getProperty(propertyName);
        String gradlePropertyValue = getGradlePropertyStringValue(propertyName);

        if (systemPropertyValue == null || systemPropertyValue.isEmpty()) {
            // System property is undefined, use Gradle property
            return gradlePropertyValue != null ? gradlePropertyValue : "not set";
        } else {
            // System property is defined, check for mismatch
            if (systemPropertyValue.equals(gradlePropertyValue)) {
                return systemPropertyValue;
            } else {
                return String.format("⚠️ Mismatch: System=%s, Gradle=%s",
                    systemPropertyValue,
                    gradlePropertyValue != null ? gradlePropertyValue : "not set");
            }
        }
    }
}
