# Development & Local Testing Scripts for Windows

param(
    [Parameter(Position = 0)]
    [ValidateSet('check-requirements', 'build-local', 'build-docker', 'start', 'stop', 'logs', 'test', 'clean', 'init-env', 'verify-comments', 'help')]
    [string]$Command = 'help',

    [Parameter(Position = 1)]
    [string]$Service
)

# Colors and formatting
function Write-Header {
    param([string]$Message)
    Write-Host "========================================" -ForegroundColor Blue
    Write-Host $Message -ForegroundColor Blue
    Write-Host "========================================" -ForegroundColor Blue
}

function Write-Success {
    param([string]$Message)
    Write-Host "✅ $Message" -ForegroundColor Green
}

function Write-Error {
    param([string]$Message)
    Write-Host "❌ $Message" -ForegroundColor Red
}

function Write-Warning {
    param([string]$Message)
    Write-Host "⚠️  $Message" -ForegroundColor Yellow
}

# Check prerequisites
function Check-Requirements {
    Write-Header "Checking Requirements"

    $docker = docker --version 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Docker is installed: $docker"
    } else {
        Write-Error "Docker is not installed"
        exit 1
    }

    $compose = docker-compose --version 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Docker Compose is installed: $compose"
    } else {
        Write-Error "Docker Compose is not installed"
        exit 1
    }

    $npm = npm --version 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Node.js/npm is installed: npm v$npm"
    } else {
        Write-Error "Node.js/npm is not installed"
        exit 1
    }
}

# Build Angular app locally
function Build-Local {
    Write-Header "Building Angular App Locally"
    Push-Location "$PSScriptRoot\angular-fe"
    npm run build
    Pop-Location
    Write-Success "Angular app built successfully"
}

# Build Docker image
function Build-Docker {
    Write-Header "Building Docker Image"
    docker build -t angular-fe:latest -f angular-fe/Dockerfile .
    Write-Success "Docker image built successfully"
    docker images | Select-String "angular-fe"
}

# Start services
function Start-Services {
    Write-Header "Starting Services with Docker Compose"
    docker-compose up -d
    Start-Sleep -Seconds 5

    Write-Header "Checking Service Status"
    docker-compose ps
    Write-Success "All services started successfully"
}

# Stop services
function Stop-Services {
    Write-Header "Stopping Services"
    docker-compose down
    Write-Success "All services stopped"
}

# View logs
function View-Logs {
    Write-Header "Viewing Logs"
    if ($Service) {
        docker-compose logs -f $Service
    } else {
        docker-compose logs -f
    }
}

# Test connectivity
function Test-Connectivity {
    Write-Header "Testing Service Connectivity"

    Write-Host "Testing Frontend (Angular)..." -ForegroundColor Yellow
    $response = Invoke-WebRequest -Uri "http://localhost:8080/" -ErrorAction SilentlyContinue
    if ($response.StatusCode -eq 200) {
        Write-Success "Frontend is accessible"
    } else {
        Write-Error "Frontend is not accessible"
    }

    Write-Host "Testing PostgreSQL..." -ForegroundColor Yellow
    $result = docker-compose exec -T postgres pg_isready -U admin 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Success "PostgreSQL is accessible"
    } else {
        Write-Error "PostgreSQL is not accessible"
    }

    Write-Host "Testing Redis..." -ForegroundColor Yellow
    $result = docker-compose exec -T redis redis-cli ping 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Redis is accessible"
    } else {
        Write-Error "Redis is not accessible"
    }

}

# Clean Docker resources
function Clean-Docker {
    Write-Header "Cleaning Docker Resources"
    docker-compose down -v
    docker system prune -f
    Write-Success "Docker resources cleaned"
}

# Initialize environment
function Initialize-Environment {
    Write-Header "Initializing Environment"
    if (-Not (Test-Path ".env")) {
        Copy-Item ".env.example" ".env"
        Write-Success ".env file created from .env.example"
    } else {
        Write-Warning ".env file already exists"
    }
}

# Verify Java comment format rules
function Verify-Comments {
    Write-Header "Verifying Java Comment Rules"
    & "$PSScriptRoot\scripts\verify-java-comments.ps1"
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Java comment verification failed"
        exit $LASTEXITCODE
    }
    Write-Success "Java comment verification passed"
}

# Show help
function Show-Help {
    Write-Host ""
    Write-Host "Available Commands:" -ForegroundColor Blue
    Write-Host "  .\dev.ps1 check-requirements  - Check if Docker and Docker Compose are installed"
    Write-Host "  .\dev.ps1 build-local         - Build Angular app locally"
    Write-Host "  .\dev.ps1 build-docker        - Build Docker image"
    Write-Host "  .\dev.ps1 start               - Start all services"
    Write-Host "  .\dev.ps1 stop                - Stop all services"
    Write-Host "  .\dev.ps1 logs [service]      - View service logs (optional service name)"
    Write-Host "  .\dev.ps1 test                - Test service connectivity"
    Write-Host "  .\dev.ps1 clean               - Clean Docker resources"
    Write-Host "  .\dev.ps1 init-env            - Initialize .env file"
    Write-Host "  .\dev.ps1 verify-comments     - Verify JavaDoc/comment rules from AGENTS.md"
    Write-Host "  .\dev.ps1 help                - Show this menu"
    Write-Host ""
}

# Main execution
switch ($Command) {
    'check-requirements' { Check-Requirements }
    'build-local' { Build-Local }
    'build-docker' { Build-Docker }
    'start' {
        Initialize-Environment
        Check-Requirements
        Build-Docker
        Start-Services
        Test-Connectivity
    }
    'stop' { Stop-Services }
    'logs' { View-Logs }
    'test' { Test-Connectivity }
    'clean' { Clean-Docker }
    'init-env' { Initialize-Environment }
    'verify-comments' { Verify-Comments }
    default { Show-Help }
}

