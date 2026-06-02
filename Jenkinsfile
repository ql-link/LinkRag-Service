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
        DEPLOY_DIR = '/opt/tolink/toLink-Service'   // TODO: 本机部署目录，内含 .env 和 deploy/docker-compose.yml
    }

    stages {
        stage('Checkout') {
            steps { checkout scm }
        }

        stage('Build & Test') {
            agent {
                docker {
                    image 'maven:3.9-eclipse-temurin-17'
                    args  '-v $HOME/.m2:/root/.m2'
                    reuseNode true
                }
            }
            steps {
                sh 'mvn -B clean test'
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
                sh """
                    cd ${DEPLOY_DIR}
                    export TAG=${TAG} SPRING_PROFILES_ACTIVE=dev
                    docker compose -f deploy/docker-compose.yml up -d
                """
            }
        }
    }

    post {
        always  { sh 'docker image prune -f || true' }
        success { echo "Deployed ${IMAGE}:${TAG}" }
        failure { echo 'Build failed.' }
    }
}
