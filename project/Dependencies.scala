import sbt._

object Dependencies {
  object Versions {
    lazy val mongodb   = "2.9.0"
    lazy val catsCore       = "2.1.1"
    lazy val catsEffect     = "2.1.3"
    lazy val catsEffectTest = "0.4.0"
    lazy val scalaTest      = "3.1.1"
  }

  object Libraries {
    lazy val mongodb = "org.mongodb.scala" %% "mongo-scala-driver" % Versions.mongodb

    lazy val catsCore   = "org.typelevel" %% "cats-core"   % Versions.catsCore
    lazy val catsEffect = "org.typelevel" %% "cats-effect" % Versions.catsEffect

    lazy val scalaTest      = "org.scalatest"  %% "scalatest"                     % Versions.scalaTest
    lazy val catsEffectTest = "com.codecommit" %% "cats-effect-testing-scalatest" % Versions.catsEffectTest
  }
}
