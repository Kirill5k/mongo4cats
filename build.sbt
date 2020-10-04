ThisBuild / scalaVersion     := "2.13.3"
ThisBuild / version          := "0.0.1"
ThisBuild / organization     := "io.kirill"
ThisBuild / organizationName := "kirill"

lazy val root = (project in file("."))
  .settings(
    name := "mongo4cats"
  )
  .aggregate(core)

lazy val core = (project in file("core"))
  .settings(
    name := "core",
    libraryDependencies ++= Dependencies.core ++ Dependencies.test
  )
