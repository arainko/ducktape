package io.github.arainko.ducktape

final case class SourceToplevel(level1: SourceLevel1)
final case class SourceLevel1(str: String)

final case class DestToplevel(extra: Option[Int] = Some(321), level1: DestLevel1)
final case class DestLevel1(extra: Option[Int] = Some(123), str: String)

object Playground extends App {
  val source = SourceToplevel(SourceLevel1("str"))

  val dest =
    source
      .into[DestToplevel]
      .transform(
        Field.fallbackToDefault.regional(_.level1),
        Field.const(_.extra, Some(1)),
      )


  // val cos = 
  //  internal.CodePrinter.structure: 
  //   Field.fallbackToDefault[Option[Int], Option[Int]].regional(_.element)

  println(dest)
}
