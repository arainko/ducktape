import xerial.sbt.Sonatype._

name := "ducktape"
organization := "io.github.arainko"
licenses := Seq("APL2" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt"))
description := "Boilerplate-free transformations"

publishMavenStyle := true
sonatypeProjectHosting := Some(GitHubHosting("arainko", "ducktape", "aleksander.rainko99@gmail.com"))
sonatypeRepository := "https://s01.oss.sonatype.org/service/local"
sonatypeCredentialHost := "s01.oss.sonatype.org"
publishTo := sonatypePublishToBundle.value

val scala3Version = "3.1.0"

lazy val ducktape =
  project
    .in(file("ducktape"))
    .settings(
      scalaVersion := scala3Version,
      scalacOptions ++= Seq("-Xmax-inlines", "64"),
      libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test
    )
