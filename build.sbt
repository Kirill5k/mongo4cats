ThisBuild / scalaVersion     := "2.13.3"
ThisBuild / organization     := "io.github.kirill5k"
ThisBuild / organizationName := "kirill"

lazy val root = (project in file("."))
  .settings(
    name := "mongo4cats"
  )
  .aggregate(core)

lazy val core = (project in file("core"))
  .settings(
    name := "mongo4cats-core",
    libraryDependencies ++= Dependencies.core ++ Dependencies.test
  )
