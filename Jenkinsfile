pipeline {
  agent {
    node {
      label 'maven'
    }

  }
  environment {
    DOCKER_CREDENTIAL_ID = 'dockerhub-id'
    GITHUB_CREDENTIAL_ID = 'github-sjphiChina'
    KUBECONFIG_CREDENTIAL_ID = 'demo-kubeconfig'
    REGISTRY = 'docker.io'
    DOCKERHUB_NAMESPACE = 'sjphichina'
    GITHUB_ACCOUNT = 'sjphiChina'
    SONAR_CREDENTIAL_ID = 'sonar-qube'
    BRANCH_NAME = 'atguigu-0.0.3.1'
  }

  stages {
    stage('pull code') {
      steps {
        git(credentialsId: 'github-sjphiChina', url: 'https://github.com/sjphiChina/gulimall.git', branch: "$BRANCH_NAME", changelog: true, poll: false)
        sh 'echo 正在构建 $PROJECT_NAME 版本号：$PROJECT_VRESION 将会提交给 $REGISTRY 镜像仓库'
        sh "echo 开始编译项目"
        container ('maven') {
          sh "mvn clean install -Dmaven.test.skip=true -gs `pwd`/mvn-settings.xml"
        }
      }
    }
//     stage('sonarqube 代码质量分析') {
//       steps {
//         container ('maven') {
//           withCredentials([string(credentialsId: "$SONAR_CREDENTIAL_ID", variable: 'SONAR_TOKEN')]) {
//             withSonarQubeEnv('sonar') {
//              sh "echo 当前目录 'pwd'"
//              sh "mvn sonar:sonar -gs `pwd`/mvn-settings.xml -Dsonar.branch=$BRANCH_NAME -Dsonar.login=$SONAR_TOKEN"
//             }
//           }
//           timeout(time: 1, unit: 'HOURS') {
//             waitForQualityGate abortPipeline: true
//           }
//         }
//       }
//     }
    stage ('build & push镜像') {
            steps {
                container ('maven') {
                    sh 'mvn -Dmaven.test.skip=true -gs `pwd`/mvn-settings.xml clean package'
                    sh 'cd $PROJECT_NAME && docker build -f Dockerfile -t $REGISTRY/$DOCKERHUB_NAMESPACE/$PROJECT_NAME:SNAPSHOT-$BRANCH_NAME-$BUILD_NUMBER .'
                    withCredentials([usernamePassword(passwordVariable : 'DOCKER_PASSWORD' ,usernameVariable : 'DOCKER_USERNAME' ,credentialsId : "$DOCKER_CREDENTIAL_ID" ,)]) {
                        sh 'echo "$DOCKER_PASSWORD" | docker login $REGISTRY -u "$DOCKER_USERNAME" --password-stdin'
                        sh 'docker tag $REGISTRY/$DOCKERHUB_NAMESPACE/$PROJECT_NAME:SNAPSHOT-$BRANCH_NAME-$BUILD_NUMBER $REGISTRY/$DOCKERHUB_NAMESPACE/$PROJECT_NAME:latest '
                        sh 'docker push $REGISTRY/$DOCKERHUB_NAMESPACE/$PROJECT_NAME:latest '
                    }
                }
            }
    }
    stage('deploy to dev 现在是部署到k8s') {
          steps {
//            input(id: "deploy-to-dev-$PROJECT_NAME", message: "是否将 $PROJECT_NAME 部署到k8s集群?")
            kubernetesDeploy(configs: "$PROJECT_NAME/deploy/**", enableConfigSubstitution: true, kubeconfigId: "$KUBECONFIG_CREDENTIAL_ID")
          }
        }
    stage('push with tag发布版本'){
          when{
            expression{
              return params.PROJECT_NAME =~ /v.*/
            }
          }
          steps {
              container ('maven') {
                input(id: 'release-image-with-tag', message: 'release image with tag发布当前版本镜像吗?')
                sh 'docker tag  $REGISTRY/$DOCKERHUB_NAMESPACE/$PROJECT_NAME:SNAPSHOT-$BRANCH_NAME-$BUILD_NUMBER $REGISTRY/$DOCKERHUB_NAMESPACE/$PROJECT_NAME:$PROJECT_VRESION '
                sh 'docker push  $REGISTRY/$DOCKERHUB_NAMESPACE/$PROJECT_NAME:$PROJECT_VRESION '
                  withCredentials([usernamePassword(credentialsId: "$GITHUB_CREDENTIAL_ID", passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
                    sh 'git config --global user.email "sjph.guo@gmail.com" '
                    sh 'git config --global user.name "sjphiChina" '
                    sh 'git tag -a $PROJECT_NAME-$PROJECT_VRESION -m "$PROJECT_NAME" '
                    sh 'git push http://$GIT_USERNAME:$GIT_PASSWORD@github.com/$GITHUB_ACCOUNT/gulimall.git --tags --ipv4'
                  }
          }
          }
        }
  }
  parameters {
    string(name: 'PROJECT_VRESION', defaultValue: 'v0.0.3.1', description: '')
    string(name: 'PROJECT_NAME', defaultValue: 'gulimall-auth-server', description: '')
  }
}