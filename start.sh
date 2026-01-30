#!/bin/bash

# Quick Start Script for KubeSent
# This script sets up Java 17 and runs both services

set -e

echo "======================================"
echo "   KubeSent Quick Start"
echo "======================================"
echo ""

# Set Java 17
export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
export JAVA_HOME=/opt/homebrew/opt/openjdk@17

echo "Using Java 17:"
java -version
echo ""

echo "======================================"
echo "   Choose an option:"
echo "======================================"
echo "1. Start Python AI Agent only (for testing AI analysis)"
echo "2. Start Java Operator only (requires AI Agent running)"
echo "3. Setup environment"
echo ""
read -p "Enter choice [1-3]: " choice

case $choice in
    1)
        echo "Starting Python AI Agent..."
        cd ai-agent-python
        
        if [ ! -d "venv" ]; then
            echo "Creating virtual environment..."
            python3 -m venv venv
        fi
        
        source venv/bin/activate
        pip install -q -r requirements.txt
        
        if [ ! -f ".env" ]; then
            cp .env.example .env
            echo ""
            echo "⚠️  Please edit ai-agent-python/.env and add your GEMINI_API_KEY"
            echo "   Then run this script again."
            exit 1
        fi
        
        echo "Starting FastAPI server on port 8000..."
        uvicorn app.main:app --reload --port 8000
        ;;
        
    2)
        echo "Starting Java Operator..."
        cd k8s-operator-java
        
        echo "Building project (if needed)..."
        mvn clean install -DskipTests
        
        echo "Starting Spring Boot application..."
        mvn spring-boot:run
        ;;
        
    3)
        echo "Setting up environment..."
        
        # Python setup
        cd ai-agent-python
        if [ ! -d "venv" ]; then
            python3 -m venv venv
        fi
        source venv/bin/activate
        pip install -r requirements.txt
        
        if [ ! -f ".env" ]; then
            cp .env.example .env
        fi
        cd ..
        
        # Java build
        cd k8s-operator-java
        mvn clean install -DskipTests
        cd ..
        
        echo ""
        echo "✅ Setup complete!"
        echo ""
        echo "Next steps:"
        echo "1. Edit ai-agent-python/.env and add your GEMINI_API_KEY"
        echo "2. Start Minikube: minikube start"
        echo "3. Run this script again and choose option 1 or 2"
        ;;
        
    *)
        echo "Invalid choice"
        exit 1
        ;;
esac
