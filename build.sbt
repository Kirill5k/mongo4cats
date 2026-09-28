import sbtghactions.{JavaSpec, WorkflowStep}
import org.typelevel.scalacoptions.ScalacOptions
import Utils.*

val scala212               = "2.12.21"
val scala213               = "2.13.18"
val scala3                 = "3.9.0"
val supportedScalaVersions = List(scala212, scala213, scala3)

ThisBuild / scalaVersion  := scala213
ThisBuild / organization  := "io.github.kirill5k"
ThisBuild / homepage      := Some(uri("https://kirill5k.github.io/mongo4cats"))
ThisBuild / scmInfo       := Some(ScmInfo(uri("https://github.com/kirill5k/mongo4cats"), "git@github.com:kirill5k/mongo4cats.git"))
ThisBuild / developers    := List(Developer("kirill5k", "Kirill", "immotional@aol.com", uri("https://github.com/kirill5k")))
ThisBuild / licenses      := List("Apache-2.0" -> uri("http://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / testFrameworks ++= Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
ThisBuild / githubWorkflowPublishTargetBranches := Nil
ThisBuild / githubWorkflowScalaVersions         := supportedScalaVersions
ThisBuild / githubWorkflowJavaVersions          := Seq(JavaSpec.temurin("21"))
ThisBuild / githubWorkflowUseSbtThinClient      := true
ThisBuild / githubWorkflowBuild                 := Seq(WorkflowStep.Sbt(List("testFull"), name = Some("Build project")))

// sbt-ci-release uses sbt-dynver for versioning, leaving these sbt-git metadata keys unused.
Global / excludeLintKeys ++= Set(git.gitDescribedVersion, git.gitUncommittedChanges)

githubWorkflowDir := (LocalRootProject / baseDirectory).value / ".github"
Test / tpolecatExcludeOptions += ScalacOptions.warnNonUnitStatement
organizationName := "MongoDB Java client wrapper for Cats-Effect & FS2"
startYear        := Some(2020)
licenses += ("Apache-2.0", uri("https://www.apache.org/licenses/LICENSE-2.0.txt"))
headerLicense := Some(HeaderLicense.ALv2("2020", "Kirill5k"))
resolvers += "Apache public" at "https://repository.apache.org/content/groups/public/"
scalafmtOnCompile  := true
crossScalaVersions := supportedScalaVersions
Compile / doc / scalacOptions ++= Seq(
  "-no-link-warnings" // Suppresses problems with Scaladoc links
)
mimaPreviousArtifacts := Set(organization.value %% moduleName.value % "0.8.0")
scalacOptions ++= partialUnificationOption(scalaVersion.value)
// Retain the shared Scala 2 source syntax when compiling with Scala 3.
scalacOptions ++= (if (scalaBinaryVersion.value == "3") Seq("-source:3.3") else Nil)
scalacOptions ~= { (options: Seq[String]) => options.filterNot(Set("-Wnonunit-statement")) }

val noPublish = Seq(
  publish               := {},
  publishLocal          := {},
  publishArtifact       := false,
  publish / skip        := true,
  mimaPreviousArtifacts := Set.empty
)

val embedded = project
  .in(file("modules/embedded"))
  .settings(
    name := "mongo4cats-embedded",
    libraryDependencies ++= Dependencies.embedded
  )
  .enablePlugins(AutomateHeaderPlugin)

val `zio-embedded` = project
  .in(file("modules/zio-embedded"))
  .settings(
    name := "mongo4cats-zio-embedded",
    libraryDependencies ++= Dependencies.zioEmbedded
  )
  .enablePlugins(AutomateHeaderPlugin)

val kernel = project
  .in(file("modules/kernel"))
  .settings(
    name := "mongo4cats-kernel",
    libraryDependencies ++= Dependencies.kernel
  )
  .enablePlugins(AutomateHeaderPlugin)

val core = project
  .in(file("modules/core"))
  .dependsOn(kernel % "test->test;compile->compile", embedded % "test->compile")
  .settings(
    name := "mongo4cats-core",
    libraryDependencies ++= Dependencies.core,
    libraryDependencies ++= kindProjectorDependency(scalaVersion.value)
  )
  .enablePlugins(AutomateHeaderPlugin)

val zio = project
  .in(file("modules/zio"))
  .dependsOn(kernel % "test->test;compile->compile", `zio-embedded` % "test->compile")
  .settings(
    name := "mongo4cats-zio",
    libraryDependencies ++= Dependencies.zio,
    libraryDependencies ++= kindProjectorDependency(scalaVersion.value),
    // ZIO specs are runnable entry points; the test jar has no default main class.
    Test / packageBin / mainClass := None
  )
  .enablePlugins(AutomateHeaderPlugin)

val circe = project
  .in(file("modules/circe"))
  .dependsOn(kernel % "test->test;compile->compile", core % "test->compile", embedded % "test->compile")
  .settings(
    name := "mongo4cats-circe",
    libraryDependencies ++= Dependencies.circe
  )
  .enablePlugins(AutomateHeaderPlugin)

val `zio-json` = project
  .in(file("modules/zio-json"))
  .dependsOn(kernel % "test->test;compile->compile", core % "test->compile", embedded % "test->compile")
  .settings(
    name := "mongo4cats-zio-json",
    libraryDependencies ++= Dependencies.zioJson
  )
  .enablePlugins(AutomateHeaderPlugin)

val examples = project
  .in(file("examples"))
  .dependsOn(core, circe, embedded, zio, `zio-embedded`, `zio-json`)
  .settings(noPublish)
  .settings(
    name := "mongo4cats-examples",
    libraryDependencies ++= Dependencies.examples,
    // The examples have multiple entry points, so their jar has no default main class.
    Compile / packageBin / mainClass := None
  )
  .enablePlugins(AutomateHeaderPlugin)

val website = project
  .in(file("website"))
  .dependsOn(kernel, core, circe, embedded, zio, `zio-embedded`, `zio-json`)
  .enablePlugins(MdocPlugin, DocusaurusPlugin)
  .settings(noPublish)
  .settings(
    moduleName    := "mongo4cats-website",
    mdocVariables := Map(
      "VERSION" -> version.value
    )
  )

val root = project
  .in(file("."))
  .settings(noPublish)
  .settings(
    name := "mongo4cats"
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
