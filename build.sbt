import xerial.sbt.Sonatype.GitHubHosting
import ReleaseTransformations._
import microsites.CdnDirectives

lazy val scala212               = "2.12.14"
lazy val scala213               = "2.13.7"
lazy val scala3                 = "3.1.0"
lazy val supportedScalaVersions = List(scala212, scala213, scala3)

ThisBuild / scalaVersion           := scala213
ThisBuild / organization           := "com.precog.mongo4cats"
ThisBuild / homepage               := Some(url("https://kirill5k.github.io/mongo4cats"))
ThisBuild / scmInfo                := Some(ScmInfo(url("https://github.com/precog/mongo4cats"), "git@github.com:precog/mongo4cats.git"))
ThisBuild / developers             := List(Developer("kirill5k", "Kirill", "immotional@aol.com", url("https://github.com/kirill5k")))
ThisBuild / licenses               := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / sonatypeProjectHosting := Some(GitHubHosting("precog", "mongo4cats", "bot@precog.com"))
ThisBuild / publishTo              := sonatypePublishToBundle.value
ThisBuild / githubWorkflowPublishTargetBranches := Seq(RefPredicate.Equals(Ref.Branch("master")))
ThisBuild / githubWorkflowScalaVersions         := supportedScalaVersions
ThisBuild / githubWorkflowJavaVersions          := Seq("amazon-corretto@1.17")
ThisBuild / githubOwner := "precog"
ThisBuild / githubRepository := "mongo4cats"

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
  organizationName := "MongoDB Java client wrapper for Cats-Effect & FS2",
  startYear        := Some(2020),
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  headerLicense := Some(HeaderLicense.ALv2("2020", "Kirill5k")),
  resolvers += "Apache public" at "https://repository.apache.org/content/groups/public/",
  scalafmtOnCompile  := true,
  crossScalaVersions := supportedScalaVersions,
  Compile / doc / scalacOptions ++= Seq(
    "-no-link-warnings" // Suppresses problems with Scaladoc links
  )
)

lazy val root = project
  .in(file("."))
  .settings(noPublish)
  .settings(
    name               := "mongo4cats",
    crossScalaVersions := Nil
  )
  .aggregate(
    `mongo4cats-core`,
    `mongo4cats-circe`,
    `mongo4cats-examples`,
    `mongo4cats-embedded`
  )

lazy val `mongo4cats-core` = project
  .in(file("core"))
  .dependsOn(`mongo4cats-embedded` % "test->compile")
  .settings(commonSettings)
  .settings(
    name := "mongo4cats-core",
    libraryDependencies ++= Dependencies.core ++ Dependencies.test,
    test / parallelExecution := false,
    mimaPreviousArtifacts    := Set(organization.value %% moduleName.value % "0.4.1")
  )
  .enablePlugins(AutomateHeaderPlugin)

lazy val `mongo4cats-circe` = project
  .in(file("circe"))
  .dependsOn(`mongo4cats-core`, `mongo4cats-embedded` % "test->compile")
  .settings(commonSettings)
  .settings(
    name := "mongo4cats-circe",
    libraryDependencies ++= Dependencies.circe ++ Dependencies.test,
    test / parallelExecution := false,
    mimaPreviousArtifacts    := Set(organization.value %% moduleName.value % "0.4.1")
  )
  .enablePlugins(AutomateHeaderPlugin)

lazy val `mongo4cats-examples` = project
  .in(file("examples"))
  .dependsOn(`mongo4cats-core`, `mongo4cats-circe`, `mongo4cats-embedded`)
  .settings(noPublish)
  .settings(commonSettings)
  .settings(
    name := "mongo4cats-examples",
    libraryDependencies ++= Dependencies.examples ++ Dependencies.test
  )
  .enablePlugins(AutomateHeaderPlugin)

lazy val `mongo4cats-embedded` = project
  .in(file("embedded"))
  .settings(commonSettings)
  .settings(
    name := "mongo4cats-embedded",
    libraryDependencies ++= Dependencies.embedded,
    test / parallelExecution := false,
    mimaPreviousArtifacts    := Set(organization.value %% moduleName.value % "0.4.1")
  )
  .enablePlugins(AutomateHeaderPlugin)

lazy val docs = project
  .in(file("docs"))
  .dependsOn(`mongo4cats-core`, `mongo4cats-circe`, `mongo4cats-embedded`)
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
