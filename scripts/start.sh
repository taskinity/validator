#!/bin/bash
echo "🚀 Starting Camel Groovy Validator..."

# Check if groovy is installed
if ! command -v groovy &> /dev/null; then
    echo "❌ Groovy not found. Installing..."
    # Add installation logic here
fi

# Load environment
if [ -f .env ]; then
    export $(cat .env | xargs)
    echo "✅ Loaded .env configuration"
fi

# Create data directories
mkdir -p data/{input,output,error}
mkdir -p logs

# Start application
groovy run.groovy