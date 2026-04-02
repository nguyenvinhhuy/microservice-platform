#!/bin/bash
# Development & Local Testing Scripts

set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

# Check prerequisites
check_requirements() {
    print_header "Checking Requirements"

    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed"
        exit 1
    fi
    print_success "Docker is installed"

    if ! command -v docker-compose &> /dev/null; then
        print_error "Docker Compose is not installed"
        exit 1
    fi
    print_success "Docker Compose is installed"

    if ! command -v npm &> /dev/null; then
        print_error "Node.js/npm is not installed"
        exit 1
    fi
    print_success "Node.js/npm is installed: $(npm --version)"
}

# Build Angular app locally
build_local() {
    print_header "Building Angular App Locally"
    cd "$PROJECT_DIR/angular-fe"
    npm run build
    cd "$PROJECT_DIR"
    print_success "Angular app built successfully"
}

# Build Docker image
build_docker() {
    print_header "Building Docker Image"
    docker build -t angular-fe:latest -f angular-fe/Dockerfile .
    print_success "Docker image built successfully"
    docker images | grep angular-fe
}

# Start services with Docker Compose
start_services() {
    print_header "Starting Services with Docker Compose"
    docker-compose up -d
    sleep 5

    print_header "Checking Service Status"
    docker-compose ps
    print_success "All services started successfully"
}

# Stop services
stop_services() {
    print_header "Stopping Services"
    docker-compose down
    print_success "All services stopped"
}

# View logs
view_logs() {
    print_header "Viewing Logs"
    service=$1
    if [ -z "$service" ]; then
        docker-compose logs -f
    else
        docker-compose logs -f "$service"
    fi
}

# Test connectivity
test_connectivity() {
    print_header "Testing Service Connectivity"

    echo -e "${YELLOW}Testing Frontend (Angular)...${NC}"
    curl -s http://localhost:8080/ | head -n 5 && print_success "Frontend is accessible" || print_error "Frontend is not accessible"

    echo -e "${YELLOW}Testing PostgreSQL...${NC}"
    docker-compose exec -T postgres pg_isready -U admin && print_success "PostgreSQL is accessible" || print_error "PostgreSQL is not accessible"

    echo -e "${YELLOW}Testing Redis...${NC}"
    docker-compose exec -T redis redis-cli ping && print_success "Redis is accessible" || print_error "Redis is not accessible"

    echo -e "${YELLOW}Testing Kafka...${NC}"
    docker-compose exec -T kafka kafka-broker-api-versions.sh --bootstrap-server kafka:29092 && print_success "Kafka is accessible" || print_error "Kafka is not accessible"
}

# Clean up Docker resources
clean_docker() {
    print_header "Cleaning Docker Resources"
    docker-compose down -v
    docker system prune -f
    print_success "Docker resources cleaned"
}

# Initialize environment
init_env() {
    print_header "Initializing Environment"
    if [ ! -f ".env" ]; then
        cp .env.example .env
        print_success ".env file created from .env.example"
    else
        print_warning ".env file already exists"
    fi
}

# Main menu
show_menu() {
    echo ""
    echo -e "${BLUE}Available Commands:${NC}"
    echo "1. check-requirements  - Check if Docker and Docker Compose are installed"
    echo "2. build-local         - Build Angular app locally"
    echo "3. build-docker        - Build Docker image"
    echo "4. start               - Start all services"
    echo "5. stop                - Stop all services"
    echo "6. logs [service]      - View service logs (optional service name)"
    echo "7. test                - Test service connectivity"
    echo "8. clean               - Clean Docker resources"
    echo "9. init-env            - Initialize .env file"
    echo "10. help               - Show this menu"
    echo ""
}

# Parse arguments
case "${1:-help}" in
    check-requirements)
        check_requirements
        ;;
    build-local)
        build_local
        ;;
    build-docker)
        build_docker
        ;;
    start)
        init_env
        check_requirements
        build_docker
        start_services
        test_connectivity
        ;;
    stop)
        stop_services
        ;;
    logs)
        view_logs "$2"
        ;;
    test)
        test_connectivity
        ;;
    clean)
        clean_docker
        ;;
    init-env)
        init_env
        ;;
    help|*)
        show_menu
        ;;
esac
