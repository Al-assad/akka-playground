

name := "akka-playground"

version := "1.0"

scalaVersion := "2.13.7"

lazy val akkaVersion = "2.6.18"
lazy val akkaHttpVersion = "10.2.7"

fork := true
Test / parallelExecution := false

// The following testkit dependencies are deliberately not marked as Test
// in order to write scalatest in the sample code.
libraryDependencies ++= Seq(
  // akka actor
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion,

  // akka cluster
  "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
  "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion,

  // akka sharding
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,

  // akka persistence
  "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-testkit" % akkaVersion,

  // akka http
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,

  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion,

  // other
  "org.scalatest" %% "scalatest" % "3.2.9",
  "ch.qos.logback" % "logback-classic" % "1.2.10",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
  "com.github.nscala-time" %% "nscala-time" % "2.30.0",
)

// tapir for akka-http
val tapirVersion = "0.20.0-M4"
libraryDependencies ++= Seq(
  "tapir-core",
  "tapir-json-circe",
  "tapir-akka-http-server",
  "tapir-swagger-ui-bundle",
  "tapir-sttp-client",
).map("com.softwaremill.sttp.tapir" %% _ % tapirVersion)


// enable multiple jvm plugin
lazy val root = (project in file("."))
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)
