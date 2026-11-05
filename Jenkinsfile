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

        stage('Verify Podman') {
            steps {
                echo 'Checking Podman availability...'
                bat 'podman --version'
                bat 'podman info'
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
    }
}
