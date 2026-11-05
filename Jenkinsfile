pipeline {
    agent any

    tools {
        jdk 'jdk-18'
    }

    stages {

        stage('Verify Java') {
            steps {
                echo 'Checking Java version...'
                bat 'java -version'
                bat 'echo JAVA_HOME=%JAVA_HOME%'
            }
        }

        
        stage('Determine Image Metadata') {
            steps {
                script {
                    // 1. Get repository URL from Git
                    def remoteUrl = bat(
                        script: '@git config --get remote.origin.url',
                        returnStdout: true
                    ).trim()

                    // Extract repository name
                    def repoName = remoteUrl
                        .replace('\\', '/')
                        .tokenize('/')
                        .last()
                        .replaceFirst(/\.git$/, '')

                    // Normalize repository name for container image
                    repoName = repoName
                        .toLowerCase()
                        .replaceAll(/[^a-z0-9]+/, '-')
                        .replaceAll(/^-+|-+$/, '')

                    // 2. Get Git branch name
                    def branch = env.BRANCH_NAME ?: env.GIT_BRANCH

                    if (!branch) {
                        branch = bat(
                            script: '@git rev-parse --abbrev-ref HEAD',
                            returnStdout: true
                        ).trim()
                    }

                    // Remove common Git prefixes
                    branch = branch.replaceFirst(/^refs\/heads\//, '')
                    branch = branch.replaceFirst(/^origin\//, '')

                    // 3. Normalize branch name for image tag
                    def tag = branch
                        .toLowerCase()
                        .replaceAll(/[^a-z0-9_.-]+/, '-')
                        .replaceAll(/^[.-]+|[.-]+$/, '')

                    if (!repoName || !tag) {
                        error('Invalid repository name or branch name.')
                    }

                    tag = tag.take(128)

                    // 4. Export metadata for subsequent stages
                    env.REPO_NAME = repoName
                    env.IMAGE_TAG = tag
                    env.IMAGE_NAME = "localhost/${repoName}"

                    echo "Repository: ${env.REPO_NAME}"
                    echo "Branch: ${branch}"
                    echo "Image: ${env.IMAGE_NAME}:${env.IMAGE_TAG}"
                }
            }
        }


        
        stage('Determine Image Tag') {
            steps {
                script {
                    // Support both Multibranch Pipeline and regular Pipeline jobs
                    def branch = env.BRANCH_NAME ?: env.GIT_BRANCH

                    if (!branch) {
                        branch = bat(
                            script: '@git rev-parse --abbrev-ref HEAD',
                            returnStdout: true
                        ).trim()
                    }

                    // Remove common Git remote prefixes
                    branch = branch.replaceFirst(/^refs\/heads\//, '')
                    branch = branch.replaceFirst(/^origin\//, '')

                    // Convert branch name into a valid Podman image tag
                    def tag = branch
                        .toLowerCase()
                        .replaceAll(/[^a-z0-9_.-]+/, '-')
                        .replaceAll(/^[.-]+|[.-]+$/, '')

                    if (!tag) {
                        error('Unable to generate a valid image tag from branch name.')
                    }

                    // Docker/Podman tags have a maximum length of 128 characters
                    tag = tag.take(128)

                    env.IMAGE_TAG = tag

                    echo "Git branch: ${branch}"
                    echo "Podman image tag: ${env.IMAGE_TAG}"
                }
            }
        }


        stage('Verify Podman Connection') {
            steps {
                bat '''
                    set "APPDATA=C:\\Users\\rinst\\AppData\\Roaming"
                    "C:\\Users\\rinst\\AppData\\Local\\Programs\\Podman\\podman.exe" system connection list
                    "C:\\Users\\rinst\\AppData\\Local\\Programs\\Podman\\podman.exe" info
                '''
            }
        }

        stage('Run Tests') {
            steps {
                echo 'Running automated tests...'
                bat '.\\mvnw.cmd -B test'
            }
        }

        stage('Build Application') {
            steps {
                echo 'Building Spring Boot application...'
                bat '.\\mvnw.cmd -B clean package'
            }
        }

        stage('Build Result') {
            steps {
                echo 'Build completed successfully!'
                archiveArtifacts artifacts: 'target/*.jar',
                                 fingerprint: true
            }
        }
        
        stage('Build Container Image') {
            steps {
                echo "Building ${env.IMAGE_NAME}:${env.IMAGE_TAG}..."

                bat '''
                    set "APPDATA=C:\\Users\\rinst\\AppData\\Roaming"

                    "C:\\Users\\rinst\\AppData\\Local\\Programs\\Podman\\podman.exe" build -t %IMAGE_NAME%:%IMAGE_TAG% .
                '''
            }
        }
        
        stage('Deploy to Podman') {
            steps {
                echo 'Deploying Auth Service to Podman...'

                bat '''
                    set "APPDATA=C:\\Users\\rinst\\AppData\\Roaming"
                    set "PATH=C:\\Users\\rinst\\AppData\\Local\\Programs\\Podman;%PATH%"

                    powershell.exe -NoProfile -NonInteractive ^
                        -ExecutionPolicy Bypass ^
                        -File ".\\deploy-podman.ps1"

                    if errorlevel 1 exit /b %ERRORLEVEL%
                '''
            }
        }
        
        stage('Verify Deployment') {
            steps {
                echo 'Checking Auth Service health...'

                bat '''
                    powershell.exe -NoProfile -Command "$url = 'http://localhost:8081/actuator/health'; for ($i = 0; $i -lt 30; $i++) { try { $response = Invoke-RestMethod -Uri $url -TimeoutSec 5; if ($response.status -eq 'UP') { Write-Host 'Auth Service is HEALTHY'; exit 0 } } catch { Write-Host 'Waiting for Auth Service...' }; Start-Sleep -Seconds 2 }; throw 'Auth Service health check failed'"
                '''
            }
        }

    }
}
