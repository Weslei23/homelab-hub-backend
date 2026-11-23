pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 20, unit: 'MINUTES')
    }

    environment {
        IMAGE_NAME = 'homepage-backend'
        IMAGE_TAG  = "${env.BUILD_NUMBER}"
        COMPOSE_PROJECT_NAME = 'homepage'
        HOST_PORT  = '8081'
        // Ajuste para a URL real do frontend
        CORS_ALLOWED_ORIGINS = 'http://192.168.3.183:8082,http://192.168.3.183:5173'
        // Downtify 3.x (downloads de musica) no ZimaOS. O token vem da credencial abaixo
        DOWNTIFY_URL = 'http://192.168.3.182:8582'
        // Painel de filmes e series (ZimaOS). As API keys vem das credenciais abaixo
        SONARR_URL   = 'http://192.168.3.182:8989'
        RADARR_URL   = 'http://192.168.3.182:7878'
        JELLYFIN_URL = 'http://192.168.3.182:8097'
        DOCKER_BUILDKIT = '1'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        // O Dockerfile compila com Maven (JDK 25) e roda os testes dentro do build
        stage('Build & Test') {
            steps {
                sh 'docker build -t ${IMAGE_NAME}:${IMAGE_TAG} -t ${IMAGE_NAME}:latest .'
            }
        }

        stage('Deploy') {
            when { expression { env.BRANCH_NAME == 'main' || env.GIT_BRANCH in ['origin/main', 'main'] } }
            steps {
                withCredentials([
                    string(credentialsId: 'homepage-db-password',    variable: 'DB_PASSWORD'),
                    string(credentialsId: 'homepage-jwt-secret',     variable: 'JWT_SECRET'),
                    string(credentialsId: 'homepage-admin-password', variable: 'ADMIN_PASSWORD'),
                    string(credentialsId: 'homepage-admin-email',    variable: 'ADMIN_EMAIL'),
                    string(credentialsId: 'homepage-discord-webhook', variable: 'DISCORD_WEBHOOK_URL'),
                    string(credentialsId: 'homepage-downtify-token',  variable: 'DOWNTIFY_TOKEN'),
                    string(credentialsId: 'homepage-sonarr-key',      variable: 'SONARR_API_KEY'),
                    string(credentialsId: 'homepage-radarr-key',      variable: 'RADARR_API_KEY'),
                    string(credentialsId: 'homepage-jellyfin-key',    variable: 'JELLYFIN_API_KEY')
                ]) {
                    sh 'docker compose -f compose.prod.yaml up -d --remove-orphans'
                }
            }
        }

        stage('Smoke test') {
            when { expression { env.BRANCH_NAME == 'main' || env.GIT_BRANCH in ['origin/main', 'main'] } }
            steps {
                sh '''
                    for i in $(seq 1 30); do
                        # Rota publica e leve (o Swagger fica desligado em producao)
                        if curl -fsS http://127.0.0.1:${HOST_PORT}/api/v1/auth/config > /dev/null; then
                            echo "API respondendo"; exit 0
                        fi
                        sleep 3
                    done
                    echo "API nao respondeu a tempo"
                    docker compose -f compose.prod.yaml logs --tail=80 backend || true
                    exit 1
                '''
            }
        }
    }

    post {
        always {
            sh 'docker image prune -f --filter "until=168h" || true'
        }
        success { echo "Deploy ${env.IMAGE_NAME}:${env.IMAGE_TAG} concluido" }
        failure { echo 'Pipeline falhou - veja os logs acima' }
    }
}
