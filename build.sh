#!/bin/bash

# LangGraph4J Engine Build Script

set -e

echo "========================================"
echo "Building LangGraph4J Engine"
echo "========================================"

# Check Java version
if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" != "17" ]; then
    echo "Warning: Java 17 is recommended (found version $JAVA_VERSION)"
fi

# Check Maven
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed"
    echo "Please install Maven 3.8+ to build the project"
    exit 1
fi

echo ""
echo "Building Backend..."
echo "-------------------"
cd backend
mvn clean package -DskipTests
cd ..

echo ""
echo "Building Frontend..."
echo "--------------------"
cd frontend
if ! command -v npm &> /dev/null; then
    echo "Error: Node.js is not installed"
    exit 1
fi

npm install
npm run build
cd ..

echo ""
echo "========================================"
echo "Build completed successfully!"
echo "========================================"
echo ""
echo "To start the application:"
echo "  1. Backend:  java -jar backend/target/langgraph4j-engine-1.0.0-SNAPSHOT.jar"
echo "  2. Frontend: cd frontend && npm run dev"
echo ""
echo "Or use Docker Compose:"
echo "  docker-compose up -d"
