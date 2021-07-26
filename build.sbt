import xerial.sbt.Sonatype.GitHubHosting
import ReleaseTransformations._

lazy val scala212               = "2.12.13"
lazy val scala213               = "2.13.6"
lazy val scala3                 = "3.0.0"
lazy val supportedScalaVersions = List(scala212, scala213, scala3)

ThisBuild / scalaVersion           := scala213
ThisBuild / organization           := "io.github.kirill5k"
ThisBuild / homepage               := Some(url("https://github.com/kirill5k/mongo4cats"))
ThisBuild / scmInfo                := Some(ScmInfo(url("https://github.com/kirill5k/mongo4cats"), "git@github.com:kirill5k/mongo4cats.git"))
ThisBuild / developers             := List(Developer("kirill5k", "Kirill", "immotional@aol.com", url("https://github.com/kirill5k")))
ThisBuild / licenses               := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / sonatypeProjectHosting := Some(GitHubHosting("kirill5k", "mongo4cats", "immotional@aol.com"))
ThisBuild / publishTo              := sonatypePublishToBundle.value

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("+publishSigned"),
  releaseStepCommand("sonatypeBundleRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)

lazy val noPublish = Seq(
  publish         := {},
  publishLocal    := {},
  publishArtifact := false,
  publish / skip  := true
)

lazy val commonSettings = Seq(
  organizationName := "Mongo DB client wrapper for Cats Effect & FS2",
  startYear        := Some(2020),
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  headerLicense := Some(HeaderLicense.ALv2("2020", "Kirill5k")),
  resolvers += "Apache public" at "https://repository.apache.org/content/groups/public/",
  scalafmtOnCompile  := true,
  crossScalaVersions := supportedScalaVersions
)

lazy val root = project
  .in(file("."))
  .settings(noPublish)
  .settings(
    name               := "mongo4cats",
    crossScalaVersions := Nil
  )
  .aggregate(`mongo4cats-core`, `mongo4cats-circe`, `mongo4cats-examples`, `mongo4cats-test`)

lazy val `mongo4cats-core` = project
  .in(file("core"))
  .dependsOn(`mongo4cats-test` % "test->compile")
  .settings(commonSettings)
  .settings(
    name := "mongo4cats-core",
    libraryDependencies ++= Dependencies.core ++ Dependencies.test,
    test / parallelExecution := false
  )
  .enablePlugins(AutomateHeaderPlugin)

lazy val `mongo4cats-circe` = project
  .in(file("circe"))
  .dependsOn(`mongo4cats-core`, `mongo4cats-test` % "test->compile")
  .settings(commonSettings)
  .settings(
    name := "mongo4cats-circe",
    libraryDependencies ++= Dependencies.circe ++ Dependencies.test,
    test / parallelExecution := false
  )
  .enablePlugins(AutomateHeaderPlugin)

lazy val `mongo4cats-examples` = project
  .in(file("examples"))
  .dependsOn(`mongo4cats-core`, `mongo4cats-circe`)
  .settings(noPublish)
  .settings(commonSettings)
  .settings(
    name                     := "mongo4cats-examples",
    test / parallelExecution := false,
    libraryDependencies ++= Dependencies.examples
  )
  .enablePlugins(AutomateHeaderPlugin)

lazy val `mongo4cats-test` = project
  .in(file("test"))
  .settings(noPublish)
  .settings(commonSettings)
  .settings(
    name                     := "mongo4cats-test",
    test / parallelExecution := false,
    libraryDependencies ++= Dependencies.testkit
  )
  .enablePlugins(AutomateHeaderPlugin)
