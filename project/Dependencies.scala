import sbt._

object Dependencies {
  private object Versions {
    val kindProjector      = "0.13.2"
    val cats               = "2.9.0"
    val catsEffect         = "3.4.8"
    val mongodb            = "4.9.0"
    val fs2                = "3.6.1"
    val circe              = "0.14.5"
    val zio                = "2.0.9"
    val zioInteropReactive = "2.0.1"

    val logback   = "1.4.5"
    val scalaTest = "3.2.15"

    val embeddedMongo   = "4.6.0"
    val immutableValue  = "2.9.3"
    val commonsCompress = "1.22"
  }

  private object Libraries {
    val mongodbBson          = "org.mongodb" % "bson"                           % Versions.mongodb
    val mongodbDriverCore    = "org.mongodb" % "mongodb-driver-core"            % Versions.mongodb
    val mongodbDriverStreams = "org.mongodb" % "mongodb-driver-reactivestreams" % Versions.mongodb
    val mongodbDriverSync    = "org.mongodb" % "mongodb-driver-sync"            % Versions.mongodb

    val cats       = "org.typelevel" %% "cats-core"   % Versions.cats
    val catsEffect = "org.typelevel" %% "cats-effect" % Versions.catsEffect
    val fs2Core    = "co.fs2"        %% "fs2-core"    % Versions.fs2

    val circeCore    = "io.circe" %% "circe-core"    % Versions.circe
    val circeParser  = "io.circe" %% "circe-parser"  % Versions.circe
    val circeGeneric = "io.circe" %% "circe-generic" % Versions.circe

    val zio                = "dev.zio" %% "zio"                         % Versions.zio
    val zioStreams         = "dev.zio" %% "zio-streams"                 % Versions.zio
    val zioTest            = "dev.zio" %% "zio-test"                    % Versions.zio
    val zioTestSbt         = "dev.zio" %% "zio-test-sbt"                % Versions.zio
    val zioInteropReactive = "dev.zio" %% "zio-interop-reactivestreams" % Versions.zioInteropReactive

    val scalaTest = "org.scalatest" %% "scalatest"       % Versions.scalaTest
    val logback   = "ch.qos.logback" % "logback-classic" % Versions.logback

    val embeddedMongo   = "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % Versions.embeddedMongo
    val immutableValue  = "org.immutables"      % "value"                     % Versions.immutableValue
    val commonsCompress = "org.apache.commons"  % "commons-compress"          % Versions.commonsCompress
  }

  val kindProjector = "org.typelevel" % "kind-projector" % Versions.kindProjector

  val kernel = Seq(
    Libraries.mongodbBson,
    Libraries.mongodbDriverCore,
    Libraries.mongodbDriverStreams,
    Libraries.cats,
    Libraries.scalaTest % Test
  )

  val core = Seq(
    Libraries.catsEffect,
    Libraries.fs2Core,
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
    Libraries.zioInteropReactive,
    Libraries.zioTest    % Test,
    Libraries.zioTestSbt % Test
  )

  val embedded = Seq(
    Libraries.catsEffect,
    Libraries.fs2Core,
    Libraries.mongodbDriverSync,
    Libraries.embeddedMongo,
    Libraries.immutableValue,
    Libraries.commonsCompress
  )

  val zioEmbedded = Seq(
    Libraries.zio,
    Libraries.mongodbDriverSync,
    Libraries.embeddedMongo,
    Libraries.immutableValue,
    Libraries.commonsCompress
  )
}
