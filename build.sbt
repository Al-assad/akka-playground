

name := "akka-playground"

version := "1.0"

scalaVersion := "2.13.7"

lazy val akkaVersion = "2.6.18"
lazy val akkaHttpVersion = "10.2.7"
lazy val slickVersion = "3.3.3"

fork := true
Test / parallelExecution := false

// The following testkit dependencies are deliberately not marked as Test
// in order to write scalatest in the sample code.
libraryDependencies ++= Seq(
  // akka actor
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion, // actor testkit

  // akka cluster
  "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
  "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion, // cluster multi-node testkit

  // akka sharding
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,

  // akka persistence
  "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-testkit" % akkaVersion, // akka persistence testkit
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion, // akka persistence query

  // akka persistence - jdbc backend plugin
  "com.lightbend.akka" %% "akka-persistence-jdbc" % "5.0.4",
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
  "com.typesafe.slick" %% "slick" % slickVersion,
  "com.typesafe.slick" %% "slick-hikaricp" % slickVersion,
  "org.postgresql" % "postgresql" % "42.3.3", // use postgresql as backend
  "com.h2database" % "h2" % "2.1.210", // use h2 as backend during test, maybe marked as %Test

  // akka streams
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion, // akka streams testkit

  // akka http
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
//  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion, // akka http testkit
  "com.github.swagger-akka-http" %% "swagger-akka-http" % "2.6.0", // swagger-akka-http
  "com.github.swagger-akka-http" %% "swagger-scala-module" % "2.5.2",
  "io.swagger.core.v3" % "swagger-jaxrs2-jakarta" % "2.1.12",
  "jakarta.ws.rs" % "jakarta.ws.rs-api" % "3.0.0",

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


// akka-http-swagger

// enable multiple jvm plugin
lazy val root = (project in file("."))
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)
