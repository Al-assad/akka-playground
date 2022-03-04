name := "akka-playground-package-sample"
version := "1.0"
maintainer := "Al-assad <yulin.ying@outlook.com>"

scalaVersion := "2.13.7"

lazy val akkaVersion = "2.6.18"
lazy val akkaHttpVersion = "10.2.7"
lazy val akkaManagementVersion = "1.1.3" // akka-management requires akka-http 2.16.14 or later

fork := true
Test / parallelExecution := false

scalacOptions := Seq("-feature", "-unchecked", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.9",
  "ch.qos.logback" % "logback-classic" % "1.2.10",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",

  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,

  "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % akkaManagementVersion,
  "com.typesafe.akka" %% "akka-discovery" % akkaVersion, // override the akka-discovery dependency in akka-management-cluster-bootstrap
  "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaManagementVersion, // provide membership http endpoint

  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test
)


enablePlugins(JavaServerAppPackaging)
Compile / mainClass := Some("com.github.al.assad.akkasample.RestApp")
Universal / javaOptions ++= Seq("-Xms256m", "-Xmx512m") // [optional] override default jvm options

enablePlugins(DockerPlugin, AshScriptPlugin)
Docker / packageName := "akka-k8s-sample"
Docker / version := "1.0"
Docker / maintainer := "Al-assad <yulin.ying@outlook.com>"
dockerBaseImage := "openjdk:8-jre-alpine"
dockerExposedPorts := Seq(8080, 8558, 25520)
Docker / daemonUser := "akka"
dockerUpdateLatest := true
