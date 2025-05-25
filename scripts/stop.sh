
#!/bin/bash

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
PID_FILE="$PROJECT_DIR/camel-validator.pid"

log() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1" >&2
}

warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log "🛑 Zatrzymywanie Apache Camel Groovy Validator..."

# Stop by PID file
if [[ -f "$PID_FILE" ]]; then
    local pid=$(cat "$PID_FILE")
    if kill -0 "$pid" 2>/dev/null; then
        log "Zatrzymywanie procesu PID: $pid"
        kill "$pid"

        # Wait for graceful shutdown
        local counter=0
        while kill -0 "$pid" 2>/dev/null && [[ $counter -lt 30 ]]; do
            sleep 1
            ((counter++))
        done

        if kill -0 "$pid" 2>/dev/null; then
            warning "Wymuszanie zatrzymania..."
            kill -9 "$pid"
        fi

        rm -f "$PID_FILE"
        log "✅ Aplikacja zatrzymana"
    else
        warning "Proces PID $pid nie działa"
        rm -f "$PID_FILE"
    fi
else
    # Find and kill by process name
    local pids=$(pgrep -f "run.groovy")
    if [[ -n "$pids" ]]; then
        log "Zatrzymywanie procesów: $pids"
        echo "$pids" | xargs kill
        log "✅ Aplikacja zatrzymana"
    else
        warning "Nie znaleziono działających procesów"
    fi
fi