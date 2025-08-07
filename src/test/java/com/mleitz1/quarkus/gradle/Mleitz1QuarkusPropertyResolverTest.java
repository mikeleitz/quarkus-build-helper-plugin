package com.mleitz1.quarkus.gradle;

import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for Mleitz1QuarkusPropertyResolver.
 * Uses a test-specific subclass to avoid mocking the Project interface.
 */
public class Mleitz1QuarkusPropertyResolverTest {

    private TestablePropertyResolver resolver;
    private Map<String, Object> testProperties;

    @BeforeEach
    void setUp() {
        testProperties = new HashMap<>();
        resolver = new TestablePropertyResolver(testProperties);
    }

    @AfterEach
    void tearDown() {
        // Clear any system properties that were set during tests
        System.clearProperty("quarkus.native.enabled");
        System.clearProperty("quarkus.native.container-build");
        System.clearProperty("quarkus.package.jar.enabled");
        System.clearProperty("quarkus.native.remote-container-build");
        System.clearProperty("quarkus.test.property");
    }

    @Test
    void testGetQuarkusNativeEnabledStatus_Enabled() {
        // Setup
        testProperties.put("quarkus.native.enabled", true);

        // Test
        String result = resolver.getQuarkusNativeEnabledStatus();

        // Verify
        assertEquals("✅ Enabled", result);
    }

    @Test
    void testGetQuarkusNativeEnabledStatus_Disabled() {
        // Setup
        testProperties.put("quarkus.native.enabled", false);

        // Test
        String result = resolver.getQuarkusNativeEnabledStatus();

        // Verify
        assertEquals("❌ Disabled", result);
    }

    @Test
    void testGetQuarkusNativeEnabledStatus_SystemOverride() {
        // Setup
        testProperties.put("quarkus.native.enabled", false);
        System.setProperty("quarkus.native.enabled", "true");

        // Test
        String result = resolver.getQuarkusNativeEnabledStatus();

        // Verify
        assertEquals("⚠️ Mismatch: System=enabled, Gradle=disabled", result);
    }

    @Test
    void testGetQuarkusNativeContainerBuildStatus_Enabled() {
        // Setup
        testProperties.put("quarkus.native.container-build", true);

        // Test
        String result = resolver.getQuarkusNativeContainerBuildStatus();

        // Verify
        assertEquals("✅ Enabled", result);
    }

    @Test
    void testGetQuarkusNativeContainerBuildStatus_Disabled() {
        // Setup
        testProperties.put("quarkus.native.container-build", false);

        // Test
        String result = resolver.getQuarkusNativeContainerBuildStatus();

        // Verify
        assertEquals("❌ Disabled", result);
    }

    @Test
    void testGetQuarkusPackageJarEnabledStatus_Enabled() {
        // Setup
        testProperties.put("quarkus.package.jar.enabled", true);

        // Test
        String result = resolver.getQuarkusPackageJarEnabledStatus();

        // Verify
        assertEquals("✅ Enabled", result);
    }

    @Test
    void testGetQuarkusPackageJarEnabledStatus_Disabled() {
        // Setup
        testProperties.put("quarkus.package.jar.enabled", false);

        // Test
        String result = resolver.getQuarkusPackageJarEnabledStatus();

        // Verify
        assertEquals("❌ Disabled", result);
    }

    @Test
    void testGetQuarkusNativeRemoteContainerBuildStatus_Enabled() {
        // Setup
        testProperties.put("quarkus.native.remote-container-build", true);

        // Test
        String result = resolver.getQuarkusNativeRemoteContainerBuildStatus();

        // Verify
        assertEquals("✅ Enabled", result);
    }

    @Test
    void testGetQuarkusNativeRemoteContainerBuildStatus_Disabled() {
        // Setup
        testProperties.put("quarkus.native.remote-container-build", false);

        // Test
        String result = resolver.getQuarkusNativeRemoteContainerBuildStatus();

        // Verify
        assertEquals("❌ Disabled", result);
    }

    @Test
    void testGetQuarkusNativeBuilderImage_PropertySet() {
        // Setup
        testProperties.put("quarkus.native.builder-image", "quay.io/quarkus/ubi-quarkus-native-image:22.3-java17");

        // Test
        String result = resolver.getQuarkusNativeBuilderImage();

        // Verify
        assertEquals("quay.io/quarkus/ubi-quarkus-native-image:22.3-java17", result);
    }

