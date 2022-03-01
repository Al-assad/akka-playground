name := "akka-playground-package-sample"
version := "1.0"
maintainer := "Al-assad <yulin.ying@outlook.com>"

scalaVersion := "2.13.7"

lazy val akkaVersion = "2.6.18"
lazy val akkaHttpVersion = "10.2.7"
lazy val akkaManagementVersion = "1.1.3" // akka-management requires akka-http 2.16.14 or later

fork := true
Test / parallelExecution := false

// The following testkit dependencies are deliberately not marked as Test
// in order to write scalatest in the sample code.
libraryDependencies ++= Seq(
  // other
  "org.scalatest" %% "scalatest" % "3.2.9",
  "ch.qos.logback" % "logback-classic" % "1.2.10",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4"
)

// akka actor
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion // actor testkit
)

// akka cluster
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
  "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion // cluster multi-node testkit
)

// akka http
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion // akka http testkit
)


/**
 * native packing settings (sbt-native-packager)
 */
// enable native packing plugin
enablePlugins(JavaAppPackaging)

// packing settings
// https://sbt-native-packager.readthedocs.io/en/latest/formats/universal.html#customize
// main class setting for bin script
Compile / mainClass := Some("com.github.al.assad.akkasample.RestApp")
Compile / discoveredMainClasses := Seq() // discard the automatically found main class
Universal / javaOptions ++= Seq("-Xms256m", "-Xmx512m") // [optional] override default jvm options


/**
 * docker building settings (sbt-native-packager build-in)
 */
// enable docker building plugin
enablePlugins(DockerPlugin)
enablePlugins(AshScriptPlugin) // due to the use of openjdk alpine image, Ash support is required

// docker plugin settings
// https://sbt-native-packager.readthedocs.io/en/latest/formats/docker.html#customize
Docker / packageName := "akka-sample" // image name
Docker / version := "1.0" // image version
Docker / maintainer := "Al-assad <yulin.ying@outlook.com>"

dockerBaseImage := "openjdk:8-jre-alpine" // base image
dockerExposedPorts := Seq(8080) // exposed ports
Docker / daemonUser := "akka" // [Optional] daemon user for container
//Docker / defaultLinuxInstallLocation := "/opt/docker" // default install location

dockerUpdateLatest := true // [Optional] always update the latest image
