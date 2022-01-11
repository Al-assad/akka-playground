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
  // logger
  "ch.qos.logback" % "logback-classic" % "1.2.3",

  // akka actor
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  // akka http
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,

  // akka actor test kit
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion,
  "org.scalatest" %% "scalatest" % "3.2.9"
)
