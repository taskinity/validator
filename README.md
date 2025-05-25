# 🚀 Apache Camel Groovy Validator

> **Walidacja endpointów przed uruchomieniem route** - koniec z "endpoint roulette"!

[![Apache Camel](https://img.shields.io/badge/Apache%20Camel-3.20.0-blue)](https://camel.apache.org/)
[![Groovy](https://img.shields.io/badge/Groovy-4.0-green)](https://groovy-lang.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## 🎯 Problem

```bash
# Typowa sytuacja z Apache Camel:
from("ftp://niedziala.com/upload")  # 💥 Runtime exception!
from("smtp://błędny-host:25")       # 💥 Dopiero po 30 sekundach!
from("file:/nieistniejący/folder")  # 💥 Silent failure!
```

## ✅ Rozwiązanie

```groovy
// Sprawdź WSZYSTKIE endpointy przed startem!
🔍 Sprawdzam: ftp://server.com:21     → ✅ FTP server: server.com:21
🔍 Sprawdzam: smtp://mail.com:587     → ✅ SMTP server: mail.com:587  
🔍 Sprawdzam: file:/data/input        → ✅ File endpoint OK: /data/input

🎉 System uruchomiony pomyślnie! 
📁 Wszystkie endpointy sprawdzone i działają
```

# 📁 CamelGroovyValidator - Struktura Projektu

```
validator/
├── 📄 README.md                    # Dokumentacja projektu
├── 🔧 .env                         # Konfiguracja środowiska
├── 🔧 .env.example                 # Przykład konfiguracji
├── ⚙️  application.properties       # Właściwości aplikacji
├── 🚀 run.groovy                   # Główny skrypt uruchomieniowy
├── 🔍 validator.groovy              # Walidator endpointów
├── 🛣️  routes.groovy                # Definicje route Camel
├── 🧪 test-endpoints.groovy         # Testy endpointów
├── 📊 health-check.groovy           # Health check endpoints
├── 🔧 config-loader.groovy          # Loader konfiguracji .env
├── 📋 endpoints.txt                 # Lista endpointów do walidacji
├── 📝 CHANGELOG.md                  # Historia zmian
├── 📜 LICENSE                       # Licencja MIT
├── 🐳 Dockerfile                    # Kontener Docker
├── 🐳 docker-compose.yml            # Compose dla testów
├── 📁 data/                         # Folder danych (gitignore)
│   ├── input/                      # Pliki wejściowe
│   ├── output/                     # Pliki wyjściowe
│   └── error/                      # Pliki błędów
├── 📁 logs/                         # Logi aplikacji (gitignore)
└── 📁 scripts/                      # Skrypty pomocnicze
    ├── start.sh                    # Start w systemie Unix
    ├── start.bat                   # Start w Windows
    └── setup.sh                    # Konfiguracja środowiska
```

 
## 🚀 Quick Start

### 1. Pobierz projekt
```bash
git clone https://github.com/taskinity/validator.git
cd validator
```

### 2. Skonfiguruj środowisko
```bash
cp .env.example .env
# Edytuj .env według potrzeb
```

### 3. Uruchom
```bash
# Unix/Linux/macOS
./scripts/start.sh

# Windows  
scripts\start.bat

# Lub bezpośrednio
groovy run.groovy
```

### 4. Sprawdź dashboard
```
🎉 System działa!
📊 Hawtio dashboard: http://localhost:8080/hawtio
🔧 Health check: http://localhost:9090/health
```

## 📦 Obsługiwane protokoły

| Kategoria | Protokoły | Status |
|-----------|-----------|--------|
| **File Systems** | `file:`, `ftp:`, `sftp:` | ✅ Pełne wsparcie |
| **HTTP/Web** | `http:`, `https:`, `websocket:` | ✅ Pełne wsparcie |
| **Email** | `smtp:`, `smtps:`, `pop3:`, `imap:` | ✅ Pełne wsparcie |
| **Messaging** | `jms:`, `activemq:`, `rabbitmq:`, `kafka:` | ✅ Pełne wsparcie |
| **Databases** | `jdbc:`, `mongodb:`, `redis:` | ✅ Pełne wsparcie |
| **Enterprise** | `ldap:`, `ldaps:`, `netty:`, `mina:` | ✅ Pełne wsparcie |

## 🔧 Konfiguracja (.env)

```bash
# Przykładowa konfiguracja
SMTP_HOST=smtp.company.com
SMTP_PORT=587
DB_HOST=postgres.company.com
DB_PORT=5432
INPUT_DIR=/data/processing
KAFKA_HOST=kafka.company.com
```

### Użycie w endpointach:
```groovy
from("smtp://${SMTP_HOST}:${SMTP_PORT}")
from("file:${INPUT_DIR}?noop=true") 
from("kafka:${KAFKA_HOST}:${KAFKA_PORT}")
```

## 🧪 Przykłady użycia

### Basic validation
```groovy
def validator = new EndpointValidator(camelContext)
def result = validator.validateSingleEndpoint("ftp://server.com:21")
println result  // ✅ FTP server: server.com:21
```

### Batch validation
```groovy
def endpoints = [
    "file:/tmp/input?noop=true",
    "smtp://localhost:25", 
    "http://api.company.com/health"
]

def results = validator.validateAllEndpoints(endpoints)
results.each { uri, status -> 
    println "${status} | ${uri}"
}
```

### Environment variables
```groovy
// .env file
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587

// route definition
from("smtp://${MAIL_HOST}:${MAIL_PORT}")
```

## 📊 Przykładowy output

```
=====================================
🔍 RAPORT WALIDACJI ENDPOINTÓW
=====================================
✅ File endpoint OK: /data/input | file:/data/input?noop=true
✅ File endpoint OK: /data/output | file:/data/output  
✅ HTTP endpoint OK: 200 | http://httpbin.org/status/200
❌ SMTP server nieosiągalny: Connection refused | smtp://localhost:25
⚠️  FTP server odpowiada: localhost | ftp://localhost:21
=====================================
📊 PODSUMOWANIE: ✅ 3 OK | ❌ 1 BŁĘDÓW
=====================================
```

## 🏗️ Architektura

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   .env Config   │───▶│  EndpointValidator │───▶│   Camel Routes  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ Environment     │    │ Pre-flight      │    │ Production      │
│ Variables       │    │ Validation      │    │ Routes          │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 🔨 Development

### Struktura plików
```
run.groovy           # 🚀 Główny launcher
validator.groovy     # 🔍 Logika walidacji
routes.groovy        # 🛣️  Definicje Camel routes
config-loader.groovy # 🔧 Loader konfiguracji .env
test-endpoints.groovy # 🧪 Testy automatyczne
```

### Dodawanie nowych protokołów
```groovy
// W validator.groovy
case 'your-protocol':
    return validateYourProtocolEndpoint(uri)

// Implementacja
String validateYourProtocolEndpoint(String uri) {
    // Your validation logic here
    return "✅ Your protocol OK"
}
```

## 🐳 Docker

### Build & Run
```bash
docker build -t camel-validator .
docker run -p 8080:8080 -p 9090:9090 camel-validator
```

### Docker Compose (z testowymi serwisami)
```bash
docker-compose up -d
# Uruchamia: validator + FTP + SMTP + PostgreSQL + Redis
```

## 🧪 Testing

```bash
# Uruchom testy endpointów
groovy test-endpoints.groovy

# Test konkretnego protokołu
groovy -e "
def validator = new EndpointValidator(context)
println validator.validateSingleEndpoint('http://httpbin.org/status/200')
"
```

## 📚 Inspired by

Rozwiązanie powstało z potrzeby na podstawie doświadczeń z:
- **IT.NRW** - Enterprise Service Bus z 15+ różnymi systemami
- **Link11 GmbH** - CDN/DNS services monitoring
- **Telemonit** - IoT edge computing endpoints

## 🤝 Contributing

1. Fork repository
2. Stwórz feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push branch (`git push origin feature/amazing-feature`)
5. Otwórz Pull Request

## 📄 License

Apache License - zobacz [LICENSE](LICENSE) file.

## 🙋‍♂️ Autor

**Tom Sapletta**
- 🌐 Website: [tom.sapletta.com](https://tom.sapletta.com)
- 💼 LinkedIn: [tom-sapletta-com](https://linkedin.com/in/tom-sapletta-com)
- 🐙 GitHub: [tom-sapletta-com](https://github.com/tom-sapletta-com)

---

⭐ **Jeśli projekt Ci pomógł, zostaw gwiazdkę!** ⭐

