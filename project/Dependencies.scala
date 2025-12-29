import sbt.*

object Dependencies {
  private object Versions {
    val mongodb            = "5.6.2"
    val fs2                = "3.12.2"
    val kindProjector      = "0.13.4"
    val circe              = "0.14.15"
    val zio                = "2.1.24"
    val zioInteropReactive = "2.0.2"
    val zioJson            = "0.8.0"

    val logback   = "1.5.23"
    val scalaTest = "3.2.19"

    val embeddedMongo   = "4.22.0"
    val immutableValue  = "2.12.0"
    val commonsCompress = "1.28.0"
    val jsr305          = "3.0.2"
  }

  private object Libraries {
    val mongodbBson          = "org.mongodb" % "bson"                           % Versions.mongodb
    val mongodbDriverCore    = "org.mongodb" % "mongodb-driver-core"            % Versions.mongodb
    val mongodbDriverStreams = "org.mongodb" % "mongodb-driver-reactivestreams" % Versions.mongodb
    val mongodbDriverSync    = "org.mongodb" % "mongodb-driver-sync"            % Versions.mongodb

    val fs2Core = "co.fs2" %% "fs2-core" % Versions.fs2

    val circeCore    = "io.circe" %% "circe-core"    % Versions.circe
    val circeParser  = "io.circe" %% "circe-parser"  % Versions.circe
    val circeGeneric = "io.circe" %% "circe-generic" % Versions.circe

    val zio                = "dev.zio" %% "zio"                         % Versions.zio
    val zioStreams         = "dev.zio" %% "zio-streams"                 % Versions.zio
    val zioTest            = "dev.zio" %% "zio-test"                    % Versions.zio
    val zioTestSbt         = "dev.zio" %% "zio-test-sbt"                % Versions.zio
    val zioInteropReactive = "dev.zio" %% "zio-interop-reactivestreams" % Versions.zioInteropReactive
    val zioJson            = "dev.zio" %% "zio-json"                    % Versions.zioJson

    val scalaTest = "org.scalatest" %% "scalatest"       % Versions.scalaTest
    val logback   = "ch.qos.logback" % "logback-classic" % Versions.logback

    val embeddedMongo   = "de.flapdoodle.embed"      % "de.flapdoodle.embed.mongo" % Versions.embeddedMongo
    val immutableValue  = "org.immutables"           % "value"                     % Versions.immutableValue
    val commonsCompress = "org.apache.commons"       % "commons-compress"          % Versions.commonsCompress
    val jsr305          = "com.google.code.findbugs" % "jsr305"                    % Versions.jsr305
  }

  val kindProjector = "org.typelevel" % "kind-projector" % Versions.kindProjector

  val kernel = Seq(
    Libraries.mongodbBson,
    Libraries.mongodbDriverCore,
    Libraries.mongodbDriverStreams,
    Libraries.jsr305    % Optional,
    Libraries.scalaTest % Test
  )

  val core = Seq(
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

  val zioJson = Seq(
    Libraries.zioJson,
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
