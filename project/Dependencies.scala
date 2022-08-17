import sbt._

object Dependencies {
  private object Versions {
    val mongodb     = "4.6.0"
    val fs2         = "3.2.7"
    val scalaCompat = "2.7.0"
    val circe       = "0.14.2"

    val logback             = "1.2.11"
    val scalaTest           = "3.2.12"
    val scalaTestScalaCheck = "3.2.13.0"
    val scalaCheck          = "1.16.0"

    val embeddedMongo   = "3.4.5"
    val immutableValue  = "2.9.0"
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

    val fs2Core     = "co.fs2"                 %% "fs2-core"                % Versions.fs2
    val scalaCompat = "org.scala-lang.modules" %% "scala-collection-compat" % Versions.scalaCompat

    val circeCore          = "io.circe" %% "circe-core"           % Versions.circe
    val circeParser        = "io.circe" %% "circe-parser"         % Versions.circe
    val circeGeneric       = "io.circe" %% "circe-generic"        % Versions.circe
    val circeGenericExtras = "io.circe" %% "circe-generic-extras" % Versions.circe

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

  lazy val core = Seq(
    Libraries.mongodbBson,
    Libraries.mongodbDriverCore,
    Libraries.mongodbDriverStreams,
    Libraries.fs2Core,
    Libraries.scalaCompat
  )

  lazy val test = Seq(
    Libraries.logback             % Test,
    Libraries.scalaTest           % Test,
    Libraries.scalaTestScalaCheck % Test,
    Libraries.scalaCheck          % Test
  )

  lazy val examples = Seq(
    Libraries.logback
  )

  lazy val circe = Seq(
    Libraries.circeCore,
    Libraries.circeGeneric,
    Libraries.circeParser
  )

  lazy val circeGenericExtras = Seq(
    Libraries.circeGenericExtras
  )

  lazy val embedded = Seq(
    Libraries.fs2Core,
    Libraries.embeddedMongo,
    Libraries.immutableValue,
    Libraries.commonsCompress
  )

  lazy val magnolia1_2 = Seq(
    Libraries.magnolia1_2
  )

  lazy val magnolia1_3 = Seq(
    Libraries.magnolia1_3
  )

  lazy val scalacheckShapeless = Seq(
    Libraries.scalacheckShapeless % Test
  )

  lazy val scalacheckCats = Seq(
    Libraries.scalacheckCats
  )
}
