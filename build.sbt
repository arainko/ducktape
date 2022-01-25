import xerial.sbt.Sonatype._

ThisBuild / dynverSonatypeSnapshots := true
ThisBuild / versionScheme := Some("early-semver")

name := "ducktape"
organization := "io.github.arainko"
licenses := Seq("APL2" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt"))
description := "Boilerplate-free transformations"

publishMavenStyle := true
sonatypeProjectHosting := Some(GitHubHosting("arainko", "ducktape", "aleksander.rainko99@gmail.com"))
sonatypeRepository := "https://s01.oss.sonatype.org/service/local"
sonatypeCredentialHost := "s01.oss.sonatype.org"
publishTo := sonatypePublishToBundle.value

lazy val ducktape =
  project
    .in(file("."))
    .settings(
      scalaVersion := "3.1.0",
      scalacOptions ++= Seq("-Xmax-inlines", "64"),
      libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test
    )
