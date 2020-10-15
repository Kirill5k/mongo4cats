import sbt._

object Dependencies {
  object Versions {
    lazy val mongodb      = "4.1.0"
    lazy val catsEffect   = "2.1.3"
    lazy val fs2          = "2.4.4"
    lazy val scalaLogging = "3.9.2"
    lazy val logback      = "1.2.3"

    lazy val catsEffectTest = "0.4.0"
    lazy val scalaTest      = "3.1.1"
    lazy val embeddedMongo  = "2.2.0"
  }

  object Libraries {
    lazy val mongodb            = "org.mongodb.scala"          %% "mongo-scala-driver"   % Versions.mongodb
    lazy val catsEffect         = "org.typelevel"              %% "cats-effect"          % Versions.catsEffect
    lazy val fs2Core            = "co.fs2"                     %% "fs2-core"             % Versions.fs2
    lazy val fs2ReactiveStreams = "co.fs2"                     %% "fs2-reactive-streams" % Versions.fs2
    lazy val scalaLogging       = "com.typesafe.scala-logging" %% "scala-logging"        % Versions.scalaLogging
    lazy val logback            = "ch.qos.logback"             % "logback-classic"       % Versions.logback

    lazy val scalaTest      = "org.scalatest"       %% "scalatest"                     % Versions.scalaTest
    lazy val catsEffectTest = "com.codecommit"      %% "cats-effect-testing-scalatest" % Versions.catsEffectTest
    lazy val embeddedMongo  = "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo"      % Versions.embeddedMongo
  }

  lazy val core = Seq(
    Libraries.mongodb,
    Libraries.catsEffect,
    Libraries.fs2Core,
    Libraries.fs2ReactiveStreams,
    Libraries.scalaLogging,
    Libraries.logback
  )

  lazy val test = Seq(
    Libraries.catsEffectTest % Test,
    Libraries.scalaTest      % Test,
    Libraries.embeddedMongo  % Test
  )
}
