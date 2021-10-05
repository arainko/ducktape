val scala3Version = "3.0.2"

lazy val ducktape =
  project
    .in(file("ducktape"))
    .settings(
      version := "0.0.1",
      scalaVersion := scala3Version,
      scalacOptions ++= Seq("-Xmax-inlines", "64"),
      libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test
    )
