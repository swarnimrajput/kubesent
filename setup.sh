#!/bin/bash

# KubeSent Quick Setup Script
# This script helps you get KubeSent running quickly

set -e

echo "======================================"
echo "   KubeSent Setup Script"
echo "======================================"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check prerequisites
echo "Checking prerequisites..."

# Check Java
if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ Java not found. Please install Java 17+${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Java found: $(java -version 2>&1 | head -n 1)${NC}"

# Check Maven
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ Maven not found. Please install Maven 3.6+${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Maven found: $(mvn -version | head -n 1)${NC}"

# Check Python
if ! command -v python3 &> /dev/null; then
    echo -e "${RED}❌ Python3 not found. Please install Python 3.9+${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Python found: $(python3 --version)${NC}"

# Check Minikube
if ! command -v minikube &> /dev/null; then
    echo -e "${YELLOW}⚠️  Minikube not found. Install it from https://minikube.sigs.k8s.io/${NC}"
else
    echo -e "${GREEN}✅ Minikube found${NC}"
fi

# Check kubectl
if ! command -v kubectl &> /dev/null; then
    echo -e "${YELLOW}⚠️  kubectl not found. Install it for Kubernetes management${NC}"
else
    echo -e "${GREEN}✅ kubectl found${NC}"
fi

echo ""
echo "======================================"
echo "   Setting up Python AI Agent"
echo "======================================"

cd ai-agent-python

# Create virtual environment
if [ ! -d "venv" ]; then
    echo "Creating Python virtual environment..."
    python3 -m venv venv
fi

# Activate virtual environment
source venv/bin/activate

# Install dependencies
echo "Installing Python dependencies..."
pip install -q --upgrade pip
pip install -q -r requirements.txt

# Set up .env file
if [ ! -f ".env" ]; then
    echo "Creating .env file..."
    cp .env.example .env
    echo -e "${YELLOW}"
    echo "⚠️  IMPORTANT: Edit ai-agent-python/.env and add your GEMINI_API_KEY"
    echo "   Get your API key from: https://makersuite.google.com/app/apikey"
    echo -e "${NC}"
fi

cd ..

echo ""
echo "======================================"
echo "   Setting up Java Operator"
echo "======================================"

cd k8s-operator-java

# Build the project
echo "Building Java project (this may take a few minutes)..."
mvn clean install -q -DskipTests

cd ..

echo ""
echo "======================================"
echo "   Setup Complete! 🚀"
echo "======================================"
echo ""
echo "Next steps:"
echo ""
echo "1. Configure your Gemini API key:"
echo "   ${YELLOW}nano ai-agent-python/.env${NC}"
echo ""
echo "2. Start Minikube (if not running):"
echo "   ${YELLOW}minikube start${NC}"
echo ""
echo "3. Start the AI Agent (in one terminal):"
echo "   ${YELLOW}cd ai-agent-python && source venv/bin/activate && uvicorn app.main:app --reload --port 8000${NC}"
echo ""
echo "4. Start the Java Operator (in another terminal):"
echo "   ${YELLOW}cd k8s-operator-java && mvn spring-boot:run${NC}"
echo ""
echo "5. Test with an OOMKilled pod:"
echo "   ${YELLOW}kubectl apply -f k8s-manifests/test-oomkilled-pod.yaml${NC}"
echo ""
echo "For more details, see README.md"
echo ""
