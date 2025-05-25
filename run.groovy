#!/usr/bin/env groovy

// 🚀 CAMEL GROOVY VALIDATOR - MAIN LAUNCHER
// Autor: Tom Sapletta | tom.sapletta.com

@Grab('org.apache.camel:camel-core:3.20.0')
@Grab('org.apache.camel:camel-file:3.20.0')
@Grab('org.apache.camel:camel-http:3.20.0')
@Grab('org.apache.camel:camel-jetty:3.20.0')
@Grab('io.hawt:hawtio-embedded:2.17.0')

import org.apache.camel.impl.DefaultCamelContext
import io.hawt.embedded.Main

class CamelApplication {

    void run() {
        println """
╔══════════════════════════════════════════════════════════════╗
║  🚀 Apache Camel Groovy Validator                            ║
║  📧 Autor: Tom Sapletta | tom.sapletta.com                   ║
║  🔍 Walidacja endpointów przed uruchomieniem                 ║
╚══════════════════════════════════════════════════════════════╝
        """.trim()

        try {
            // 🔧 Załaduj konfigurację
            println "📋 Ładowanie konfiguracji..."
            evaluate(new File('config-loader.groovy'))

            // 🔍 Załaduj walidator (już zawiera EndpointValidator + EnvConfigLoader)
            println "🔍 Inicjalizacja walidatora endpointów..."
            evaluate(new File('validator.groovy'))

            // 🛣️ Załaduj routes
            println "🛣️  Ładowanie definicji route..."
            def routesScript = new File('routes.groovy')
            if (routesScript.exists()) {
                evaluate(routesScript)
            } else {
                println "⚠️  Plik routes.groovy nie istnieje - używam domyślnych route"
                createDefaultRoutes()
            }

            // 🚀 Uruchom Camel Context
            def camelContext = new DefaultCamelContext()
            camelContext.setManagementNameStrategy(new org.apache.camel.management.DefaultManagementNameStrategy())

            // Dodaj routes z walidacją
            camelContext.addRoutes(new ValidatedRoutes())

            // ⚡ Uruchom Health Check endpoint
            startHealthCheck(camelContext)

            // 📊 Uruchom Hawtio Dashboard
            startHawtioDashboard()

            // 🎯 Start Camel
            camelContext.start()

            println """
┌─────────────────────────────────────────────────────────────┐
│  ✅ System uruchomiony pomyślnie!                           │
│  📊 Hawtio Dashboard: http://localhost:8080/hawtio          │
│  🔧 Health Check: http://localhost:9090/health             │
│  📁 Logs: ./logs/camel.log                                 │
│  🛑 Zatrzymaj: Ctrl+C                                      │
└─────────────────────────────────────────────────────────────┘
            """.trim()

            // Keep alive
            addShutdownHook {
                println "\n🛑 Zatrzymywanie aplikacji..."
                camelContext.stop()
                println "✅ Aplikacja zatrzymana"
            }

            // Wait indefinitely
            synchronized(this) {
                this.wait()
            }

        } catch (Exception e) {
            println "\n💥 BŁĄD URUCHOMIENIA: ${e.message}"
            println "🔧 Sprawdź konfigurację i spróbuj ponownie"
            e.printStackTrace()
            System.exit(1)
        }
    }

