import com.typesafe.tools.mima.core._
import xerial.sbt.Sonatype._
import org.typelevel.sbt.TypelevelMimaPlugin

// Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / tlBaseVersion := "0.2"
ThisBuild / organization := "io.github.arainko"
ThisBuild / organizationName := "arainko"
ThisBuild / startYear := Some(2023)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(tlGitHubDev("arainko", "Aleksander Rainko"))
ThisBuild / tlSonatypeUseLegacyHost := false
ThisBuild / scalaVersion := "3.3.1"

ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

ThisBuild / mimaBinaryIssueFilters ++= Seq(
  ProblemFilters.exclude[Problem]("io.github.arainko.ducktape.internal.*")
)

ThisBuild / tlCiReleaseBranches := Seq("series/0.1.x", "series/0.2.x")
ThisBuild / tlCiScalafixCheck := true
ThisBuild / tlCiScalafmtCheck := true
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
ThisBuild / githubWorkflowUseSbtThinClient := true
ThisBuild / githubWorkflowBuild += WorkflowStep.Run(
  name = Some("Check docs"),
  commands = "sbt --client docs/mdoc" :: Nil,
  cond = Some(s"matrix.project == '${root.jvm.id}'")
)

ThisBuild / tlVersionIntroduced := Map("3" -> "0.1.6")

lazy val root = tlCrossRootProject.aggregate(ducktape)

lazy val ducktape =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .enablePlugins(TypelevelMimaPlugin)
    .in(file("ducktape"))
    .settings(
      scalacOptions ++= List("-Xcheck-macros", "-deprecation", "-Werror", "-Wunused:all"),
      libraryDependencies += "org.scalameta" %%% "munit" % "1.0.0-M10" % Test
    )
    .dependsOn(tooling)

lazy val tooling =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .disablePlugins(MimaPlugin)
    .enablePlugins(NoPublishPlugin)
    .in(file("tooling"))

lazy val docs =
  project
    .in(file("documentation"))
    .enablePlugins(NoPublishPlugin, MdocPlugin)
    .disablePlugins(MimaPlugin)
    .settings(
      mdocVariables := Map("VERSION" -> version.value),
      libraryDependencies += ("org.scalameta" %% "scalafmt-dynamic" % "3.6.1").cross(CrossVersion.for3Use2_13)
    )
    .dependsOn(ducktape.jvm)
