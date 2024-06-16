pipeline {
  agent {
    kubernetes {
      label 'maven'
    }

  }
  stages {
    stage('checkout scm') {
      agent none
      steps {
        git(url: 'http://192.168.113.121:28080/root/shop-flashsale.git', credentialsId: 'gitlab-user-pass', changelog: true, poll: false, branch: "$BRANCH_NAME")
      }
    }

    stage('unit test') {
      agent none
      steps {
        container('maven') {
          sh '''cd ${SERVICE}
mvn clean test'''
        }

      }
    }

    stage('sonarqube analysis') {
      agent none
      steps {
        withCredentials([string(credentialsId : 'sonarqube' ,variable : 'SONAR_TOKEN' ,)]) {
          withSonarQubeEnv('sonar') {
            container('maven') {
              sh '''service_name=${SERVICE#*/}
service_name=${service_name#*/}

cd ${SERVICE}
mvn sonar:sonar -Dsonar.projectKey=${service_name}
echo "mvn sonar:sonar -Dsonar.projectKey=${service_name}"'''
            }

          }

        }

        timeout(time: 1, unit: 'HOURS') {
          waitForQualityGate true
        }

      }
    }

    stage('build & push') {
      agent none
      steps {
        withCredentials([usernamePassword(credentialsId : 'harbor-user-pass' ,passwordVariable : 'DOCKER_PASSWORD' ,usernameVariable : 'DOCKER_USERNAME' ,)]) {
          container('maven') {
            sh '''cd ${SERVICE}
mvn clean package -DskipTests
cd ${WORKSPACE}
chmod -R 777 deploy/copy.sh && deploy/copy.sh'''
            sh '''echo "${DOCKER_PASSWORD}" | docker login ${REGISTRY} -u "${DOCKER_USERNAME}" --password-stdin

service_name=${SERVICE#*/}
service_name=${service_name#*/}
cd deploy/${service_name}/build

docker build -f Dockerfile -t ${REGISTRY}/${DOCKERHUB_NAMESPACE}/${service_name}:SNAPSHOT-$BUILD_NUMBER .
docker push ${REGISTRY}/${DOCKERHUB_NAMESPACE}/${service_name}:SNAPSHOT-${BUILD_NUMBER}'''
          }

        }

      }
    }

    stage('push latest') {
      agent none
      steps {
        container('maven') {
          sh '''service_name=${SERVICE#*/}
service_name=${service_name#*/}
cd deploy/${service_name}/build

docker tag ${REGISTRY}/${DOCKERHUB_NAMESPACE}/${service_name}:SNAPSHOT-${BUILD_NUMBER} ${REGISTRY}/${DOCKERHUB_NAMESPACE}/${service_name}:latest
docker push ${REGISTRY}/${DOCKERHUB_NAMESPACE}/${service_name}:latest
'''
        }

      }
    }

    stage('deploy to dev') {
      agent none
      steps {
        input(id: 'deploy-to-dev', message: 'deploy to dev?')
        container('maven') {
          withCredentials([kubeconfigContent(credentialsId : 'admin-kubeconfig' ,variable : 'ADMIN_KUBECONFIG' ,)]) {
            sh '''
service_name=${SERVICE#*/}
service_name=${service_name#*/}
cd deploy/${service_name}

sed -i\'\' "s#REGISTRY#${REGISTRY}#" deployment.yaml
sed -i\'\' "s#DOCKERHUB_NAMESPACE#${DOCKERHUB_NAMESPACE}#" deployment.yaml
sed -i\'\' "s#APP_NAME#${service_name}#" deployment.yaml
sed -i\'\' "s#BUILD_NUMBER#${BUILD_NUMBER}#" deployment.yaml
sed -i\'\' "s#REPLICAS#${REPLICAS}#" deployment.yaml

mkdir ~/.kube
echo "$ADMIN_KUBECONFIG" > ~/.kube/config

if [ ${service_name} != "frontend-server" ]; then
  kubectl create cm ${service_name}-yml --dry-run=\'client\' -oyaml --from-file=build/target/bootstrap.yml -n ks-shop-dev > ${service_name}-configmap.yml
fi

kubectl apply -f .'''
          }

        }

      }
    }

    stage('push with tag') {
      agent none
      when {
        expression {
          return params.TAG_NAME =~ /v.*/
        }

      }
      steps {
        input(id: 'release-image-with-tag', message: 'release image with tag?')
        withCredentials([usernamePassword(credentialsId : 'gitlab-user-pass' ,passwordVariable : 'GIT_PASSWORD' ,usernameVariable : 'GIT_USERNAME' ,)]) {
          sh 'git config --global user.email "liugang@wolfcode.cn" '
          sh 'git config --global user.name "xiaoliu" '
          sh 'git tag -a ${TAG_NAME} -m "${TAG_NAME}" '
          sh 'git push http://${GIT_USERNAME}:${GIT_PASSWORD}@${GIT_REPO_URL}/${GIT_ACCOUNT}/${APP_NAME}.git --tags --ipv4'
        }

        sh '''
service_name=${SERVICE#*/}
service_name=${service_name#*/}

docker tag ${REGISTRY}/${DOCKERHUB_NAMESPACE}/${service_name}:SNAPSHOT-${BUILD_NUMBER} ${REGISTRY}/${DOCKERHUB_NAMESPACE}/${service_name}:${TAG_NAME}
docker push ${REGISTRY}/${DOCKERHUB_NAMESPACE}/${service_name}:${TAG_NAME}
'''
      }
    }

    stage('deploy to production') {
      agent none
      when {
        expression {
          return params.TAG_NAME =~ /v.*/
        }

      }
      steps {
        input(id: 'deploy-to-production', message: 'deploy to production?')
        container('maven') {
          withCredentials([kubeconfigContent(credentialsId : 'admin-kubeconfig' ,variable : 'ADMIN_KUBECONFIG' ,)]) {
            sh '''
service_name=${SERVICE#*/}
service_name=${service_name#*/}
cd deploy/${service_name}/prod

sed -i\'\' "s#REGISTRY#${REGISTRY}#" deployment.yaml
sed -i\'\' "s#DOCKERHUB_NAMESPACE#${DOCKERHUB_NAMESPACE}#" deployment.yaml
sed -i\'\' "s#APP_NAME#${service_name}#" deployment.yaml
sed -i\'\' "s#BUILD_NUMBER#${BUILD_NUMBER}#" deployment.yaml
sed -i\'\' "s#REPLICAS#${REPLICAS}#" deployment.yaml

mkdir ~/.kube
echo "$ADMIN_KUBECONFIG" > ~/.kube/config

if [ ${service_name} != "frontend-server" ]; then
  kubectl create cm ${service_name}-yml --dry-run=\'client\' -oyaml --from-file=build/target/bootstrap.yml -n ks-shop-flashsale > ${service_name}-configmap.yml
fi
kubectl apply -f .
'''
          }

        }

      }
    }

  }
  environment {
    APP_NAME = 'shop-flashsale'
    DOCKER_CREDENTIAL_ID = 'harbor-user-pass'
    REGISTRY = '192.168.113.122:8858'
    GIT_REPO_URL = '192.168.113.121:28080'
    GIT_CREDENTIAL_ID = 'git-user-pass'
    GIT_ACCOUNT = 'root'
    SONAR_CREDENTIAL_ID = 'sonarqube-token'
    KUBECONFIG_CREDENTIAL_ID = '546163de-4d55-40b9-9035-83b51d91260b'
  }
  parameters {
    choice(name: 'SERVICE', choices: ['frontend-server', 'shop-parent/api-gateway', 'shop-parent/shop-uaa', 'shop-parent/shop-provider/product-server', 'shop-parent/shop-provider/flashsale-server'], description: '请选择要部署的服务')
    choice(name: 'DOCKERHUB_NAMESPACE', choices: ['snapshots', 'releases'], description: '请选择部署到哪个镜像仓库')
    choice(name: 'REPLICAS', choices: ['1', '3', '5', '7'], description: '请选择构建后的副本数')
    string(name: 'BRANCH_NAME', defaultValue: 'master', description: '请输入要构建的分支名称')
    string(name: 'TAG_NAME', defaultValue: 'snapshot', description: '部署版本：必须以 v 开头，例如：v1、v1.0.0')
  }
}
