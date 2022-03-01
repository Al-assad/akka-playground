## Akka Application Packing, Deployment Sample



### Using sbt-native-packer plugin

`sbt-native-packer` plugin: https://github.com/sbt/sbt-native-packager

Reference: https://doc.akka.io/docs/akka/current/additional/packaging.html

1. Add the following to your `project/plugins.sbt` file:

```scala
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.4")
```

2. In your `build.sbt` enable the plugin you want. For example the `JavaAppPackaging`

```scala
enablePlugins(JavaAppPackaging)
```

  

###  Build Binary Packing

Reference: https://sbt-native-packager.readthedocs.io/en/latest/formats/universal.html

```bash
cd packing-sample
# universal zip
sbt universal:packageBin
```

The built package are in `packing-sample/target/universal/akka-playground-package-sample-1.0.zip`, The unpacked content is similar to the following:

```
.
├── bin
│   ├── akka-playground-package-sample
│   └── akka-playground-package-sample.bat
└── lib
    ├── akka-playground-package-sample.akka-playground-package-sample-1.0.jar
    ├── ch.qos.logback.logback-classic-1.2.10.jar
    ├── ch.qos.logback.logback-core-1.2.10.jar
    ├── com.fasterxml.jackson.core.jackson-annotations-2.11.4.jar
    ├── com.fasterxml.jackson.core.jackson-core-2.11.4.jar
```

let's lauch the app:

```bash
cd target/universal/akka-playground-package-sample-1.0
chmod +x bin/akka-playground-package-sample
bin/akka-playground-package-sample	
```

  

###  Build Docker Image

Reference: https://sbt-native-packager.readthedocs.io/en/latest/formats/docker.html

1. Enable the `DockerPlugin` in your `build.sbt` 

```scala
enablePlugins(DockerPlugin)
```

2. Custom the docker setting in your `build.sbt`, for example:

```scala
Docker / packageName := "akka-sample" // image name
Docker / version := "1.0" // image version
Docker / maintainer := "Al-assad <yulin.ying@outlook.com>"

dockerBaseImage := "openjdk:8-jre-alpine" // base image
dockerExposedPorts := Seq(8080) // exposed ports
Docker / daemonUser := "akka" // [Optional] daemon user for container
```

3. Build the docker image via sbt.

```bash
cd packing-sample
sbt docker:publishLocal
```

4. Let's run the docker container.

```bash
 docker run -p 8080:8080 --rm akka-sample:1.0
```

What's more, you can clean the docker image resource via:

```bash
sbt docker:publishLocal
```

You can generates a directory with the Dockerfile and environment prepared for creating a Docker image via:

```
sbt docker:stage
```

The output docker build resource and dockerfile are under `target/docker`.

There is another docker-plugin `sbt-docker` that provides more customization abilities, see: https://github.com/marcuslonnberg/sbt-docker.

​    

### Build K8s Deployment Plan

sbt-kubeyml: https://github.com/vaslabs/sbt-kubeyml

