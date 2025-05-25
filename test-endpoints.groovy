#!/usr/bin/env groovy

// 🧪 TEST ENDPOINTS - Automatyczne testy walidacji endpointów
// Uruchom: groovy test-endpoints.groovy

@Grab('org.apache.camel:camel-core:3.20.0')
@Grab('org.apache.camel:camel-test:3.20.0')

import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.test.junit4.CamelTestSupport

// Załaduj dependencies
evaluate(new File('config-loader.groovy'))
evaluate(new File('validator.groovy'))

class EndpointValidatorTest {

    def camelContext
    def validator

    void setUp() {
        println "🧪 Inicjalizacja testów..."
        camelContext = new DefaultCamelContext()
        validator = new EndpointValidator(camelContext)
        ConfigLoader.loadAllConfigurations()
    }

    void tearDown() {
        camelContext?.stop()
    }

    // ✅ TEST BASIC VALIDATION
    void testBasicValidation() {
        println "\n📋 Test: Basic endpoint validation"

        def result = validator.validateSingleEndpoint("timer:test?period=1000")
        assert result.contains("✅"), "Timer endpoint should be valid: ${result}"
        println "   ✅ Timer endpoint validation passed"

        def badResult = validator.validateSingleEndpoint("invalid:endpoint")
        assert badResult.contains("❌"), "Invalid endpoint should fail: ${badResult}"
        println "   ✅ Invalid endpoint properly rejected"
    }

    // 📁 TEST FILE ENDPOINTS
    void testFileEndpoints() {
        println "\n📁 Test: File endpoints validation"

        // Test existing directory
        def tempDir = File.createTempDir()
        def result = validator.validateSingleEndpoint("file:${tempDir.absolutePath}")
        assert result.contains("✅"), "Existing directory should be valid: ${result}"
        println "   ✅ Existing directory validation passed"

        // Test non-existing directory with autoCreate
        def nonExistentDir = new File(tempDir, "nonexistent")
        def autoCreateResult = validator.validateSingleEndpoint("file:${nonExistentDir.absolutePath}?autoCreate=true")
        assert autoCreateResult.contains("✅"), "AutoCreate should work: ${autoCreateResult}"
        println "   ✅ AutoCreate directory validation passed"

        // Test non-existing directory without autoCreate
        def badDir = new File(tempDir, "bad")
        def badResult = validator.validateSingleEndpoint("file:${badDir.absolutePath}")
        assert badResult.contains("❌"), "Non-existent directory should fail: ${badResult}"
        println "   ✅ Non-existent directory properly rejected"

        // Cleanup
        tempDir.deleteDir()
    }

    // 🌐 TEST HTTP ENDPOINTS
    void testHttpEndpoints() {
        println "\n🌐 Test: HTTP endpoints validation"

        // Test valid HTTP endpoint
        def result = validator.validateSingleEndpoint("http://httpbin.org/status/200")
        println "   HTTP result: ${result}"
        // Note: May fail if no internet connection

        // Test invalid HTTP endpoint
        def badResult = validator.validateSingleEndpoint("http://nonexistent-domain-12345.com")
        assert badResult.contains("❌"), "Invalid HTTP endpoint should fail: ${badResult}"
        println "   ✅ Invalid HTTP endpoint properly rejected"

        // Test malformed URL
        def malformedResult = validator.validateSingleEndpoint("http://")
        assert malformedResult.contains("❌"), "Malformed URL should fail: ${malformedResult}"
        println "   ✅ Malformed URL properly rejected"
    }

    // 📧 TEST EMAIL ENDPOINTS
    void testEmailEndpoints() {
        println "\n📧 Test: Email endpoints validation"

        // Test localhost SMTP (will likely fail, but should handle gracefully)
        def result = validator.validateSingleEndpoint("smtp://localhost:25")
        println "   SMTP localhost result: ${result}"

        // Test invalid SMTP
        def badResult = validator.validateSingleEndpoint("smtp://nonexistent-mail-server-12345.com:25")
        assert badResult.contains("❌"), "Invalid SMTP should fail: ${badResult}"
        println "   ✅ Invalid SMTP endpoint properly rejected"
    }

    // 💾 TEST DATABASE ENDPOINTS
    void testDatabaseEndpoints() {
        println "\n💾 Test: Database endpoints validation"

        // Test H2 in-memory database
        def h2Result = validator.validateSingleEndpoint("jdbc:h2:mem:testdb")
        assert h2Result.contains("✅"), "H2 driver should be available: ${h2Result}"
        println "   ✅ H2 database validation passed"

        // Test unknown database
        def unknownResult = validator.validateSingleEndpoint("jdbc:unknowndb:mem:test")
        assert unknownResult.contains("❌"), "Unknown DB should fail: ${unknownResult}"
        println "   ✅ Unknown database properly rejected"
    }

    // 🔄 TEST ENVIRONMENT VARIABLE RESOLUTION
    void testEnvironmentResolution() {
        println "\n🔄 Test: Environment variable resolution"

        // Set test variables
        System.setProperty("TEST_HOST", "localhost")
        System.setProperty("TEST_PORT", "8080")

        def endpoint = "http://\${TEST_HOST}:\${TEST_PORT}/test"
        def resolved = ConfigLoader.resolveEndpoint(endpoint)

        assert resolved == "http://localhost:8080/test", "Resolution failed: ${resolved}"
        println "   ✅ Environment variable resolution passed"

        // Test with default values
        def endpointWithDefault = "http://\${UNKNOWN_HOST:defaulthost}:\${UNKNOWN_PORT:9090}/test"
        def resolvedDefault = ConfigLoader.resolveEndpoint(endpointWithDefault)

        assert resolvedDefault == "http://defaulthost:9090/test", "Default resolution failed: ${resolvedDefault}"
        println "   ✅ Default value resolution passed"
    }

