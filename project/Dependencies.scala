import sbt._

object Dependencies {
  private object Versions {
    val mongodb     = "4.4.0"
    val fs2         = "3.2.4"
    val scalaCompat = "2.6.0"
    val circe       = "0.14.1"
    val findbugsJsr305Version = "1.3.9"

    val logback   = "1.2.10"
    val scalaTest = "3.2.10"

    val embeddedMongo   = "3.2.5"
    val immutableValue  = "2.8.8"
    val commonsCompress = "1.21"
  }

  private object Libraries {
    val mongodbBson          = "org.mongodb" % "bson"                           % Versions.mongodb
    val mongodbDriverCore    = "org.mongodb" % "mongodb-driver-core"            % Versions.mongodb
    val mongodbDriverStreams = "org.mongodb" % "mongodb-driver-reactivestreams" % Versions.mongodb
    val findbugsJsr305Version = "com.google.code.findbugs" % "jsr305" % Versions.findbugsJsr305Version % Provided

    val fs2Core     = "co.fs2"                 %% "fs2-core"                % Versions.fs2
    val fs2RS       = "co.fs2"                 %% "fs2-reactive-streams"    % Versions.fs2
    val scalaCompat = "org.scala-lang.modules" %% "scala-collection-compat" % Versions.scalaCompat

    val circeCore    = "io.circe" %% "circe-core"    % Versions.circe
    val circeParser  = "io.circe" %% "circe-parser"  % Versions.circe
    val circeGeneric = "io.circe" %% "circe-generic" % Versions.circe

    val scalaTest = "org.scalatest" %% "scalatest"       % Versions.scalaTest
    val logback   = "ch.qos.logback" % "logback-classic" % Versions.logback

    val embeddedMongo   = "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % Versions.embeddedMongo
    val immutableValue  = "org.immutables"      % "value"                     % Versions.immutableValue
    val commonsCompress = "org.apache.commons"  % "commons-compress"          % Versions.commonsCompress
  }

  lazy val core = Seq(
    Libraries.mongodbBson,
    Libraries.mongodbDriverCore,
    Libraries.mongodbDriverStreams,
    Libraries.findbugsJsr305Version,
    Libraries.fs2Core,
    Libraries.fs2RS,
    Libraries.scalaCompat
  )

  lazy val test = Seq(
    Libraries.logback   % Test,
    Libraries.scalaTest % Test
  )

  lazy val examples = Seq(
    Libraries.logback
  )

  lazy val circe = Seq(
    Libraries.circeCore,
    Libraries.circeGeneric,
    Libraries.circeParser
  )

  lazy val embedded = Seq(
    Libraries.fs2Core,
    Libraries.fs2RS,
    Libraries.embeddedMongo,
    Libraries.immutableValue,
    Libraries.commonsCompress
  )
}
