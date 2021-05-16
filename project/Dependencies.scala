import sbt._

object Dependencies {
  private object Versions {
    lazy val mongodb     = "4.2.3"
    lazy val fs2         = "3.0.3"
    lazy val scalaCompat = "2.4.4"
    lazy val circe       = "0.14.0-M7"

    lazy val logback        = "1.2.3"
    lazy val scalaTest      = "3.2.9"
    lazy val embeddedMongo  = "3.0.0"
    lazy val immutableValue = "2.8.8"
  }

  private object Libraries {
    lazy val mongodbBson          = "org.mongodb" % "bson"                           % Versions.mongodb
    lazy val mongodbDriverCore    = "org.mongodb" % "mongodb-driver-core"            % Versions.mongodb
    lazy val mongodbDriverStreams = "org.mongodb" % "mongodb-driver-reactivestreams" % Versions.mongodb

    lazy val fs2Core     = "co.fs2"                 %% "fs2-core"                % Versions.fs2
    lazy val scalaCompat = "org.scala-lang.modules" %% "scala-collection-compat" % Versions.scalaCompat

    lazy val circeCore    = "io.circe" %% "circe-core"    % Versions.circe
    lazy val circeParser  = "io.circe" %% "circe-parser"  % Versions.circe
    lazy val circeGeneric = "io.circe" %% "circe-generic" % Versions.circe

    lazy val scalaTest      = "org.scalatest"      %% "scalatest"                 % Versions.scalaTest
    lazy val embeddedMongo  = "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % Versions.embeddedMongo
    lazy val immutableValue = "org.immutables"      % "value"                     % Versions.immutableValue
    lazy val logback        = "ch.qos.logback"      % "logback-classic"           % Versions.logback
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
}
