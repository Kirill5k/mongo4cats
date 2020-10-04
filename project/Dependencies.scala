import sbt._

object Dependencies {
  object Versions {
    lazy val mongodb        = "4.0.2"
    lazy val catsEffect     = "2.1.3"
    lazy val catsEffectTest = "0.4.0"
    lazy val scalaTest      = "3.1.1"
    lazy val embeddedMongo  = "2.2.0"
  }

  object Libraries {
    lazy val mongodb    = "org.mongodb.scala" %% "mongo-scala-driver" % Versions.mongodb
    lazy val catsEffect = "org.typelevel"     %% "cats-effect"        % Versions.catsEffect

    lazy val scalaTest      = "org.scalatest"       %% "scalatest"                     % Versions.scalaTest
    lazy val catsEffectTest = "com.codecommit"      %% "cats-effect-testing-scalatest" % Versions.catsEffectTest
    lazy val embeddedMongo  = "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo"      % Versions.embeddedMongo
  }

  lazy val core = Seq(
    Libraries.mongodb,
    Libraries.catsEffect
  )

  lazy val test = Seq(
    Libraries.catsEffectTest % Test,
    Libraries.scalaTest      % Test,
    Libraries.embeddedMongo  % Test
  )
}
