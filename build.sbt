import xerial.sbt.Sonatype.GitHubHosting
import ReleaseTransformations.*
import microsites.CdnDirectives
import sbtghactions.JavaSpec
import Utils.*

val scala212               = "2.12.18"
val scala213               = "2.13.11"
val scala3                 = "3.3.0"
val supportedScalaVersions = List(scala212, scala213, scala3)

ThisBuild / scalaVersion           := scala213
ThisBuild / organization           := "io.github.kirill5k"
ThisBuild / homepage               := Some(url("https://kirill5k.github.io/mongo4cats"))
ThisBuild / scmInfo                := Some(ScmInfo(url("https://github.com/kirill5k/mongo4cats"), "git@github.com:kirill5k/mongo4cats.git"))
ThisBuild / developers             := List(Developer("kirill5k", "Kirill", "immotional@aol.com", url("https://github.com/kirill5k")))
ThisBuild / licenses               := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / sonatypeProjectHosting := Some(GitHubHosting("kirill5k", "mongo4cats", "immotional@aol.com"))
ThisBuild / publishTo              := sonatypePublishToBundle.value
ThisBuild / githubWorkflowPublishTargetBranches := Nil
ThisBuild / githubWorkflowScalaVersions         := supportedScalaVersions
ThisBuild / githubWorkflowJavaVersions          := Seq(JavaSpec.temurin("17"))
ThisBuild / testFrameworks ++= Seq(new TestFramework("zio.test.sbt.ZTestFramework"))

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("+publishSigned"),
  releaseStepCommand("sonatypeBundleRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)

val noPublish = Seq(
  publish         := {},
  publishLocal    := {},
  publishArtifact := false,
  publish / skip  := true
)

val commonSettings = Seq(
  organizationName := "MongoDB Java client wrapper for Cats-Effect & FS2",
  startYear        := Some(2020),
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  headerLicense := Some(HeaderLicense.ALv2("2020", "Kirill5k")),
  resolvers += "Apache public" at "https://repository.apache.org/content/groups/public/",
  scalafmtOnCompile  := true,
  crossScalaVersions := supportedScalaVersions,
  Compile / doc / scalacOptions ++= Seq(
    "-no-link-warnings" // Suppresses problems with Scaladoc links
  ),
  parallelExecution        := false,
  test / parallelExecution := false,
  mimaPreviousArtifacts    := Set(organization.value %% moduleName.value % "0.5.0"),
  scalacOptions ++= partialUnificationOption(scalaVersion.value),
  scalacOptions ~= { options: Seq[String] => options.filterNot(Set("-Wnonunit-statement")) }
)

val embedded = project
  .in(file("modules/embedded"))
  .settings(commonSettings)
  .settings(
    name := "mongo4cats-embedded",
    libraryDependencies ++= Dependencies.embedded
  )
  .enablePlugins(AutomateHeaderPlugin)

val `zio-embedded` = project
  .in(file("modules/zio-embedded"))
  .settings(commonSettings)
  .settings(
    name := "mongo4cats-zio-embedded",
    libraryDependencies ++= Dependencies.zioEmbedded
  )
  .enablePlugins(AutomateHeaderPlugin)

val kernel = project
  .in(file("modules/kernel"))
  .settings(commonSettings)
  .settings(
    name := "mongo4cats-kernel",
    libraryDependencies ++= Dependencies.kernel
  )
  .enablePlugins(AutomateHeaderPlugin)

val core = project
  .in(file("modules/core"))
  .dependsOn(kernel % "test->test;compile->compile", embedded % "test->compile")
  .settings(commonSettings)
  .settings(
    name := "mongo4cats-core",
    libraryDependencies ++= Dependencies.core,
    libraryDependencies ++= kindProjectorDependency(scalaVersion.value)
  )
  .enablePlugins(AutomateHeaderPlugin)

val zio = project
  .in(file("modules/zio"))
  .dependsOn(kernel % "test->test;compile->compile", `zio-embedded` % "test->compile")
  .settings(commonSettings)
  .settings(
    name := "mongo4cats-zio",
    libraryDependencies ++= Dependencies.zio,
    libraryDependencies ++= kindProjectorDependency(scalaVersion.value)
  )
  .enablePlugins(AutomateHeaderPlugin)

val circe = project
  .in(file("modules/circe"))
  .dependsOn(kernel % "test->test;compile->compile", core % "test->compile", embedded % "test->compile")
  .settings(commonSettings)
  .settings(
    name := "mongo4cats-circe",
    libraryDependencies ++= Dependencies.circe
  )
  .enablePlugins(AutomateHeaderPlugin)

val `zio-json` = project
  .in(file("modules/zio-json"))
  .dependsOn(kernel % "test->test;compile->compile", core % "test->compile", embedded % "test->compile")
  .settings(commonSettings)
  .settings(
    name := "mongo4cats-zio-json",
    libraryDependencies ++= Dependencies.zioJson
  )
  .enablePlugins(AutomateHeaderPlugin)

val examples = project
  .in(file("examples"))
  .dependsOn(core, circe, embedded, zio, `zio-embedded`, `zio-json`)
  .settings(noPublish)
  .settings(commonSettings)
  .settings(
    name := "mongo4cats-examples",
    libraryDependencies ++= Dependencies.examples
  )
  .enablePlugins(AutomateHeaderPlugin)

val docs = project
  .in(file("docs"))
  .dependsOn(core, circe, embedded, zio, `zio-embedded`)
  .enablePlugins(MicrositesPlugin)
  .settings(noPublish)
  .settings(commonSettings)
  .settings(
    name                      := "mongo4cats-docs",
    micrositeName             := "mongo4cats",
    micrositeAuthor           := "Kirill",
    micrositeDescription      := "MongoDB Java client wrapper for Cats-Effect & FS2",
    micrositeBaseUrl          := "/mongo4cats",
    micrositeDocumentationUrl := "/mongo4cats/docs",
    micrositeHomepage         := "https://github.com/kirill5k/mongo4cats",
    micrositeGithubOwner      := "kirill5k",
    micrositeGithubRepo       := "mongo4cats",
    micrositeHighlightTheme   := "docco",
    micrositeGitterChannel    := false,
    micrositeShareOnSocial    := false,
    mdocIn                    := (Compile / sourceDirectory).value / "mdoc",
    micrositeCDNDirectives := CdnDirectives(
      cssList = List("https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.2.0/styles/docco.min.css")
    )
  )

val root = project
  .in(file("."))
  .settings(noPublish)
  .settings(
    name               := "mongo4cats",
    crossScalaVersions := Nil
  )
  .aggregate(
    kernel,
    core,
    zio,
    circe,
    `zio-json`,
    examples,
    embedded,
    `zio-embedded`
  )
