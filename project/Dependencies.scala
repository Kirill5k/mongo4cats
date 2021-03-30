import sbt._

object Dependencies {
  object Versions {
    lazy val mongodb     = "4.2.2"
    lazy val fs2         = "3.0.0"
    lazy val scalaCompat = "2.2.0"

    lazy val scalaLogging  = "3.9.2"
    lazy val logback       = "1.2.3"
    lazy val scalaTest     = "3.2.6"
    lazy val embeddedMongo = "3.0.0"
  }

  object Libraries {
    lazy val mongodb            = "org.mongodb.scala"      %% "mongo-scala-driver"      % Versions.mongodb
    lazy val fs2Core            = "co.fs2"                 %% "fs2-core"                % Versions.fs2
    lazy val fs2ReactiveStreams = "co.fs2"                 %% "fs2-reactive-streams"    % Versions.fs2
    lazy val scalaCompat        = "org.scala-lang.modules" %% "scala-collection-compat" % Versions.scalaCompat

    lazy val scalaTest     = "org.scalatest"              %% "scalatest"                 % Versions.scalaTest
    lazy val embeddedMongo = "de.flapdoodle.embed"         % "de.flapdoodle.embed.mongo" % Versions.embeddedMongo
    lazy val scalaLogging  = "com.typesafe.scala-logging" %% "scala-logging"             % Versions.scalaLogging
    lazy val logback       = "ch.qos.logback"              % "logback-classic"           % Versions.logback
  }

  lazy val core = Seq(
    Libraries.mongodb,
    Libraries.fs2Core,
    Libraries.fs2ReactiveStreams,
    Libraries.scalaCompat
  )

  lazy val test = Seq(
    Libraries.scalaLogging  % Test,
    Libraries.logback       % Test,
    Libraries.scalaTest     % Test,
    Libraries.embeddedMongo % Test
  )
}
