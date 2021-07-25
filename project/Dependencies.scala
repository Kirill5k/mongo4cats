import sbt._

object Dependencies {
  private object Versions {
    val mongodb     = "4.3.0"
    val fs2         = "3.0.6"
    val scalaCompat = "2.5.0"
    val circe       = "0.14.1"

    val logback        = "1.2.4"
    val scalaTest      = "3.2.9"
    val embeddedMongo  = "3.0.0"
    val immutableValue = "2.8.8"
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

    val scalaTest      = "org.scalatest"      %% "scalatest"                 % Versions.scalaTest
    val embeddedMongo  = "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % Versions.embeddedMongo
    val immutableValue = "org.immutables"      % "value"                     % Versions.immutableValue
    val logback        = "ch.qos.logback"      % "logback-classic"           % Versions.logback
  }

  lazy val core = Seq(
    Libraries.mongodbBson,
    Libraries.mongodbDriverCore,
    Libraries.mongodbDriverStreams,
    Libraries.fs2Core,
    Libraries.scalaCompat
  )

  lazy val test = Seq(
    Libraries.logback        % Test,
    Libraries.scalaTest      % Test,
    Libraries.embeddedMongo  % Test,
    Libraries.immutableValue % Test
  )

  lazy val examples = Seq(
    Libraries.logback
  )

  lazy val circe = Seq(
    Libraries.circeCore,
    Libraries.circeGeneric,
    Libraries.circeParser
  )

  lazy val testkit = Seq(
    Libraries.fs2Core,
    Libraries.embeddedMongo,
    Libraries.logback
  )
}