    void createDefaultRoutes() {
        println "🔨 Tworzenie domyślnych route..."

        // Stwórz podstawowy plik routes.groovy
        def defaultRoutes = """
// 🛣️ DEFAULT ROUTES - wygenerowane automatycznie

@Grab('org.apache.camel:camel-core:3.20.0')
import org.apache.camel.builder.RouteBuilder

class ValidatedRoutes extends RouteBuilder {
    void configure() {
        // 🔧 ZAŁADUJ KONFIGURACJĘ Z .env
        if (binding.hasVariable('EnvConfigLoader')) {
            EnvConfigLoader.loadEnvFile()
        }
        
        // 📋 PODSTAWOWE ENDPOINTY 
        def endpointsToValidate = [
            "file:\${INPUT_DIR:/tmp/input}?noop=true",
            "file:\${OUTPUT_DIR:/tmp/output}",
            "timer:healthcheck?period=30000"
        ]
        
        // ✅ WALIDUJ ENDPOINTY
        if (binding.hasVariable('EndpointValidator')) {
            def validator = new EndpointValidator(getContext())
            def results = validator.validateAllEndpoints(endpointsToValidate)
            
            println "\\n" + "="*50
            println "🔍 WALIDACJA ENDPOINTÓW"
            println "="*50
            results.each { uri, result -> println "\${result} | \${uri}" }
        }
        
        // 🚀 PODSTAWOWE ROUTES
        from("timer:healthcheck?period=30000")
        .routeId("health-timer")
        .setBody(constant("Health check OK - \${date:now}"))
        .to("log:health?level=INFO")
        
        // File processing route (jeśli foldery istnieją)
        def inputDir = System.getProperty('INPUT_DIR') ?: '/tmp/input'
        def outputDir = System.getProperty('OUTPUT_DIR') ?: '/tmp/output'
        
        if (new File(inputDir).exists()) {
            from("file:\${inputDir}?noop=true&delay=5000")
            .routeId("file-processor")
            .log("📁 Przetwarzam plik: \${header.CamelFileName}")
            .setBody(simple("Przetworzono: \${body} o \${date:now}"))
            .to("file:\${outputDir}")
        }
    }
}
"""

        new File('routes.groovy').text = defaultRoutes
        println "✅ Utworzono routes.groovy z domyślnymi route"
    }

    void startHealthCheck(camelContext) {
        println "🔧 Uruchamianie Health Check endpoint..."

        // Dodaj health check route
        camelContext.addRoutes(new org.apache.camel.builder.RouteBuilder() {
            void configure() {
                from("jetty:http://0.0.0.0:9090/health")
                        .routeId("health-check-http")
                        .setHeader("Content-Type", constant("application/json"))
                        .setBody(constant('''
                {
                    "status": "UP",
                    "application": "Camel Groovy Validator",
                    "timestamp": "''' + new Date().toString() + '''",
                    "routes": "''' + camelContext.getRoutes().size() + '''",
                    "uptime": "''' + camelContext.getUptime() + '''"
                }
                '''))
            }
        })
    }

    void startHawtioDashboard() {
        try {
            println "📊 Uruchamianie Hawtio Dashboard..."

            Main hawtio = new Main()
            hawtio.setPort(8080)
            hawtio.setContextPath("/hawtio")
            hawtio.run()

            println "✅ Hawtio Dashboard dostępny na http://localhost:8080/hawtio"

        } catch (Exception e) {
            println "⚠️  Nie udało się uruchomić Hawtio: ${e.message}"
            println "   Aplikacja będzie działać bez dashboardu"
        }
    }
}

// 🚀 URUCHOM APLIKACJĘ
if (args.contains('--help') || args.contains('-h')) {
    println """
🚀 Camel Groovy Validator - Pomoc

Użycie:
  groovy run.groovy [opcje]

Opcje:
  --help, -h     Pokaż tę pomoc
  --version, -v  Pokaż wersję
  --config FILE  Użyj innego pliku konfiguracji (domyślnie: .env)

Przykłady:
  groovy run.groovy
  groovy run.groovy --config production.env

Więcej informacji: https://tom.sapletta.com
    """
} else if (args.contains('--version') || args.contains('-v')) {
    println "Camel Groovy Validator v1.0.0"
} else {
    // Utwórz foldery jeśli nie istnieją
    ['data', 'data/input', 'data/output', 'data/error', 'logs'].each { dir ->
        new File(dir).mkdirs()
    }

    // Uruchom aplikację
    new CamelApplication().run()
}