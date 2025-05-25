#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
LOG_FILE="$PROJECT_DIR/logs/startup.log"

# Functions
log() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] $1" >> "$LOG_FILE"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1" >&2
    echo "[ERROR] $1" >> "$LOG_FILE"
}

warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
    echo "[WARNING] $1" >> "$LOG_FILE"
}

info() {
    echo -e "${BLUE}[INFO]${NC} $1"
    echo "[INFO] $1" >> "$LOG_FILE"
}

check_prerequisites() {
    log "🔍 Sprawdzanie wymagań systemowych..."

    # Check Java
    if ! command -v java &> /dev/null; then
        error "Java nie jest zainstalowana. Wymagana Java 11 lub nowsza."
        exit 1
    fi

    local java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1-2)
    info "Java wersja: $java_version"

    # Check Groovy
    if ! command -v groovy &> /dev/null; then
        warning "Groovy nie jest zainstalowana. Próbuję zainstalować..."
        install_groovy
    else
        local groovy_version=$(groovy --version | head -n1 | awk '{print $3}')
        info "Groovy wersja: $groovy_version"
    fi

    # Check curl for health checks
    if ! command -v curl &> /dev/null; then
        warning "curl nie jest dostępny - health checks mogą nie działać"
    fi
}

install_groovy() {
    info "📦 Instalowanie Groovy..."

    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS with Homebrew
        if command -v brew &> /dev/null; then
            brew install groovy
        else
            error "Homebrew nie jest zainstalowany. Zainstaluj Groovy ręcznie."
            exit 1
        fi
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        # Linux - try package manager
        if command -v apt-get &> /dev/null; then
            sudo apt-get update && sudo apt-get install -y groovy
        elif command -v yum &> /dev/null; then
            sudo yum install -y groovy
        else
            # Manual installation
            local groovy_version="4.0.15"
            local groovy_url="https://archive.apache.org/dist/groovy/${groovy_version}/distribution/apache-groovy-binary-${groovy_version}.zip"

            log "Pobieranie Groovy ${groovy_version}..."
            wget -q "$groovy_url" -O "/tmp/groovy.zip"
            unzip -q "/tmp/groovy.zip" -d "/tmp/"
            sudo mv "/tmp/groovy-${groovy_version}" "/opt/groovy"

            # Add to PATH
            echo 'export GROOVY_HOME=/opt/groovy' >> ~/.bashrc
            echo 'export PATH=$GROOVY_HOME/bin:$PATH' >> ~/.bashrc
            export GROOVY_HOME=/opt/groovy
            export PATH=$GROOVY_HOME/bin:$PATH

            rm "/tmp/groovy.zip"
            info "Groovy zainstalowany w /opt/groovy"
        fi
    fi
}

setup_environment() {
    log "🔧 Konfiguracja środowiska..."

    cd "$PROJECT_DIR"

    # Create directories
    mkdir -p data/{input,output,error,archive}
    mkdir -p logs

    # Load environment variables
    if [[ -f ".env" ]]; then
        log "Ładowanie konfiguracji z .env"
        set -a
        source .env
        set +a
    elif [[ -f ".env.example" ]]; then
        warning "Plik .env nie istnieje. Kopiuję z .env.example"
        cp .env.example .env
        info "Edytuj plik .env przed ponownym uruchomieniem"
    else
        warning "Nie znaleziono pliku konfiguracyjnego .env"
    fi

    # Set default values
    export APP_NAME="${APP_NAME:-CamelGroovyValidator}"
    export ENVIRONMENT="${ENVIRONMENT:-development}"
    export HAWTIO_PORT="${HAWTIO_PORT:-8080}"
    export HEALTH_PORT="${HEALTH_PORT:-9090}"

    info "Środowisko: $ENVIRONMENT"
    info "Porty: Hawtio=$HAWTIO_PORT, Health=$HEALTH_PORT"
}

start_application() {
    log "🚀 Uruchamianie Apache Camel Groovy Validator..."

    # Check if already running
    if pgrep -f "run.groovy" > /dev/null; then
        warning "Aplikacja już działa. Używaj './scripts/stop.sh' aby zatrzymać."
        return 1
    fi

    # Start application
    if [[ "$1" == "--background" || "$1" == "-d" ]]; then
        log "Uruchamianie w tle..."
        nohup groovy run.groovy > "$LOG_FILE" 2>&1 &
        local pid=$!
        echo $pid > "$PROJECT_DIR/camel-validator.pid"
        info "Aplikacja uruchomiona w tle (PID: $pid)"

        # Wait a bit and check if still running
        sleep 5
        if kill -0 $pid 2>/dev/null; then
            log "✅ Aplikacja działa poprawnie"
        else
            error "❌ Aplikacja nie uruchomiła się poprawnie"
            return 1
        fi
    else
        log "Uruchamianie w trybie interaktywnym..."
        groovy run.groovy
    fi
}

wait_for_startup() {
    log "⏳ Oczekiwanie na uruchomienie usług..."

    local health_url="http://localhost:${HEALTH_PORT:-9090}/health"
    local hawtio_url="http://localhost:${HAWTIO_PORT:-8080}/hawtio"

    # Wait for health endpoint
    local counter=0
    while [[ $counter -lt 30 ]]; do
        if curl -f "$health_url" >/dev/null 2>&1; then
            log "✅ Health endpoint dostępny"
            break
        fi
        sleep 2
        ((counter++))
    done

    if [[ $counter -eq 30 ]]; then
        warning "Health endpoint nie odpowiada po 60 sekundach"
    fi

    # Display URLs
    log "🌐 Aplikacja dostępna pod adresami:"
    echo -e "   📊 Hawtio Dashboard: ${BLUE}$hawtio_url${NC}"
    echo -e "   🔧 Health Check: ${BLUE}$health_url${NC}"
    echo -e "   📈 Metrics: ${BLUE}http://localhost:${HEALTH_PORT:-9090}/metrics${NC}"
}

show_help() {
    cat << EOF
🚀 Apache Camel Groovy Validator - Start Script

Użycie:
    $0 [opcje]

Opcje:
    --background, -d    Uruchom w tle
    --help, -h         Pokaż tę pomoc
    --check, -c        Sprawdź tylko wymagania
    --verbose, -v      Szczegółowe logi

Przykłady:
    $0                 # Uruchom interaktywnie
    $0 -d              # Uruchom w tle
    $0 --check         # Sprawdź wymagania

Pliki konfiguracyjne:
    .env               Konfiguracja środowiska
    application.properties  Właściwości aplikacji

Więcej informacji: https://tom.sapletta.com
EOF
}

# Main execution
main() {
    case "${1:-}" in
        --help|-h)
            show_help
            exit 0
            ;;
        --check|-c)
            check_prerequisites
            info "✅ Wszystkie wymagania spełnione"
            exit 0
            ;;
        --verbose|-v)
            set -x
            ;;
    esac

    # Create logs directory
    mkdir -p "$(dirname "$LOG_FILE")"

    log "🎯 Rozpoczynanie uruchamiania Camel Groovy Validator..."

    check_prerequisites
    setup_environment
    start_application "$1"

    if [[ "$1" == "--background" || "$1" == "-d" ]]; then
        wait_for_startup
        log "🎉 Uruchomienie zakończone pomyślnie!"
        log "📋 Sprawdź status: ./scripts/status.sh"
        log "🛑 Zatrzymaj: ./scripts/stop.sh"
    fi
}

# Execute main function
main "$@"
