ThisBuild / scalaVersion     := "2.13.3"
ThisBuild / organization     := "io.github.kirill5k"

lazy val noPublish = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false,
  publish / skip := true
)

lazy val root = (project in file("."))
  .settings(noPublish)
  .settings(
    name := "mongo4cats",
    headerLicense := None
  )
  .aggregate(`mongo4cats-core`)

lazy val commonSettings = Seq(
  organizationName := "Mongo DB client wrapper for Cats Effect & Fs2",
  startYear := Some(2020),
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  resolvers += "Apache public" at "https://repository.apache.org/content/groups/public/",
  scalafmtOnCompile := true
)

lazy val `mongo4cats-core` = (project in file("core"))
  .settings(commonSettings)
  .settings(
    name := "mongo4cats-core",
    libraryDependencies ++= Dependencies.core ++ Dependencies.test,
    test / parallelExecution := false
  )
  .enablePlugins(AutomateHeaderPlugin)
