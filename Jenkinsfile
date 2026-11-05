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

        stage('Check Jenkins Environment') {
            steps {
                bat 'whoami'
                bat 'echo USERPROFILE=%USERPROFILE%'
                bat 'echo HOME=%HOME%'
            }
        }

        stage('Check Podman Files') {
            steps {
                bat 'dir "C:\\Users\\rinst\\.local\\share\\containers\\podman\\machine"'
                bat 'dir "C:\\Users\\rinst\\.ssh"'
            }
        }

        stage('Locate Podman Configuration') {
            steps {
                bat 'dir "C:\\Users\\rinst\\.config\\containers"'
                bat 'dir "C:\\Users\\rinst\\AppData\\Roaming\\containers"'
                bat 'dir "C:\\Users\\rinst\\.local\\share\\containers\\podman\\machine\\machine"'
            }
        }

        
        stage('Inspect Podman Configuration') {
            steps {
                bat 'type "C:\\Users\\rinst\\AppData\\Roaming\\containers\\podman-connections.json"'
                bat 'icacls "C:\\Users\\rinst\\.local\\share\\containers\\podman\\machine\\machine"'
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
                echo 'Building Auth Service container image...'

                bat '''
                    set "APPDATA=C:\\Users\\rinst\\AppData\\Roaming"

                    "C:\\Users\\rinst\\AppData\\Local\\Programs\\Podman\\podman.exe" build -t ecomm-auth-service:local .
                '''
            }
        }

    }
}
