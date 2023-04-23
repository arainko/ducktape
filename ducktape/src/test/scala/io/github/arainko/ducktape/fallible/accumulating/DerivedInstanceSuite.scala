package io.github.arainko.ducktape.fallible.accumulating

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.fallible.model.*
import io.github.arainko.ducktape.fallible.model.basic.{ CreateConference, CreateTalk, UpdateTalk }

import java.time.LocalDate

class DerivedInstanceSuite extends DucktapeSuite {

  private given Transformer.Accumulating.Support[[A] =>> Either[List[String], A]] = 
    Transformer.Accumulating.Support.either[String, List]

  successfulTransformationTest("accumulatingTo")(
    _.accumulatingTo[Talk]
  )

  successfulTransformationTest("accumulatingVia")(
    _.accumulatingVia(Talk.apply)
  )

  successfulTransformationTest("into.accumulating")(
    _.into[Talk].accumulating.transform()
  )

  successfulTransformationTest("Transformer.define.accumulating")(
    Transformer.define[basic.CreateTalk, Talk].accumulating.build().transform
  )

  successfulTransformationTest("Transforme.defineVia.accumulating")(
    Transformer.defineVia[basic.CreateTalk](Talk.apply).accumulating.build().transform
  )

  failingTransformationTest("accumulatingTo")(
    _.accumulatingTo[Talk]
  )

  failingTransformationTest("accumulatingVia")(
    _.accumulatingVia(Talk.apply)
  )

  failingTransformationTest("into.accumulating")(
    _.into[Talk].accumulating.transform()
  )

  failingTransformationTest("Transformer.define.accumulating")(
    Transformer.define[basic.CreateTalk, Talk].accumulating.build().transform
  )

  private def successfulTransformationTest(name: String)(transformation: basic.CreateTalk => Either[List[String], Talk]) =
    test(s"CreateTalk transform into Talk using - $name") {
      val createTalk =
        basic.CreateTalk("talk", "pitch", basic.Presenter("Presenter", "bio", Some(basic.Presenter.Pronouns.`They/them`)))

      val expected =
        Talk(
          Talk.Name.unsafe("talk"),
          Talk.ElevatorPitch.unsafe("pitch"),
          Presenter(
            Presenter.Name.unsafe("Presenter"),
            Presenter.Bio.unsafe("bio"),
            Some(Pronouns.`They/them`)
          )
        )

      assertEquals(transformation(createTalk), Right(expected))
    }

  private def failingTransformationTest(name: String)(transformation: basic.CreateTalk => Either[List[String], Talk]) =
    test(s"CreateTalk fails to transform into Talk and accumulates all errors using - $name") {
      val createTalk =
        basic.CreateTalk(
          "talk" * 10,
          "pitch" * 100,
          basic.Presenter("Presenter" * 10, "bio" * 200, Some(basic.Presenter.Pronouns.`They/them`))
        )

      val expected =
        Left(
          "Invalid Talk.Name" :: "Invalid Talk.ElevatorPitch" :: "Invalid Presenter.Name" :: "Invalid Presenter.Bio" :: Nil
        )

      assertEquals(transformation(createTalk), expected)
    }

}
