package io.github.arainko.ducktape.fallible.failfast

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.fallible.model.*
import io.github.arainko.ducktape.fallible.model.basic.{ CreateConference, CreateTalk, UpdateTalk }

import java.time.LocalDate

class DerivedInstanceSuite extends DucktapeSuite {

  private given Transformer.Mode.FailFast[[A] =>> Either[String, A]] =
    Transformer.Mode.FailFast.either[String]

  successfulTransformationTest("fallibleTo")(
    _.fallibleTo[Talk]
  )

  successfulTransformationTest("fallibleVia")(
    _.fallibleVia(Talk.apply)
  )

  successfulTransformationTest("into.fallible")(
    _.into[Talk].fallible.transform()
  )

  successfulTransformationTest("Transformer.define.fallible")(
    Transformer.define[basic.CreateTalk, Talk].fallible.build().transform
  )

  successfulTransformationTest("Transformer.defineVia.fallible")(
    Transformer.defineVia[basic.CreateTalk](Talk.apply).fallible.build().transform
  )

  failingTransformationTest("fallibleTo")(
    _.fallibleTo[Talk]
  )

  failingTransformationTest("fallibleVia")(
    _.fallibleVia(Talk.apply)
  )

  failingTransformationTest("into.fallible")(
    _.into[Talk].fallible.transform()
  )

  failingTransformationTest("Transformer.define.fallible")(
    Transformer.define[basic.CreateTalk, Talk].fallible.build().transform
  )

  private def successfulTransformationTest(name: String)(transformation: basic.CreateTalk => Either[String, Talk]) =
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

  private def failingTransformationTest(name: String)(transformation: basic.CreateTalk => Either[String, Talk]) =
    test(s"CreateTalk fails to transform into Talk and accumulates all errors using - $name") {
      val createTalk =
        basic.CreateTalk(
          "talk" * 10,
          "pitch" * 100,
          basic.Presenter("Presenter" * 10, "bio" * 200, Some(basic.Presenter.Pronouns.`They/them`))
        )

      val expected =
        Left("Invalid Talk.Name")

      assertEquals(transformation(createTalk), expected)
    }

}
