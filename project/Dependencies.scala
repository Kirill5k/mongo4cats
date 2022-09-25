import sbt._

object Dependencies {
  private object Versions {
    val kindProjector      = "0.13.2"
    val cats               = "2.8.0"
    val mongodb            = "4.7.1"
    val fs2                = "3.2.12"
    val scalaCompat        = "2.8.1"
    val circe              = "0.14.2"
    val zio                = "2.0.2"
    val zioInteropReactive = "2.0.0"

    val logback             = "1.4.0"
    val scalaTest           = "3.2.13"
    val scalaTestScalaCheck = "3.2.13.0"
    val scalaCheck          = "1.16.0"

    val embeddedMongo   = "3.4.8"
    val immutableValue  = "2.9.1"
    val commonsCompress = "1.21"

    val magnolia1_2         = "1.1.2"
    val magnolia1_3         = "1.1.4"
    val scalacheckShapeless = "1.3.0"
    val scalacheckCats      = "0.3.1"
  }

  private object Libraries {
    val mongodbBson          = "org.mongodb" % "bson"                           % Versions.mongodb
    val mongodbDriverCore    = "org.mongodb" % "mongodb-driver-core"            % Versions.mongodb
    val mongodbDriverStreams = "org.mongodb" % "mongodb-driver-reactivestreams" % Versions.mongodb

    val cats        = "org.typelevel"          %% "cats-core"               % Versions.cats
    val fs2Core     = "co.fs2"                 %% "fs2-core"                % Versions.fs2
    val scalaCompat = "org.scala-lang.modules" %% "scala-collection-compat" % Versions.scalaCompat

    val circeCore          = "io.circe" %% "circe-core"           % Versions.circe
    val circeParser        = "io.circe" %% "circe-parser"         % Versions.circe
    val circeGeneric       = "io.circe" %% "circe-generic"        % Versions.circe
    val circeGenericExtras = "io.circe" %% "circe-generic-extras" % Versions.circe

    val zio                = "dev.zio" %% "zio"                         % Versions.zio
    val zioStreams         = "dev.zio" %% "zio-streams"                 % Versions.zio
    val zioTest            = "dev.zio" %% "zio-test"                    % Versions.zio
    val zioTestSbt         = "dev.zio" %% "zio-test-sbt"                % Versions.zio
    val zioInteropReactive = "dev.zio" %% "zio-interop-reactivestreams" % Versions.zioInteropReactive

    val scalaTest           = "org.scalatest"     %% "scalatest"       % Versions.scalaTest
    val scalaTestScalaCheck = "org.scalatestplus" %% "scalacheck-1-16" % Versions.scalaTestScalaCheck
    val scalaCheck          = "org.scalacheck"    %% "scalacheck"      % Versions.scalaCheck
    val logback             = "ch.qos.logback"     % "logback-classic" % Versions.logback

    val embeddedMongo   = "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % Versions.embeddedMongo
    val immutableValue  = "org.immutables"      % "value"                     % Versions.immutableValue
    val commonsCompress = "org.apache.commons"  % "commons-compress"          % Versions.commonsCompress

    val magnolia1_2 = "com.softwaremill.magnolia1_2" %% "magnolia" % Versions.magnolia1_2
    val magnolia1_3 = "com.softwaremill.magnolia1_3" %% "magnolia" % Versions.magnolia1_3

    val scalacheckShapeless = "com.github.alexarchambault" %% "scalacheck-shapeless_1.15" % Versions.scalacheckShapeless

    val scalacheckCats = "io.chrisdavenport" %% "cats-scalacheck" % Versions.scalacheckCats
  }

  val kindProjector = "org.typelevel" % "kind-projector" % Versions.kindProjector

  val kernel = Seq(
    Libraries.mongodbBson,
    Libraries.mongodbDriverCore,
    Libraries.mongodbDriverStreams,
    Libraries.scalaCompat,
    Libraries.cats,
    Libraries.scalaTest % Test
  )

  val core = Seq(
    Libraries.fs2Core,
    Libraries.logback             % Test,
    Libraries.scalaTest           % Test,
    Libraries.scalaTestScalaCheck % Test,
    Libraries.scalaCheck          % Test
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
    Libraries.fs2Core,
    Libraries.embeddedMongo,
    Libraries.immutableValue,
    Libraries.commonsCompress
  )

  val zioEmbedded = Seq(
    Libraries.zio,
    Libraries.embeddedMongo,
    Libraries.immutableValue,
    Libraries.commonsCompress
  )

  val circeGenericExtras = Seq(
    Libraries.circeGenericExtras
  )

  val magnolia1_2 = Seq(
    Libraries.magnolia1_2
  )

  lazy val magnolia1_3 = Seq(
    Libraries.magnolia1_3
  )

  lazy val scalacheckShapeless = Seq(
    Libraries.scalacheckShapeless
  )

  lazy val scalacheckCats = Seq(
    Libraries.scalacheckCats
  )
}
