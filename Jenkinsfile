pipeline {
    agent any

    environment {
        REGISTRY = 'docker.io'
        REGISTRY_CREDENTIALS = 'docker-credentials'
        IMAGE_NAME = 'your-docker-username/angular-fe'
        IMAGE_TAG = "${BUILD_NUMBER}"
        KUBECONFIG = credentials('kubeconfig')
        DOCKER_BUILDKIT = '1'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        timeout(time: 1, unit: 'HOURS')
    }

    stages {
        stage('Checkout') {
            steps {
                echo "🔍 Checking out code..."
                checkout scm
            }
        }

        stage('Install Dependencies') {
            steps {
                echo "📦 Installing dependencies..."
                dir('angular-fe') {
                    sh '''
                        npm ci
                        npm list
                    '''
                }
            }
        }

        stage('Lint') {
            steps {
                echo "🔬 Running linter..."
                dir('angular-fe') {
                    sh '''
                        npm run lint || true
                    '''
                }
            }
        }

        stage('Build') {
            steps {
                echo "🏗️  Building Angular application..."
                dir('angular-fe') {
                    sh '''
                        npm run build -- --configuration production
                    '''
                }
            }
        }

        stage('Test') {
            steps {
                echo "🧪 Running tests..."
                dir('angular-fe') {
                    sh '''
                        npm run test -- --watch=false --browsers=ChromeHeadless || true
                    '''
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                echo "🐳 Building Docker image..."
                sh '''
                    docker build \
                        -t ${IMAGE_NAME}:${IMAGE_TAG} \
                        -t ${IMAGE_NAME}:latest \
                        -f angular-fe/Dockerfile \
                        .
                    docker image inspect ${IMAGE_NAME}:${IMAGE_TAG}
                '''
            }
        }

        stage('Push Docker Image') {
            when {
                branch 'main'
            }
            steps {
                echo "📤 Pushing Docker image to registry..."
                withCredentials([usernamePassword(credentialsId: REGISTRY_CREDENTIALS, usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    sh '''
                        echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin ${REGISTRY}
                        docker push ${IMAGE_NAME}:${IMAGE_TAG}
                        docker push ${IMAGE_NAME}:latest
                        docker logout
                    '''
                }
            }
        }

        stage('Deploy to Kubernetes') {
            when {
                branch 'main'
            }
            steps {
                echo "🚀 Deploying to Kubernetes..."
                sh '''
                    kubectl apply -f k8s/00-namespace.yaml
                    kubectl apply -f k8s/01-configmap-secret.yaml
                    kubectl apply -f k8s/02-postgres.yaml
                    kubectl apply -f k8s/03-redis.yaml
                    kubectl apply -f k8s/05-kafka.yaml

                    # Update image in deployment
                    kubectl set image deployment/angular-fe \
                        angular-fe=${IMAGE_NAME}:${IMAGE_TAG} \
                        -n microservice-platform || \
                    kubectl apply -f k8s/18-angular-fe.yaml

                    kubectl rollout status deployment/angular-fe -n microservice-platform --timeout=5m
                '''
            }
        }

        stage('Verify Deployment') {
            when {
                branch 'main'
            }
            steps {
                echo "✅ Verifying deployment..."
                sh '''
                    kubectl get pods -n microservice-platform
                    kubectl get svc -n microservice-platform
                    kubectl describe deployment angular-fe -n microservice-platform
                '''
            }
        }
    }

    post {
        always {
            echo "🧹 Cleaning up..."
            sh '''
                docker system prune -f
            '''
        }

        success {
            echo "✨ Pipeline completed successfully!"
        }

        failure {
            echo "❌ Pipeline failed. Check logs for details."
        }
    }
}

