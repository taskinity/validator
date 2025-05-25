// 🔧 CONFIG LOADER - Zarządzanie konfiguracją środowiskową
// Obsługa .env, zmiennych systemowych i properties

class ConfigLoader {
    static Map<String, String> config = [:]
    static boolean loaded = false

    // 📋 ZAŁADUJ WSZYSTKIE ŹRÓDŁA KONFIGURACJI
    static void loadAllConfigurations(String envFile = '.env') {
        if (loaded) {
            println "⚡ Konfiguracja już załadowana"
            return
        }

        println "🔧 Ładowanie konfiguracji..."

        // 1. Załaduj .env file
        loadEnvFile(envFile)

        // 2. Załaduj application.properties
        loadPropertiesFile('application.properties')

        // 3. Załaduj zmienne systemowe (najwyższy priorytet)
        loadSystemProperties()

        // 4. Ustaw domyślne wartości
        setDefaults()

        loaded = true
        println "✅ Konfiguracja załadowana z ${config.size()} parametrów"
    }

    // 📄 ŁADUJ PLIK .env
    static void loadEnvFile(String envFile = '.env') {
        def file = new File(envFile)
        if (!file.exists()) {
            println "⚠️  Plik ${envFile} nie istnieje"

            // Sprawdź czy istnieje .env.example
            def exampleFile = new File('.env.example')
            if (exampleFile.exists()) {
                println "💡 Znaleziono .env.example - skopiuj jako .env"
                println "   cp .env.example .env"
            }
            return
        }

        int envCount = 0
        file.eachLine { line ->
            line = line.trim()
            if (line && !line.startsWith('#') && line.contains('=')) {
                def parts = line.split('=', 2)
                def key = parts[0].trim()
                def value = parts[1].trim()

                // Usuń cudzysłowy jeśli istnieją
                value = value.replaceAll(/^["']|["']$/, '')

                config[key] = value
                System.setProperty(key, value)
                envCount++
            }
        }

        println "📄 Załadowano ${envCount} zmiennych z ${envFile}"
    }

    // ⚙️ ŁADUJ APPLICATION.PROPERTIES
    static void loadPropertiesFile(String propsFile = 'application.properties') {
        def file = new File(propsFile)
        if (!file.exists()) {
            println "⚠️  Plik ${propsFile} nie istnieje"
            return
        }

        Properties props = new Properties()
        file.withInputStream { props.load(it) }

        int propsCount = 0
        props.each { key, value ->
            // Nie nadpisuj jeśli już istnieje w .env
            if (!config.containsKey(key.toString())) {
                config[key.toString()] = value.toString()
                System.setProperty(key.toString(), value.toString())
                propsCount++
            }
        }

        println "⚙️  Załadowano ${propsCount} właściwości z ${propsFile}"
    }

    // 🖥️ ŁADUJ ZMIENNE SYSTEMOWE
    static void loadSystemProperties() {
        int sysCount = 0

        // Zmienne środowiskowe mają najwyższy priorytet
        System.getenv().each { key, value ->
            if (key.contains('_')) { // Typowe zmienne ENV
                config[key] = value
                System.setProperty(key, value)
                sysCount++
            }
        }

        println "🖥️  Załadowano ${sysCount} zmiennych systemowych"
    }

    // 🎯 USTAW WARTOŚCI DOMYŚLNE
    static void setDefaults() {
        def defaults = [
                'INPUT_DIR': '/tmp/input',
                'OUTPUT_DIR': '/tmp/output',
                'ERROR_DIR': '/tmp/error',
                'LOG_LEVEL': 'INFO',
                'TIMER_PERIOD': '30000',
                'HAWTIO_PORT': '8080',
                'HEALTH_PORT': '9090',
                'APP_NAME': 'CamelGroovyValidator',
                'APP_VERSION': '1.0.0'
        ]

        defaults.each { key, defaultValue ->
            if (!config.containsKey(key) && !System.getProperty(key)) {
                config[key] = defaultValue
                System.setProperty(key, defaultValue)
            }
        }
    }

    // 🔍 POBIERZ WARTOŚĆ Z HIERARCHII
    static String get(String key, String defaultValue = null) {
        // Hierarchia: System Properties > Environment > Config > Default
        return System.getProperty(key) ?:
                System.getenv(key) ?:
                        config[key] ?:
                                defaultValue
    }

    // 🔄 ROZWIŃ ZMIENNE W ENDPOINT URI
    static String resolveEndpoint(String endpoint) {
        if (!endpoint) return endpoint

        // Zamień ${VAR} i ${VAR:default} na wartości
        return endpoint.replaceAll(/\$\{([^}:]+)(?::([^}]*))?\}/) { match, varName, defaultVal ->
            def value = get(varName.trim(), defaultVal?.trim())
            return value ?: "\${${varName}}"
        }
    }

    // 📋 POKAŻ CAŁĄ KONFIGURACJĘ
    static void printConfiguration() {
        println "\n" + "="*60
        println "🔧 AKTUALNA KONFIGURACJA"
        println "="*60

        def sortedKeys = config.keySet().sort()
        sortedKeys.each { key ->
            def value = config[key]
            // Ukryj hasła
            if (key.toLowerCase().contains('password') ||
                    key.toLowerCase().contains('secret') ||
                    key.toLowerCase().contains('token')) {
                value = '*'.multiply(value.length())
            }
            printf "%-25s = %s\n", key, value
        }
        println "="*60
    }

    // 🧪 WALIDUJ WYMAGANĄ KONFIGURACJĘ
    static boolean validateRequired(List<String> requiredKeys) {
        def missing = []

        requiredKeys.each { key ->
            if (!get(key)) {
                missing << key
            }
        }

        if (missing) {
            println "❌ Brakuje wymaganych zmiennych konfiguracyjnych:"
            missing.each { key ->
                println "   - ${key}"
            }
            return false
        }

        return true
    }

    // 🔄 PRZEŁADUJ KONFIGURACJĘ
    static void reload(String envFile = '.env') {
        println "🔄 Przeładowywanie konfiguracji..."
        config.clear()
        loaded = false
        loadAllConfigurations(envFile)
    }
}

// 🚀 AUTO-LOAD przy pierwszym użyciu
if (!ConfigLoader.loaded) {
    ConfigLoader.loadAllConfigurations()
}

// Alias dla kompatybilności wstecznej
class EnvConfigLoader {
    static void loadEnvFile(String file = '.env') {
        ConfigLoader.loadEnvFile(file)
    }

    static String get(String key, String defaultValue = null) {
        return ConfigLoader.get(key, defaultValue)
    }

    static String resolveEndpoint(String endpoint) {
        return ConfigLoader.resolveEndpoint(endpoint)
    }
}