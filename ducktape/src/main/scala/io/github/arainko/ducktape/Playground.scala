package io.github.arainko.ducktape

final case class SourceToplevel(level1: SourceLevel1)
final case class SourceLevel1(str: String)

final case class DestToplevel(extra: Option[Int], level1: DestLevel1)
final case class DestLevel1(extra: Option[Int], str: String)

object Playground extends App {
  val source = SourceToplevel(SourceLevel1("str"))

  val dest =
    source
      .into[DestToplevel]
      .transform(
        Field.const(_.extra, Some(1)),
        Field.useNones(a => a.level1)
      )

  println(dest)
}
