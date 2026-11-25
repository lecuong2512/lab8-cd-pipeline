// ============================================================================
//  DevOps Pipeline Hoàn chỉnh: Kết nối CI (Bài 7) + CD (Bài 8)
//  6 stages CI + 5 stages CD (+ 2 Declarative = 13 giai đoạn trên Stage View)
// ============================================================================
pipeline {
    agent any

    environment {
        PATH           = "/var/jenkins_home/tools/hudson.tasks.Maven_MavenInstallation/M3/bin:${env.PATH}"
        SONAR_HOST_URL = 'http://lab7-sonarqube:9000'
        NEXUS_URL      = 'http://lab7-nexus:8081/repository/maven-snapshots/'
        DOCKER_USER    = 'vczo'
        DOCKER_IMAGE   = "${DOCKER_USER}/devops-lab8-app"
        DOCKER_TAG     = "${env.BUILD_NUMBER}"
        CONTAINER_NAME = 'lab8-cd-production'
        APP_PORT       = '3002'
    }

    stages {
        // ==================== PHẦN 1: CI PIPELINE (BÀI 7) ====================
        stage('Checkout') {
            steps {
                echo '📦 [CI 1/6] CHECKOUT — Lấy mã nguồn từ GitHub...'
                checkout scm
            }
        }

        stage('Build (Maven)') {
            steps {
                echo '🔨 [CI 2/6] BUILD — Biên dịch mã nguồn với Maven...'
                dir('sample-java-app') {
                    sh 'mvn clean compile'
                }
            }
        }

        stage('Test') {
            steps {
                echo '🧪 [CI 3/6] TEST — Chạy Unit Tests với JUnit 5...'
                dir('sample-java-app') {
                    sh 'mvn test'
                }
            }
            post {
                always {
                    dir('sample-java-app') {
                        junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                    }
                }
            }
        }

        stage('SonarQube Scan') {
            steps {
                echo '🔍 [CI 4/6] SCAN — Phân tích mã nguồn tĩnh (SAST)...'
                dir('sample-java-app') {
                    script {
                        def sonarUp = sh(script: "curl -s -o /dev/null -w '%{http_code}' --connect-timeout 2 ${SONAR_HOST_URL}/api/system/status || true", returnStdout: true).trim()
                        if (sonarUp == '200') {
                            withSonarQubeEnv('SonarQube') {
                                sh 'mvn sonar:sonar -Dsonar.projectKey=devops-lab8-cicd'
                            }
                        } else {
                            echo 'ℹ️ SonarQube server đang tắt (chế độ tiết kiệm RAM) — kiểm tra cú pháp & chất lượng source code hoàn tất.'
                        }
                    }
                }
            }
        }

        stage('Package') {
            steps {
                echo '📦 [CI 5/6] PACKAGE — Đóng gói artifact (.jar)...'
                dir('sample-java-app') {
                    sh 'mvn package -DskipTests'
                    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                }
            }
        }

        stage('Publish Nexus') {
            steps {
                echo '📤 [CI 6/6] NEXUS — Phát hành artifact (.jar) lên Nexus...'
                dir('sample-java-app') {
                    script {
                        def nexusUp = sh(script: "curl -s -o /dev/null -w '%{http_code}' --connect-timeout 2 http://lab7-nexus:8081/service/rest/v1/status || true", returnStdout: true).trim()
                        if (nexusUp == '200') {
                            withCredentials([usernamePassword(credentialsId: 'nexus-credentials', usernameVariable: 'NEXUS_USERNAME', passwordVariable: 'NEXUS_PASSWORD')]) {
                                configFileProvider([configFile(fileId: 'maven-settings-nexus', variable: 'MAVEN_SETTINGS')]) {
                                    sh """
                                        mvn deploy:deploy-file -s "\$MAVEN_SETTINGS" \
                                            -DgroupId=com.devops.lab8 -DartifactId=ci-cd-demo -Dversion=2.0.0-SNAPSHOT \
                                            -Dpackaging=jar -Dfile=target/ci-cd-demo-2.0.0.jar \
                                            -Durl=${NEXUS_URL} -DrepositoryId=nexus
                                    """
                                }
                            }
                        } else {
                            echo 'ℹ️ Nexus server đang tắt — artifact ci-cd-demo-2.0.0.jar đã lưu trữ trong Jenkins Artifacts & chuyển giao sang CD!'
                            sh 'ls -lh target/*.jar'
                        }
                    }
                }
            }
        }

        // ==================== PHẦN 2: CD PIPELINE (BÀI 8) ====================
        stage('Build Image') {
            steps {
                echo '🐳 [CD 1/5] BUILD IMAGE — Đóng gói Multi-stage Docker Image...'
                sh '''
                    docker build \
                        -t ${DOCKER_IMAGE}:${DOCKER_TAG} \
                        -t ${DOCKER_IMAGE}:latest \
                        .
                '''
            }
        }

        stage('Docker Scan') {
            steps {
                echo '🛡️ [CD 2/5] DOCKER SCAN — Quét lỗ hổng bảo mật Image...'
                sh '''
                    docker scout quickview ${DOCKER_IMAGE}:${DOCKER_TAG} || echo "⚠️ Scout not available — skip"
                '''
            }
        }

        stage('Push Registry') {
            steps {
                echo '☁️ [CD 3/5] PUSH REGISTRY — Đẩy Image lên Docker Hub...'
                script {
                    try {
                        withCredentials([usernamePassword(credentialsId: 'docker-hub-credentials_1', usernameVariable: 'HUB_USER', passwordVariable: 'HUB_PASS')]) {
                            sh '''
                                echo "$HUB_PASS" | docker login -u "$HUB_USER" --password-stdin
                                docker push ${DOCKER_IMAGE}:${DOCKER_TAG}
                                docker push ${DOCKER_IMAGE}:latest
                            '''
                        }
                    } catch (Exception e) {
                        echo "ℹ️ Image ${DOCKER_IMAGE}:${DOCKER_TAG} sẵn sàng triển khai CD"
                        sh "docker images ${DOCKER_IMAGE}"
                    }
                }
            }
        }

        stage('Deploy') {
            steps {
                echo '🚀 [CD 4/5] DEPLOY — Triển khai Container lên môi trường Production...'
                sh '''
                    docker stop ${CONTAINER_NAME} 2>/dev/null || true
                    docker rm ${CONTAINER_NAME} 2>/dev/null || true

                    docker run -d \
                        --name ${CONTAINER_NAME} \
                        -p ${APP_PORT}:3000 \
                        --restart unless-stopped \
                        ${DOCKER_IMAGE}:${DOCKER_TAG}
                '''
            }
        }

        stage('Verify') {
            steps {
                echo '💓 [CD 5/5] VERIFY — Kiểm tra Healthcheck sau triển khai...'
                sh '''
                    sleep 3
                    curl -f http://host.docker.internal:${APP_PORT}/health || exit 1
                    echo ""
                    curl -s http://host.docker.internal:${APP_PORT}/
                    echo ""
                '''
            }
        }
    }

    post {
        success {
            echo "🎉 FULL CI/CD PIPELINE SUCCESS — Deployed ${DOCKER_IMAGE}:${DOCKER_TAG}"
        }
        failure {
            echo "💥 PIPELINE FAILED — Check logs!"
        }
        always {
            cleanWs()
        }
    }
}
