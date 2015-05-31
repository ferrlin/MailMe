name := "emailme"

version := "1.0"

scalaVersion := "2.11.6"

scalacOptions ++= Seq("-deprecation", "-feature")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.11",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.11",
  "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3.11",
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.11",
  "com.typesafe.akka" %% "akka-cluster" % "2.3.11",
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "org.springframework" % "spring-core" % "4.1.6.RELEASE",
  "org.springframework" % "spring-context" % "4.1.6.RELEASE"
)

fork := true
