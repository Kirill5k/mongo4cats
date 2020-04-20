import Dependencies.Libraries._

ThisBuild / scalaVersion     := "2.13.1"
ThisBuild / version          := "0.0.1-SNAPSHOT"
ThisBuild / organization     := "io.kirill"
ThisBuild / organizationName := "kirill"

lazy val root = (project in file("."))
  .settings(
    name := "mongo4cats",
    libraryDependencies ++= Seq(
      mongodb,
      catsCore,
      catsEffect,
      catsEffectTest % Test,
      scalaTest % Test,
      embeddedMongo % Test
    )
  )
