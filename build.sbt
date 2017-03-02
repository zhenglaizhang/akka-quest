name := "akka-quest"

version := "1.0"

scalaVersion := "2.12.1"


// akka core deps
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.17",
  "com.typesafe.akka" %% "akka-agent" % "2.4.17",
  "com.typesafe.akka" %% "akka-camel" % "2.4.17",
  "com.typesafe.akka" %% "akka-cluster" % "2.4.17",
  "com.typesafe.akka" %% "akka-cluster-metrics" % "2.4.17",
  "com.typesafe.akka" %% "akka-cluster-sharding" % "2.4.17",
  "com.typesafe.akka" %% "akka-cluster-tools" % "2.4.17",
  "com.typesafe.akka" %% "akka-contrib" % "2.4.17",
  "com.typesafe.akka" %% "akka-multi-node-testkit" % "2.4.17",
  "com.typesafe.akka" %% "akka-osgi" % "2.4.17",
  "com.typesafe.akka" %% "akka-persistence" % "2.4.17",
  "com.typesafe.akka" %% "akka-persistence-tck" % "2.4.17",
  "com.typesafe.akka" %% "akka-remote" % "2.4.17",
  "com.typesafe.akka" %% "akka-slf4j" % "2.4.17",
  "com.typesafe.akka" %% "akka-stream" % "2.4.17",
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.4.17",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.17",
  "com.typesafe.akka" %% "akka-distributed-data-experimental" % "2.4.17",
  "com.typesafe.akka" %% "akka-typed-experimental" % "2.4.17",
  "com.typesafe.akka" %% "akka-persistence-query-experimental" % "2.4.17"
)


// akka http deps
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http-core" % "10.0.4",
  "com.typesafe.akka" %% "akka-http" % "10.0.4",
  "com.typesafe.akka" %% "akka-http-testkit" % "10.0.4",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.4",
  "com.typesafe.akka" %% "akka-http-jackson" % "10.0.4",
  "com.typesafe.akka" %% "akka-http-xml" % "10.0.4"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream-kafka" % "0.13"
)

// LevelDB based plugin
libraryDependencies ++= Seq(
  "org.iq80.leveldb" % "leveldb" % "0.7",
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8"
)

libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick" % "3.2.0",
  "com.h2database" % "h2" % "1.4.185", // H2 driver
  // TODO: MySQL or PostgreSQL drivers
  "ch.qos.logback" % "logback-classic" % "1.1.2"
)


libraryDependencies ++= Seq(
    "org.typelevel" %% "cats" % "0.9.0"
)
