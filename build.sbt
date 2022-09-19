import xerial.sbt.Sonatype.GitHubHosting
import ReleaseTransformations._
import microsites.CdnDirectives
import sbtghactions.JavaSpec
import Utils._
import pl.project13.scala.sbt.JmhPlugin

val scala212               = "2.12.16"
val scala213               = "2.13.8"
val scala3                 = "3.2.0"
val supportedScalaVersions = List(scala212, scala213, scala3)

def priorTo2_13(scalaVersion: String): Boolean =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, minor)) if minor < 13 => true
    case _                              => false
  }

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
ThisBuild / githubWorkflowJavaVersions          := Seq(JavaSpec.temurin("18"))
ThisBuild / testFrameworks ++= Seq(new TestFramework("zio.test.sbt.ZTestFramework"))

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
  scalafmtOnCompile  := false, // true,
  crossScalaVersions := supportedScalaVersions,
  Compile / doc / scalacOptions ++= Seq(
    "-no-link-warnings" // Suppresses problems with Scaladoc links
  ),
  scalacOptions ++= partialUnificationOption(scalaVersion.value),
  scalacOptions ++= (if (priorTo2_13(scalaVersion.value)) Seq("-Ypartial-unification") else Nil),
  tpolecatCiModeOptions ~= { opts =>
    opts.filterNot(
      ScalacOptions.privateWarnUnusedOptions ++
        ScalacOptions.warnUnusedOptions
    )
  }
)

val embedded = project
  .in(file("embedded"))
  .settings(commonSettings)
  .settings(
    name := "mongo4cats-embedded",
    libraryDependencies ++= Dependencies.embedded,
    test / parallelExecution := false,
    mimaPreviousArtifacts    := Set(organization.value %% moduleName.value % "0.5.0")
  )
  .enablePlugins(AutomateHeaderPlugin)

val `zio-embedded` = project
  .in(file("zio-embedded"))
  .settings(commonSettings)
  .settings(
    name := "mongo4cats-zio-embedded",
    libraryDependencies ++= Dependencies.zioEmbedded,
    test / parallelExecution := false,
    mimaPreviousArtifacts    := Set(organization.value %% moduleName.value % "0.5.0")
  )
  .enablePlugins(AutomateHeaderPlugin)

val kernel = project
  .in(file("kernel"))
  .settings(commonSettings)
  .settings(
    name := "mongo4cats-kernel",
    libraryDependencies ++= Dependencies.kernel,
    test / parallelExecution := false,
    mimaPreviousArtifacts    := Set(organization.value %% moduleName.value % "0.5.0")
  )
  .enablePlugins(AutomateHeaderPlugin)

val core = project
  .in(file("core"))
  .dependsOn(kernel % "test->test;compile->compile", embedded % "test->compile")
  .settings(commonSettings)
  .settings(
    name := "mongo4cats-core",
    libraryDependencies ++= Dependencies.core,
    test / parallelExecution := false,
    mimaPreviousArtifacts    := Set(organization.value %% moduleName.value % "0.5.0"),
    libraryDependencies ++= kindProjectorDependency(scalaVersion.value)
  )
  .enablePlugins(AutomateHeaderPlugin)

val zio = project
  .in(file("zio"))
  .dependsOn(kernel % "test->test;compile->compile", `zio-embedded` % "test->compile")
  .settings(commonSettings)
  .settings(
    name := "mongo4cats-zio",
    libraryDependencies ++= Dependencies.zio,
    test / parallelExecution := false,
    mimaPreviousArtifacts    := Set(organization.value %% moduleName.value % "0.5.0"),
    libraryDependencies ++= kindProjectorDependency(scalaVersion.value)
  )
  .enablePlugins(AutomateHeaderPlugin)

val circe = project
  .in(file("circe"))
  .dependsOn(kernel, core % "test->compile", embedded % "test->compile")
  .settings(commonSettings)
  .settings(
    name := "mongo4cats-circe",
    libraryDependencies ++= Dependencies.circe,
    test / parallelExecution := false,
    mimaPreviousArtifacts    := Set(organization.value %% moduleName.value % "0.5.0")
  )
  .enablePlugins(AutomateHeaderPlugin)

val `mongo4cats-bson-derivation` = project
  .in(file("bson-derivation"))
  .dependsOn(core, embedded % "test->compile")
  .settings(commonSettings)
  .settings(
    name := "mongo4cats-bson-derivation",
    Compile / sourceGenerators += (Compile / sourceManaged).map(Boilerplate.gen).taskValue,
    libraryDependencies ++= Dependencies.circe ++ Dependencies.core,
    libraryDependencies ++=
      Dependencies.scalacheckCats ++
        (CrossVersion.partialVersion(scalaVersion.value) match {
          case Some((2, _)) =>
            Dependencies.circeGenericExtras ++
              Dependencies.scalacheckShapeless ++
              Dependencies.magnolia1_2 ++
              Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
          case _ => Dependencies.magnolia1_3
        }),
    test / parallelExecution := false,
    mimaPreviousArtifacts    := Set(organization.value %% moduleName.value % "0.4.1"),
    scalacOptions ++=
      (CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, _)) => Seq("-Yretain-trees") // For Magnolia: https://github.com/softwaremill/magnolia#limitations
        case _            => Nil
      })
  )
  .dependsOn(circe)
  .enablePlugins(AutomateHeaderPlugin)

lazy val bench = (project in file("bench"))
  .enablePlugins(JmhPlugin)
  .settings(
    commonSettings,
    libraryDependencies ++= kindProjectorDependency(scalaVersion.value)
  )
  .dependsOn(
    core,
    circe,
    examples,
    embedded,
    `mongo4cats-bson-derivation`
  )
  .aggregate(
    core,
    circe,
    examples,
    embedded,
    `mongo4cats-bson-derivation`
  )

val examples = project
  .in(file("examples"))
  .dependsOn(core, circe, embedded, zio, `zio-embedded`)
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
    examples,
    embedded,
    `zio-embedded`,
    `mongo4cats-bson-derivation`,
    bench
  )
