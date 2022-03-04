## Akka Kubernetes Deployment Sample

Reference: https://doc.akka.io/docs/akka-management/current/bootstrap/index.html

<br>

1. Compile and build docker image

```shell
sbt docker:publishLocal
```

2. Push image to your remote register

```shell
# for example, push this image to aliyun public image register
docker tag akka-k8s-sample registry.cn-hangzhou.aliyuncs.com/assad-pub/akka-k8s-sample
docker push registry.cn-hangzhou.aliyuncs.com/assad-pub/akka-k8s-sample
```

3. Apply K8s resources config

Assuming that the namespace for resource deployment is `dev`, the relevant Service and Deployment configuration file is on: `kubernetes/akka-sample.yaml`

```
kubectl apply -f kubernetes/akka-sample.yml
```



