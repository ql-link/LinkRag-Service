pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        REGISTRY    = 'registry.example.com/tolink'   // TODO: 改成你的镜像仓库
        IMAGE_NAME  = 'tolink-service'
        IMAGE       = "${REGISTRY}/${IMAGE_NAME}"
        TAG         = "${env.GIT_COMMIT?.take(8) ?: env.BUILD_NUMBER}"
        DEPLOY_HOST = 'deploy@your-server'             // TODO: 部署目标主机
        DEPLOY_DIR  = '/opt/tolink/toLink-Service'
    }

    stages {
        stage('Checkout') {
            steps { checkout scm }
        }

        stage('Build & Test') {
            agent {
                docker {
                    image 'maven:3.9-eclipse-temurin-17'
                    args  '-v $HOME/.m2:/root/.m2'   // 复用 Maven 本地仓库缓存
                    reuseNode true
                }
            }
            steps {
                sh 'mvn -B clean test'
            }
        }

        stage('Build Image') {
            steps {
                sh "DOCKER_BUILDKIT=1 docker build -t ${IMAGE}:${TAG} -t ${IMAGE}:latest ."
            }
        }

        stage('Push Image') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'registry-cred',
                        usernameVariable: 'REG_USER', passwordVariable: 'REG_PASS')]) {
                    sh '''
                        echo "$REG_PASS" | docker login ${REGISTRY%%/*} -u "$REG_USER" --password-stdin
                        docker push ${IMAGE}:${TAG}
                        docker push ${IMAGE}:latest
                    '''
                }
            }
        }

        stage('Deploy') {
            steps {
                sshagent(credentials: ['deploy-ssh-key']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${DEPLOY_HOST} '
                            cd ${DEPLOY_DIR} &&
                            export REGISTRY=${REGISTRY} TAG=${TAG} SPRING_PROFILES_ACTIVE=dev &&
                            docker compose -f deploy/docker-compose.yml pull &&
                            docker compose -f deploy/docker-compose.yml up -d
                        '
                    """
                }
            }
        }
    }

    post {
        always  { sh 'docker image prune -f || true' }
        success { echo "Deployed ${IMAGE}:${TAG}" }
        failure { echo 'Build failed.' }
    }
}