    @Test
    void testGetQuarkusNativeBuilderImage_PropertyNotSet() {
        // Test
        String result = resolver.getQuarkusNativeBuilderImage();

        // Verify
        assertEquals("Not specified", result);
    }

    @Test
    void testGetQuarkusNativeNativeImageXmx_PropertySet() {
        // Setup
        testProperties.put("quarkus.native.native-image-xmx", "4g");

        // Test
        String result = resolver.getQuarkusNativeNativeImageXmx();

        // Verify
        assertEquals("4g", result);
    }

    @Test
    void testGetQuarkusNativeNativeImageXmx_PropertyNotSet() {
        // Test
        String result = resolver.getQuarkusNativeNativeImageXmx();

        // Verify
        assertEquals("Not specified", result);
    }

    @Test
    void testGetQuarkusNativeAdditionalBuildArgs_PropertySet() {
        // Setup
        testProperties.put("quarkus.native.additionalBuildArgs", "--no-fallback");

        // Test
        String result = resolver.getQuarkusNativeAdditionalBuildArgs();

        // Verify
        assertEquals("--no-fallback", result);
    }

    @Test
    void testGetQuarkusNativeAdditionalBuildArgs_PropertyNotSet() {
        // Test
        String result = resolver.getQuarkusNativeAdditionalBuildArgs();

        // Verify
        assertNull(result);
    }

    @Test
    void testPropertyStatus_BothPropertiesTrue() {
        // Setup
        testProperties.put("quarkus.test.property", true);
        System.setProperty("quarkus.test.property", "true");

        // Test
        String result = resolver.getPropertyStatus("quarkus.test.property");

        // Verify
        assertEquals("✅ Enabled", result);
    }

    @Test
    void testPropertyStatus_BothPropertiesFalse() {
        // Setup
        testProperties.put("quarkus.test.property", false);
        System.setProperty("quarkus.test.property", "false");

        // Test
        String result = resolver.getPropertyStatus("quarkus.test.property");

        // Verify
        assertEquals("❌ Disabled", result);
    }

    @Test
    void testPropertyStatus_SystemTrueGradleFalse() {
        // Setup
        testProperties.put("quarkus.test.property", false);
        System.setProperty("quarkus.test.property", "true");

        // Test
        String result = resolver.getPropertyStatus("quarkus.test.property");

        // Verify
        assertEquals("⚠️ Mismatch: System=enabled, Gradle=disabled", result);
    }

    @Test
    void testPropertyStatus_SystemFalseGradleTrue() {
        // Setup
        testProperties.put("quarkus.test.property", true);
        System.setProperty("quarkus.test.property", "false");

        // Test
        String result = resolver.getPropertyStatus("quarkus.test.property");

        // Verify
        assertEquals("⚠️ Mismatch: System=disabled, Gradle=enabled", result);
    }

    @Test
    void testPropertyStatus_SystemNullGradleTrue() {
        // Setup
        testProperties.put("quarkus.test.property", true);

        // Test
        String result = resolver.getPropertyStatus("quarkus.test.property");

        // Verify
        assertEquals("✅ Enabled", result);
    }

    @Test
    void testPropertyStatus_SystemNullGradleFalse() {
        // Setup
        testProperties.put("quarkus.test.property", false);

        // Test
        String result = resolver.getPropertyStatus("quarkus.test.property");

        // Verify
        assertEquals("❌ Disabled", result);
    }

    @Test
    void testPropertyStatus_SystemNullGradleNull() {
        // Test
        String result = resolver.getPropertyStatus("quarkus.test.property");

        // Verify
        assertEquals("❌ Disabled", result);
    }

    /**
     * A testable subclass of Mleitz1QuarkusPropertyResolver that overrides
     * the methods that interact with the Project interface.
     */
    private static class TestablePropertyResolver extends Mleitz1QuarkusPropertyResolver {
        private final Map<String, Object> properties;

        public TestablePropertyResolver(Map<String, Object> properties) {
            super(null); // Pass null as we're overriding the methods that use Project
            this.properties = properties;
        }

        @Override
        protected Boolean getGradlePropertyValue(String propertyName) {
            Object value = properties.get(propertyName);
            if (value == null) {
                return false;
            }
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            return Boolean.parseBoolean(value.toString());
        }

        @Override
        protected String getGradlePropertyStringValue(String propertyName) {
            Object value = properties.get(propertyName);
            if (value == null) {
                return null;
            }
            return value.toString();
        }

        // Make protected methods public for testing
        public String getPropertyStatus(String propertyName) {
            return super.getPropertyStatus(propertyName);
        }
    }
}
