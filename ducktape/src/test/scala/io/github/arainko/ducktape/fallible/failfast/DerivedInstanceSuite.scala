package io.github.arainko.ducktape.fallible.failfast


import io.github.arainko.ducktape.fallible.model.*
import io.github.arainko.ducktape.fallible.model.basic.{ CreateConference, CreateTalk, UpdateTalk }
import io.github.arainko.ducktape.DucktapeSuite
import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.fallible.FailFast.Support

class DerivedInstanceSuite extends DucktapeSuite {
  private given Transformer.FailFast.Support[[A] =>> Either[Predef.String, A]] = Transformer.FailFast.Support.either[String]

  successfulTransformationTest("failFastTo")(
    _.failFastTo[Talk]
  )

  successfulTransformationTest("failFastVia")(
    _.failFastVia(Talk.apply)
  )

  successfulTransformationTest("into.failFast")(
    _.into[Talk].failFast.transform()
  )

  successfulTransformationTest("Transformer.define.failFast")(
    Transformer.define[basic.CreateTalk, Talk].failFast.build().transform
  )

  successfulTransformationTest("Transforme.defineVia.failFast")(
    Transformer.defineVia[basic.CreateTalk](Talk.apply).failFast.build().transform
  )

  failingTransformationTest("failFastTo")(
    _.failFastTo[Talk]
  )

  failingTransformationTest("failFastVia")(
    _.failFastVia(Talk.apply)
  )

  failingTransformationTest("into.failFast")(
    _.into[Talk].failFast.transform()
  )

  failingTransformationTest("Transformer.define.failFast")(
    Transformer.define[basic.CreateTalk, Talk].failFast.build().transform
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
    test(s"CreateTalk fails to transform into Talk and short-circuits using - $name") {
      val createTalk =
        basic.CreateTalk(
          "talk" * 10,
          "pitch" * 100,
          basic.Presenter("Presenter" * 10, "bio" * 200, Some(basic.Presenter.Pronouns.`They/them`))
        )

      val expected = Left("Invalid Talk.Name")

      assertEquals(transformation(createTalk), expected)
    }
}
