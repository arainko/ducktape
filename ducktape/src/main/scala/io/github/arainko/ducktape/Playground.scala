package io.github.arainko.ducktape

final case class SourceToplevel(level1: SourceLevel1)
final case class SourceLevel1(str: String)

final case class DestToplevel(extra: Option[Int], level1: DestLevel1)
final case class DestLevel1(extra: Option[Int], str: String)

final case class MoreFields(field1: Int, field2: Int, field3: Int, field4: Int)
final case class LessFields(field1: Int, field2: Int, field3: Int)


object Playground {
  val more = MoreFields(1, 2, 3, 4)
  val expected = LessFields(1, 2, 3)
  val actual =
    // List(
      (1: Int).to[Int]
      // more.into[LessFields].transform(),
      // // more.via(LessFields.apply),
      // more.intoVia(LessFields.apply).transform(),
      // Transformer.define[MoreFields, LessFields].build().transform(more),
      // Transformer.defineVia[MoreFields]S(LessFields.apply).build().transform(more)
    // )

  // val source = SourceToplevel(SourceLevel1("str"))

  // val dest =
  //   source
  //     .into[DestToplevel]
  //     .transform(
  //       Field.const(_.extra, Some(1)),
  //       Field.useNones(_.level1)
  //     )

  // val cos = Field.useNones[Option[Int], Option[Int]].regional(_.element)

  // Field.useNones[Option[Int], Option[Int]].regional(_.element)

  // println(dest)
}
