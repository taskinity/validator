// 🛣️ APACHE CAMEL ROUTES - Definicje tras z walidacją
// Konfiguracja route z obsługą .env i pre-validation

@Grab('org.apache.camel:camel-core:3.20.0')
@Grab('org.apache.camel:camel-file:3.20.0')
@Grab('org.apache.camel:camel-http:3.20.0')

import org.apache.camel.builder.RouteBuilder

class ValidatedRoutes extends RouteBuilder {

    void configure() {

        // 🔧 ZAŁADUJ KONFIGURACJĘ Z .env (jeśli jeszcze nie załadowana)
        if (binding.hasVariable('ConfigLoader')) {
            ConfigLoader.loadAllConfigurations()
        }

        // 📋 DEFINICJE ENDPOINTÓW Z WYKORZYSTANIEM ZMIENNYCH
        def endpointsToValidate = [
                // File processing endpoints
                "file:\${INPUT_DIR:/tmp/input}?noop=true&delay=\${FILE_DELAY:5000}",
                "file:\${OUTPUT_DIR:/tmp/output}",
                "file:\${ERROR_DIR:/tmp/error}?autoCreate=true",

                // HTTP/API endpoints
                "http://\${API_HOST:httpbin.org}/status/200?connectTimeout=\${HTTP_TIMEOUT:5000}",
                "https://\${EXTERNAL_API:api.github.com}",

                // Email endpoints
                "smtp://\${SMTP_HOST:localhost}:\${SMTP_PORT:25}?from=\${MAIL_FROM:noreply@localhost}",

                // Timer endpoints
                "timer:healthcheck?period=\${TIMER_PERIOD:30000}",
                "timer:monitoring?period=\${MONITOR_PERIOD:60000}"
        ]

        // ✅ WALIDUJ ENDPOINTY PRZED STARTEM
        if (binding.hasVariable('EndpointValidator')) {
            def validator = new EndpointValidator(getContext())
            def results = validator.validateAllEndpoints(endpointsToValidate)

            // 📊 POKAŻ WYNIKI WALIDACJI
            println "\n" + "="*70
            println "🔍 RAPORT WALIDACJI ENDPOINTÓW"
            println "="*70

            def successCount = 0
            def errorCount = 0
            def warningCount = 0

            results.each { uri, result ->
                println "${result} | ${uri}"
                if (result.startsWith("✅")) successCount++
                else if (result.startsWith("❌")) errorCount++
                else if (result.startsWith("⚠️")) warningCount++
            }

            println "="*70
            println "📊 PODSUMOWANIE: ✅ ${successCount} OK | ⚠️ ${warningCount} OSTRZEŻEŃ | ❌ ${errorCount} BŁĘDÓW"
            println "="*70

            // 🚨 ZATRZYMAJ JEŚLI KRYTYCZNE BŁĘDY
            def criticalErrors = results.findAll { k, v ->
                v.startsWith("❌") && !k.contains("autoCreate") && !k.contains("localhost")
            }

            if (criticalErrors.size() > 0) {
                println "🚨 WYKRYTO KRYTYCZNE BŁĘDY ENDPOINTÓW!"
                criticalErrors.each { uri, error ->
                    println "   💥 ${uri} → ${error}"
                }
                throw new RuntimeException("Validation failed for critical endpoints")
            }
        }

        // 🎯 GLOBALNE EXCEPTION HANDLING
        onException(Exception.class)
                .handled(true)
                .log("💥 Błąd w route \${routeId}: \${exception.message}")
                .setBody(simple("ERROR: \${exception.message} at \${date:now}"))
                .to("file:\${ERROR_DIR:/tmp/error}?fileName=error-\${date:yyyyMMdd-HHmmss}.txt")

        // ⏰ HEALTH CHECK ROUTE
        from("timer:healthcheck?period=\${TIMER_PERIOD:30000}")
                .routeId("health-timer")
                .log("💚 Health check OK - \${date:now}")
                .setBody(simple("Health check successful at \${date:now:yyyy-MM-dd HH:mm:ss}"))
                .choice()
                .when(simple("\${sys.LOG_HEALTH_TO_FILE}"))
                .to("file:\${OUTPUT_DIR:/tmp/output}?fileName=health-\${date:yyyyMMdd}.log")
                .otherwise()
                .to("log:health?level=INFO")

        // 📁 FILE PROCESSING ROUTE
        def inputDir = System.getProperty('INPUT_DIR') ?: '/tmp/input'
        if (new File(inputDir).exists() || System.getProperty('INPUT_DIR')) {

            from("file:\${INPUT_DIR:/tmp/input}?noop=true&delay=\${FILE_DELAY:5000}")
                    .routeId("file-processor")
                    .log("📁 Przetwarzam plik: \${header.CamelFileName} (\${header.CamelFileLength} bytes)")

            // Sprawdź typ pliku
                    .choice()
                    .when(header("CamelFileName").endsWith(".json"))
                    .to("direct:processJson")
                    .when(header("CamelFileName").endsWith(".xml"))
                    .to("direct:processXml")
                    .when(header("CamelFileName").endsWith(".csv"))
                    .to("direct:processCsv")
                    .otherwise()
                    .to("direct:processGeneral")

            // Zapisz wynik
                    .to("file:\${OUTPUT_DIR:/tmp/output}?fileName=processed-\${header.CamelFileName}")
                    .log("✅ Plik przetworzony: \${header.CamelFileName}")
        }

        // 📄 JSON PROCESSING
        from("direct:processJson")
                .routeId("json-processor")
                .log("🔧 Przetwarzanie JSON: \${header.CamelFileName}")
                .process { exchange ->
                    def jsonText = exchange.in.body.toString()
                    try {
                        def json = new groovy.json.JsonSlurper().parseText(jsonText)
                        json.processed_at = new Date().toString()
                        json.processed_by = "CamelGroovyValidator"

                        def result = new groovy.json.JsonBuilder(json).toPrettyString()
                        exchange.in.body = result

                    } catch (Exception e) {
                        exchange.in.body = """
{
    "error": "Invalid JSON format",
    "original_content": "${jsonText.take(100)}...",
    "processed_at": "${new Date()}"
}
"""
                    }
                }

        // 📊 CSV PROCESSING
        from("direct:processCsv")
                .routeId("csv-processor")
                .log("📊 Przetwarzanie CSV: \${header.CamelFileName}")
                .process { exchange ->
                    def csvContent = exchange.in.body.toString()
                    def lines = csvContent.split('\n')
                    def processedLines = ["# Processed at ${new Date()}"]

                    lines.eachWithIndex { line, index ->
                        if (index == 0) {
                            // Header - dodaj kolumnę
                            processedLines << line + ",processed_at"
                        } else if (line.trim()) {
                            // Data - dodaj timestamp
                            processedLines << line + ",${new Date().format('yyyy-MM-dd HH:mm:ss')}"
                        }
                    }

                    exchange.in.body = processedLines.join('\n')
                }

        // 🔧 XML PROCESSING
        from("direct:processXml")
                .routeId("xml-processor")
                .log("🔧 Przetwarzanie XML: \${header.CamelFileName}")
                .process { exchange ->
                    def xmlContent = exchange.in.body.toString()
                    try {
                        def xml = new XmlSlurper().parseText(xmlContent)
                        // Dodaj metadane przetwarzania
                        def processed = """<?xml version="1.0" encoding="UTF-8"?>
<processed_document>
    <metadata>
        <processed_at>${new Date()}</processed_at>
        <processed_by>CamelGroovyValidator</processed_by>
    </metadata>
    <original_content><![CDATA[${xmlContent}]]></original_content>
</processed_document>"""
                        exchange.in.body = processed
                    } catch (Exception e) {
                        exchange.in.body = """<?xml version="1.0" encoding="UTF-8"?>
<error>
    <message>Invalid XML format</message>
    <timestamp>${new Date()}</timestamp>
</error>"""
                    }
                }

        // 📄 GENERAL FILE PROCESSING
        from("direct:processGeneral")
                .routeId("general-processor")
                .log("📄 Przetwarzanie pliku ogólnego: \${header.CamelFileName}")
                .setBody(simple("""
=== PRZETWORZONO PLIK ===
Nazwa: \${header.CamelFileName}
Rozmiar: \${header.CamelFileLength} bytes
Data: \${date:now:yyyy-MM-dd HH:mm:ss}
========================

ORYGINALNY CONTENT:
\${body}

=== KONIEC PLIKU ===
"""))

        // 📊 MONITORING ROUTE
        from("timer:monitoring?period=\${MONITOR_PERIOD:60000}")
                .routeId("system-monitor")
                .log("📊 Monitoring systemu...")
                .process { exchange ->
                    def runtime = Runtime.getRuntime()
                    def monitoring = [
                            timestamp: new Date().toString(),
                            memory_total: "${runtime.totalMemory() / 1024 / 1024} MB",
                            memory_free: "${runtime.freeMemory() / 1024 / 1024} MB",
                            memory_used: "${(runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024} MB",
                            processors: runtime.availableProcessors(),
                            routes_count: exchange.context.routes.size(),
                            uptime: exchange.context.uptime
                    ]

                    def json = new groovy.json.JsonBuilder(monitoring).toPrettyString()
                    exchange.in.body = json
                }
                .choice()
                .when(simple("\${sys.ENABLE_MONITORING_LOG}"))
                .to("file:\${OUTPUT_DIR:/tmp/output}?fileName=monitoring-\${date:yyyyMMdd-HH}.json")
                .otherwise()
                .to("log:monitoring?level=DEBUG")

        // 🌐 HTTP API HEALTH CHECK (jeśli włączony)
        def apiHost = System.getProperty('API_HOST')
        if (apiHost && apiHost != 'localhost') {
            from("timer:api-check?period=\${API_CHECK_PERIOD:120000}")
                    .routeId("api-health-check")
                    .setHeader("User-Agent", constant("CamelGroovyValidator/1.0"))
                    .to("http://\${API_HOST:httpbin.org}/status/200?connectTimeout=5000")
                    .choice()
                    .when(header("CamelHttpResponseCode").isEqualTo(200))
                    .log("✅ API Health OK: \${header.CamelHttpResponseCode}")
                    .otherwise()
                    .log("❌ API Health FAILED: \${header.CamelHttpResponseCode}")
                    .to("file:\${ERROR_DIR:/tmp/error}?fileName=api-error-\${date:yyyyMMdd-HHmmss}.txt")
        }
    }
}