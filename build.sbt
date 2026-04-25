import laika.config.SelectionConfig
import laika.config.ChoiceConfig
import laika.config.Selections
import org.typelevel.sbt.site.TypelevelSiteSettings
import com.typesafe.tools.mima.core._
import org.typelevel.sbt.TypelevelMimaPlugin

Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / tlBaseVersion := "0.2"
ThisBuild / organization := "io.github.arainko"
ThisBuild / organizationName := "arainko"
ThisBuild / startYear := Some(2023)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(tlGitHubDev("arainko", "Aleksander Rainko"))
ThisBuild / scalaVersion := "3.3.7"
ThisBuild / tlSitePublishBranch := Some("series/0.2.x")

ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

ThisBuild / mimaBinaryIssueFilters ++= Seq(
  ProblemFilters.exclude[Problem]("io.github.arainko.ducktape.internal.*"),
  // Selector only exists at compiletime
  ProblemFilters.exclude[ReversedMissingMethodProblem]("io.github.arainko.ducktape.Selector.element")
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

lazy val root = tlCrossRootProject.aggregate(ducktape, scalaNextTests)

lazy val ducktape =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .enablePlugins(TypelevelMimaPlugin)
    .in(file("ducktape"))
    .settings(
      scalacOptions ++= List("-deprecation", "-Wunused:all", "-Ykind-projector:underscores", "-Xcheck-macros"),
      Test / scalacOptions --= List("-deprecation", "-Wunused:all"),
      Test / scalacOptions ++= List("-Werror", "-Wconf:cat=deprecation:s", "-Wunused:imports"),
      libraryDependencies += "org.scalameta" %%% "munit" % "1.3.0" % Test
    )
    .nativeSettings(
      bspEnabled := false,
      tlMimaPreviousVersions := Set.empty
    )
    .jsSettings(bspEnabled := false)
    .jvmConfigure(_.dependsOn(javaFixtures))

lazy val scalaNextTests =
  project
    .in(file("scala-next-tests"))
    .enablePlugins(NoPublishPlugin)
    .settings(
      scalaVersion := "3.7.4",
      scalacOptions ++= List("-Wunused:all", "-Xcheck-macros"),
      libraryDependencies += "org.scalameta" %%% "munit" % "1.3.0" % Test
    )
    .dependsOn(ducktape.jvm % "compile->compile;test->test")

lazy val javaFixtures = project
  .in(file("java-fixtures"))
  .enablePlugins(NoPublishPlugin)
  .settings(
    crossPaths := false,
    autoScalaLibrary := false
  )

lazy val docs =
  project
    .in(file("documentation"))
    .enablePlugins(NoPublishPlugin, MdocPlugin, TypelevelSitePlugin)
    .disablePlugins(MimaPlugin)
    .settings(
      laikaConfig := LaikaConfig.defaults
        .withConfigValue(
          Selections(
            Seq(
              SelectionConfig(
                "model",
                ChoiceConfig("wire", "Wire model"),
                ChoiceConfig("domain", "Domain model")
              ),
              SelectionConfig(
                "fallible-model",
                ChoiceConfig("wire", "Wire model"),
                ChoiceConfig("domain", "Domain model"),
                ChoiceConfig("newtypes", "Newtypes")
              )
            ) ++ (
              // Going overboard with this since all selections are connected to each other (eg. you pick an option on of them)
              // then all of them will change, this caused screen jumps when looking at the generated code
              (1 to 25).map(num =>
                SelectionConfig(
                  s"underlying-code-$num",
                  ChoiceConfig("visible", "User visible code"),
                  ChoiceConfig("generated", "Generated code")
                )
              )
            ): _*
          )
        ),
      tlSiteHelium := DeffSiteSettings.defaults.value,
      mdocVariables := Map("VERSION" -> tlLatestVersion.value.mkString),
      libraryDependencies += ("org.scalameta" %% "scalafmt-dynamic" % "3.6.1").cross(CrossVersion.for3Use2_13)
    )
    .dependsOn(ducktape.jvm)

lazy val generateReadme = taskKey[Unit]("gen readme")

generateReadme := Def.task {
  val docOutput = (docs / mdocOut).value
  IO.copyFile(docOutput / "readme.md", file("README.md"))
}.dependsOn((docs / mdoc).toTask("")).value
