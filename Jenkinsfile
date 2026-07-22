pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        IMAGE      = 'tolink-service'
        TAG        = "${env.GIT_COMMIT?.take(8) ?: env.BUILD_NUMBER}"
        DEPLOY_DIR = '/opt/tolink/toLink-Service'   // Compose 由 Jenkins 更新，本机密钥文件长期保留
        SERVICE_SECRET_CONFIG_FILE = '/opt/tolink/toLink-Service/config/application-prod-local.yml'
        SERVICE_SECRET_CONFIG_NAME = 'application-prod-local.yml'
        HOST_VPN_IP = '100.86.10.52'
        RECALL_SESSION_STREAM_BASE_URL = 'http://117.72.214.40:8000'
        OBSERVABILITY_LOKI_BASE_URL = 'http://100.86.10.52:3100'
    }

    stages {
        stage('Checkout') {
            steps { checkout scm }
        }

        stage('Build') {
            agent {
                docker {
                    image 'maven:3.9-eclipse-temurin-17'
                    args  '-v $HOME/.m2:/root/.m2'
                    reuseNode true
                }
            }
            steps {
                sh 'mvn -B clean package -DskipTests'
            }
        }

        stage('Build Image') {
            steps {
                // 本机构建镜像，打两个 tag：commit 和 latest
                sh "DOCKER_BUILDKIT=1 docker build -t ${IMAGE}:${TAG} -t ${IMAGE}:latest ."
            }
        }

        stage('Deploy') {
            steps {
                // 同机部署：镜像已在本机 docker 中，compose 直接按名引用
                sh '''
                    install -d "$DEPLOY_DIR/deploy" "$DEPLOY_DIR/config" "$DEPLOY_DIR/logs"
                    cmp -s deploy/docker-compose.yml "$DEPLOY_DIR/deploy/docker-compose.yml" || \
                        install -m 0644 deploy/docker-compose.yml "$DEPLOY_DIR/deploy/docker-compose.yml"

                    test -r "$SERVICE_SECRET_CONFIG_FILE" || {
                        echo "Missing or unreadable service secret config: $SERVICE_SECRET_CONFIG_FILE"
                        exit 14
                    }
                    test "$(stat -c '%a' "$SERVICE_SECRET_CONFIG_FILE")" = "600" || {
                        echo "Service secret config must use mode 600: $SERVICE_SECRET_CONFIG_FILE"
                        exit 15
                    }

                    cd "$DEPLOY_DIR"
                    export TAG SPRING_PROFILES_ACTIVE=prod
                    export HOST_VPN_IP RECALL_SESSION_STREAM_BASE_URL OBSERVABILITY_LOKI_BASE_URL
                    export SERVICE_SECRET_CONFIG_FILE SERVICE_SECRET_CONFIG_NAME
                    docker compose -f deploy/docker-compose.yml up -d
                '''
            }
        }
    }

    post {
        always  { sh 'docker image prune -f || true' }
        success { echo "Deployed ${IMAGE}:${TAG}" }
        failure { echo 'Build failed.' }
    }
}
