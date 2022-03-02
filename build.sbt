name := "akka-playground"

version := "1.0"

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
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
  "com.github.nscala-time" %% "nscala-time" % "2.30.0"
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

// akka sharding
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion
)

// akka persistence
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-testkit" % akkaVersion, // akka persistence testkit
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion // akka persistence query
)

// akka persistence - jdbc backend plugin
lazy val slickVersion = "3.3.3"
libraryDependencies ++= Seq(
  "com.lightbend.akka" %% "akka-persistence-jdbc" % "5.0.4",
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
  "com.typesafe.slick" %% "slick" % slickVersion,
  "com.typesafe.slick" %% "slick-hikaricp" % slickVersion,
  "org.postgresql" % "postgresql" % "42.3.3", // use postgresql as backend
  "com.h2database" % "h2" % "2.1.210" // use h2 as backend during test, maybe marked as %Test
)

// akka streams
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion // akka streams testkit
)

// akka http
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion // akka http testkit
)

// akka http: swagger-akka-http
libraryDependencies ++= Seq(
  "com.github.swagger-akka-http" %% "swagger-akka-http" % "2.6.0",
  "com.github.swagger-akka-http" %% "swagger-scala-module" % "2.5.2",
  "io.swagger.core.v3" % "swagger-jaxrs2-jakarta" % "2.1.12",
  "jakarta.ws.rs" % "jakarta.ws.rs-api" % "3.0.0"
)

// akka http: tapir
val tapirVersion = "0.20.0-M4"
libraryDependencies ++= Seq(
  "tapir-core",
  "tapir-json-circe",
  "tapir-akka-http-server",
  "tapir-sttp-client",
  "tapir-swagger-ui-bundle" // when using swagger ui
).map("com.softwaremill.sttp.tapir" %% _ % tapirVersion)


// akka management
libraryDependencies ++= Seq(
  "com.lightbend.akka.management" %% "akka-management" % akkaManagementVersion // provides the base http management endpoint
)


// akka examples
lazy val root = (project in file("."))
  .enablePlugins(MultiJvmPlugin) // enable multiple jvm plugin
  .configs(MultiJvm)
  .aggregate(packingSample)

// akka packing，deploy examples
lazy val packingSample = project in file("packing-sample")