    // 📊 TEST BATCH VALIDATION
    void testBatchValidation() {
        println "\n📊 Test: Batch validation"

        def endpoints = [
                "timer:test1?period=1000",
                "timer:test2?period=2000",
                "file:/tmp/nonexistent",
                "invalid:endpoint"
        ]

        def results = validator.validateAllEndpoints(endpoints)

        assert results.size() == 4, "Should validate all endpoints: ${results.size()}"
        assert results.values().count { it.contains("✅") } >= 2, "Should have some valid endpoints"
        assert results.values().count { it.contains("❌") } >= 2, "Should have some invalid endpoints"

        println "   ✅ Batch validation completed with ${results.size()} results"
    }

    // 🎯 TEST PROTOCOL DETECTION
    void testProtocolDetection() {
        println "\n🎯 Test: Protocol detection"

        assert validator.getEndpointType("file:/tmp") == "file"
        assert validator.getEndpointType("http://test.com") == "http"
        assert validator.getEndpointType("smtp://mail.com") == "smtp"
        assert validator.getEndpointType("ftp://ftp.com") == "ftp"
        assert validator.getEndpointType("timer:test") == "timer"

        println "   ✅ Protocol detection passed"
    }

    // 🚀 RUN ALL TESTS
    void runAllTests() {
        println """
╔══════════════════════════════════════════════════════════════╗
║  🧪 APACHE CAMEL ENDPOINT VALIDATOR - TEST SUITE            ║  
║  📧 Autor: Tom Sapletta | tom.sapletta.com                   ║
╚══════════════════════════════════════════════════════════════╝
        """.trim()

        setUp()

        def tests = [
                'testBasicValidation',
                'testFileEndpoints',
                'testHttpEndpoints',
                'testEmailEndpoints',
                'testDatabaseEndpoints',
                'testEnvironmentResolution',
                'testBatchValidation',
                'testProtocolDetection'
        ]

        def passed = 0
        def failed = 0

        tests.each { testName ->
            try {
                this."${testName}"()
                passed++
                println "✅ ${testName} - PASSED"
            } catch (Exception e) {
                failed++
                println "❌ ${testName} - FAILED: ${e.message}"
                if (System.getProperty('TEST_VERBOSE')) {
                    e.printStackTrace()
                }
            }
        }

        tearDown()

        println "\n" + "="*60
        println "📊 TEST RESULTS:"
        println "   ✅ Passed: ${passed}"
        println "   ❌ Failed: ${failed}"
        println "   📊 Total: ${tests.size()}"
        println "   💯 Success rate: ${Math.round((passed / tests.size()) * 100)}%"
        println "="*60

        if (failed > 0) {
            println "\n💡 Uruchom z -DTEST_VERBOSE=true dla szczegółów błędów"
            System.exit(1)
        } else {
            println "\n🎉 Wszystkie testy przeszły pomyślnie!"
        }
    }
}

// 🎯 PERFORMANCE TEST
class PerformanceTest {

    static void runPerformanceTest() {
        println "\n⚡ Test wydajności walidacji..."

        def camelContext = new DefaultCamelContext()
        def validator = new EndpointValidator(camelContext)

        def endpoints = []
        (1..100).each { i ->
            endpoints << "timer:test${i}?period=1000"
            endpoints << "file:/tmp/test${i}"
        }

        def startTime = System.currentTimeMillis()
        def results = validator.validateAllEndpoints(endpoints)
        def endTime = System.currentTimeMillis()

        def duration = endTime - startTime
        def avgTime = duration / endpoints.size()

        println "📊 Wyniki testu wydajności:"
        println "   🔢 Endpointy: ${endpoints.size()}"
        println "   ⏱️  Całkowity czas: ${duration}ms"
        println "   📈 Średni czas/endpoint: ${avgTime}ms"
        println "   🚀 Endpointy/sekundę: ${Math.round(1000 / avgTime)}"

        camelContext.stop()
    }
}

// 🚀 MAIN EXECUTION
if (args.contains('--help') || args.contains('-h')) {
    println """
🧪 Camel Endpoint Validator - Test Suite

Użycie:
  groovy test-endpoints.groovy [opcje]

Opcje:
  --help, -h          Pokaż tę pomoc
  --performance, -p   Uruchom test wydajności
  --verbose, -v       Szczegółowe logi błędów

Przykłady:
  groovy test-endpoints.groovy
  groovy test-endpoints.groovy --performance
  groovy test-endpoints.groovy --verbose

Więcej: https://tom.sapletta.com
    """
} else if (args.contains('--performance') || args.contains('-p')) {
    PerformanceTest.runPerformanceTest()
} else {
    if (args.contains('--verbose') || args.contains('-v')) {
        System.setProperty('TEST_VERBOSE', 'true')
    }

    new EndpointValidatorTest().runAllTests()
}