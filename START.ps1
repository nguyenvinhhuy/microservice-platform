# Quick Start Guide - Print when user opens project

Write-Host ""
Write-Host "╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║   🚀 MICROSERVICE PLATFORM - QUICK START GUIDE 🚀             ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""
Write-Host "📋 WHAT'S READY:" -ForegroundColor Yellow
Write-Host "  ✅ Complete Docker Compose with 20 services"
Write-Host "  ✅ Angular 21 Frontend (Tailwind CSS)"
Write-Host "  ✅ Keycloak Identity Provider"
Write-Host "  ✅ 6 Spring Boot Service Templates"
Write-Host "  ✅ PostgreSQL (per-service databases)"
Write-Host "  ✅ Kafka + Redis"
Write-Host "  ✅ MinIO Object Storage"
Write-Host "  ✅ Jenkins CI/CD"
Write-Host "  ✅ Prometheus + Grafana Monitoring"
Write-Host ""
Write-Host "🚀 QUICK START:" -ForegroundColor Yellow
Write-Host ""
Write-Host "  1️⃣  Copy environment file:" -ForegroundColor Green
Write-Host "     Copy-Item '.env.example' '.env'" -ForegroundColor Gray
Write-Host ""
Write-Host "  2️⃣  Start all services:" -ForegroundColor Green
Write-Host "     .\dev.ps1 start" -ForegroundColor Gray
Write-Host ""
Write-Host "  3️⃣  Wait 2-3 minutes for services to start" -ForegroundColor Green
Write-Host ""
Write-Host "  4️⃣  Verify all services running:" -ForegroundColor Green
Write-Host "     docker-compose ps" -ForegroundColor Gray
Write-Host ""
Write-Host "🌐 ACCESS SERVICES:" -ForegroundColor Yellow
Write-Host ""
Write-Host "  Frontend:        http://localhost:8080" -ForegroundColor Cyan
Write-Host "  Gateway API:     http://localhost:8000/swagger-ui" -ForegroundColor Cyan
Write-Host "  Keycloak:        http://localhost:8180/admin" -ForegroundColor Cyan
Write-Host "                   (admin / admin123)" -ForegroundColor Gray
Write-Host "  MinIO:           http://localhost:9001" -ForegroundColor Cyan
Write-Host "                   (minioadmin / minioadmin)" -ForegroundColor Gray
Write-Host "  Jenkins:         http://localhost:8088" -ForegroundColor Cyan
Write-Host "                   (admin / admin123)" -ForegroundColor Gray
Write-Host "  Grafana:         http://localhost:3000" -ForegroundColor Cyan
Write-Host "                   (admin / admin123)" -ForegroundColor Gray
Write-Host "  Prometheus:      http://localhost:9090" -ForegroundColor Cyan
Write-Host ""
Write-Host "📚 DOCUMENTATION:" -ForegroundColor Yellow
Write-Host ""
Write-Host "  README.md           - Complete guide"
Write-Host "  QUICK_REFERENCE.md  - Common commands"
Write-Host "  ARCHITECTURE.md     - Service dependencies"
Write-Host "  CHECKLIST.md        - Implementation plan"
Write-Host "  SETUP_COMPLETE.md   - What's been setup"
Write-Host ""
Write-Host "💡 HELPFUL COMMANDS:" -ForegroundColor Yellow
Write-Host ""
Write-Host "  View logs:"
Write-Host "    docker-compose logs -f [service-name]" -ForegroundColor Gray
Write-Host ""
Write-Host "  Stop services:"
Write-Host "    docker-compose down" -ForegroundColor Gray
Write-Host ""
Write-Host "  Reset (delete all data):"
Write-Host "    docker-compose down -v" -ForegroundColor Gray
Write-Host ""
Write-Host "  Rebuild specific service:"
Write-Host "    docker-compose up -d --build [service-name]" -ForegroundColor Gray
Write-Host ""
Write-Host "🛠️  NEXT STEPS:" -ForegroundColor Yellow
Write-Host ""
Write-Host "  1. Review ARCHITECTURE.md for service dependencies"
Write-Host "  2. Check QUICK_REFERENCE.md for useful commands"
Write-Host "  3. Read README.md for detailed setup instructions"
Write-Host "  4. Start with: .\dev.ps1 start"
Write-Host "  5. Monitor services via Grafana (localhost:3000)"
Write-Host ""
Write-Host "⚠️  SYSTEM REQUIREMENTS:" -ForegroundColor Yellow
Write-Host ""
Write-Host "  • Docker & Docker Compose 2.0+"
Write-Host "  • 8GB+ RAM"
Write-Host "  • 5GB+ disk space"
Write-Host "  • Node.js 22+ (for Angular development)"
Write-Host "  • Java 17+ (for Spring Boot development)"
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
Write-Host "Happy coding! 🎉" -ForegroundColor Green
Write-Host ""

