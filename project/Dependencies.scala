import sbt._

object Dependencies {
  private object Versions {
    val mongodb     = "4.7.1"
    val fs2         = "3.2.11"
    val scalaCompat = "2.8.1"
    val circe       = "0.14.2"
    val zio         = "2.0.1"

    val logback   = "1.2.11"
    val scalaTest = "3.2.13"

    val embeddedMongo   = "3.4.8"
    val immutableValue  = "2.9.0"
    val commonsCompress = "1.21"
  }

  private object Libraries {
    val mongodbBson          = "org.mongodb" % "bson"                           % Versions.mongodb
    val mongodbDriverCore    = "org.mongodb" % "mongodb-driver-core"            % Versions.mongodb
    val mongodbDriverStreams = "org.mongodb" % "mongodb-driver-reactivestreams" % Versions.mongodb

    val fs2Core     = "co.fs2"                 %% "fs2-core"                % Versions.fs2
    val scalaCompat = "org.scala-lang.modules" %% "scala-collection-compat" % Versions.scalaCompat

    val circeCore    = "io.circe" %% "circe-core"    % Versions.circe
    val circeParser  = "io.circe" %% "circe-parser"  % Versions.circe
    val circeGeneric = "io.circe" %% "circe-generic" % Versions.circe

    val zio        = "dev.zio" %% "zio"          % Versions.zio
    val zioStreams = "dev.zio" %% "zio-streams"  % Versions.zio
    val zioTest    = "dev.zio" %% "zio-test"     % Versions.zio
    val zioTestSbt = "dev.zio" %% "zio-test-sbt" % Versions.zio

    val scalaTest = "org.scalatest" %% "scalatest"       % Versions.scalaTest
    val logback   = "ch.qos.logback" % "logback-classic" % Versions.logback

    val embeddedMongo   = "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % Versions.embeddedMongo
    val immutableValue  = "org.immutables"      % "value"                     % Versions.immutableValue
    val commonsCompress = "org.apache.commons"  % "commons-compress"          % Versions.commonsCompress
  }

  val core = Seq(
    Libraries.mongodbBson,
    Libraries.mongodbDriverCore,
    Libraries.mongodbDriverStreams,
    Libraries.fs2Core,
    Libraries.scalaCompat,
    Libraries.logback   % Test,
    Libraries.scalaTest % Test
  )

  val examples = Seq(
    Libraries.logback,
    Libraries.scalaTest % Test
  )

  val circe = Seq(
    Libraries.circeCore,
    Libraries.circeGeneric,
    Libraries.circeParser,
    Libraries.logback   % Test,
    Libraries.scalaTest % Test
  )

  val zio = Seq(
    Libraries.zio,
    Libraries.zioStreams,
    Libraries.zioTest    % Test,
    Libraries.zioTestSbt % Test
  )

  val embedded = Seq(
    Libraries.fs2Core,
    Libraries.embeddedMongo,
    Libraries.immutableValue,
    Libraries.commonsCompress
  )
}
