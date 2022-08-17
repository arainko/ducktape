import xerial.sbt.Sonatype._

Global / onChangedBuildSource := ReloadOnSourceChanges
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / organization := "io.github.arainko"
ThisBuild / homepage := Some(url("https://github.com/arainko/ducktape"))
ThisBuild / licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / developers := List(
  Developer(
    "arainko",
    "Aleksander Rainko",
    "aleksander.rainko99@gmail.com",
    url("https://github.com/arainko")
  )
)
ThisBuild / scalaVersion := "3.1.3"

name := "ducktape"
sonatypeRepository := "https://s01.oss.sonatype.org/service/local"
sonatypeCredentialHost := "s01.oss.sonatype.org"

lazy val root =
  project
    .in(file("."))
    .settings(publish / skip := true)
    .aggregate(ducktape)

lazy val ducktape =
  project
    .in(file("ducktape"))
    .settings(
      scalacOptions ++= List("-Xcheck-macros", "-no-indent", "-old-syntax"),
      libraryDependencies += "org.scalameta" %% "munit" % "1.0.0-M6" % Test
    )

lazy val docs =
  project
    .in(file("documentation"))
    .settings(mdocVariables := Map("VERSION" -> version.value))
    .dependsOn(ducktape)
    .enablePlugins(MdocPlugin)

lazy val examples =
  project
    .in(file("examples"))
    .settings(publish / skip := true)
    .dependsOn(ducktape)
