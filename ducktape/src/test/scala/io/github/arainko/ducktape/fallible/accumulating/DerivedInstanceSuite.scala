package io.github.arainko.ducktape.fallible.accumulating

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.fallible.model.*
import java.time.LocalDate
import io.github.arainko.ducktape.fallible.model.basic.CreateConference

class DerivedInstanceSuite extends DucktapeSuite {
  test("CreateConference sucessfully transforms into Conference.Info") {
    val createConf = basic.CreateConference("name", basic.DateSpan(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 2)), "Larks")

    val expected =
      Conference.Info(
        Conference.Name.unsafe("name"),
        DateSpan.unsafe(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 2)),
        Conference.City.unsafe("Larks")
      )

    val actual =
      List(
        createConf
          .into[Conference.Info]
          .accumulating[[A] =>> Either[List[String], A]]
          .transform(Field.fallibleComputed(_.dateSpan, _.dateSpan.via(DateSpan.create))),
        createConf
          .intoVia(Conference.Info.apply)
          .accumulating[[A] =>> Either[List[String], A]]
          .transform(Arg.fallibleComputed(_.dateSpan, _.dateSpan.via(DateSpan.create))),
        Transformer
          .define[CreateConference, Conference.Info]
          .accumulating[[A] =>> Either[List[String], A]]
          .build(Field.fallibleComputed(_.dateSpan, _.dateSpan.via(DateSpan.create)))
          .transform(createConf),
        Transformer
          .defineVia[CreateConference](Conference.Info.apply)
          .accumulating[[A] =>> Either[List[String], A]]
          .build(Arg.fallibleComputed(_.dateSpan, _.dateSpan.via(DateSpan.create)))
          .transform(createConf)
      )

    actual.foreach(actual => assertEquals(actual, Right(expected)))
  }

  //TODO: Use Talk and Create or UpdateTalk instead of Conference (or in addition to)
  test("transforming into Conference.Info accumulates all errors") {
    val createConf = basic.CreateConference("name", basic.DateSpan(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 2)), "Larks")

  }
}
