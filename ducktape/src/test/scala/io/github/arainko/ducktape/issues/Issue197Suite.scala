package io.github.arainko.ducktape.issues

import io.github.arainko.ducktape.*

class Issue197Suite extends DucktapeSuite {
  test("issue 197 repro works") {
    import Issue197Suite.scoped.*
    import Issue197Suite.*
    val id = "id".asInstanceOf[Ident]
    val actual = Issue197Suite.demo.f(From("asd"), id)
    val expected = To("asd", id)
    assertEquals(actual, expected)
  }
}

object Issue197Suite {

  case class From(name: String)

  object scoped {

    opaque type Ident = String

    case class To(name: String, id: Ident)
  }

  object demo {
    def f(
      f: From,
      theId: scoped.Ident
    ): scoped.To =
      f
        .into[scoped.To]
        .transform(Field.const(_.id, theId))
  }
}
