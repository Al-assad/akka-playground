name := "akka-playground"

version := "1.0"

scalaVersion := "2.13.7"

lazy val akkaVersion = "2.6.18"
lazy val akkaHttpVersion = "10.2.7"

// Run in a separate JVM, to make sure sbt waits until all threads have
// finished before returning.
// If you want to keep the application running while executing other
// sbt tasks, consider https://github.com/spray/sbt-revolver/
fork := true

libraryDependencies ++= Seq(
  // akka actor
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  // akka http
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  // akka actor test kit
  "org.scalatest" %% "scalatest" % "3.2.9",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion,
  // other
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.github.nscala-time" %% "nscala-time" % "2.30.0",
)

val tapirVersion = "0.20.0-M4"
libraryDependencies ++= Seq(
  "tapir-core",
  "tapir-json-circe",
  "tapir-akka-http-server",
  "tapir-swagger-ui-bundle",
  "tapir-sttp-client",
).map("com.softwaremill.sttp.tapir" %% _ % tapirVersion)

