import xerial.sbt.Sonatype._

Global / onChangedBuildSource := ReloadOnSourceChanges
ThisBuild / versionScheme := Some("early-semver")

inThisBuild(
  List(
    organization := "io.github.arainko",
    homepage := Some(url("https://github.com/arainko/ducktape")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "arainko",
        "Aleksander Rainko",
        "aleksander.rainko99@gmail.com",
        url("https://github.com/arainko")
      )
    )
  )
)

name := "ducktape"
sonatypeRepository := "https://s01.oss.sonatype.org/service/local"
sonatypeCredentialHost := "s01.oss.sonatype.org"

lazy val root = project
  .in(file("."))
  .settings(publish / skip := true)
  .aggregate(ducktape)

lazy val ducktape =
  project
    .in(file("ducktape"))
    .settings(
      scalaVersion := "3.1.3",
      scalacOptions ++= List("-Xcheck-macros", "-no-indent", "-old-syntax"),
      libraryDependencies += "org.scalameta" %% "munit" % "1.0.0-M6" % Test
    )

lazy val docs =
  project
    .in(file("documentation"))
    .settings(
      scalaVersion := "3.1.3",
      mdocVariables := Map("VERSION" -> version.value)
    )
    .dependsOn(ducktape)
    .enablePlugins(MdocPlugin)
