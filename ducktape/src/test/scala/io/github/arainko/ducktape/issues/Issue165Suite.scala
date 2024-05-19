package io.github.arainko.ducktape.issues

import io.github.arainko.ducktape.*

class Issue165Suite extends DucktapeSuite {
  test("rejects _.to in given Transformer definitions") {
    assertFailsToCompileWith {
      """
      case class A(a: Int)
      case class B(b: Int)
      given Transformer[A, B] = _.to[B]
      """
    }(
      "Detected usage of `_.to[B]`, `_.fallibleTo[B]`, `_.into[B].transform()` or `_.into[B].fallible.transform()` in a given Transformer definition which results in a self-looping Transformer. Please use `Transformer.define[A, B]` or `Transformer.define[A, B].fallible` (for some types A and B) to create Transformer definitions @ B"
    )
  }

  test("rejects _.into.transform() in given Transformer definitions") {
    assertFailsToCompileWith {
      """
      case class A(a: Int)
      case class B(b: Int)
      given Transformer[A, B] = _.into[B].transform()
      """
    }(
      "Detected usage of `_.to[B]`, `_.fallibleTo[B]`, `_.into[B].transform()` or `_.into[B].fallible.transform()` in a given Transformer definition which results in a self-looping Transformer. Please use `Transformer.define[A, B]` or `Transformer.define[A, B].fallible` (for some types A and B) to create Transformer definitions @ B"
    )
  }

  test("rejects _.falibleTo in given Trasformer.Fallible definitions") {
    assertFailsToCompileWith {
      """
      given Mode.FailFast.Option with {}

      case class A(a: Int)
      case class B(b: Int)
      given Transformer.Fallible[Option, A, B] = _.fallibleTo[B]
      """
    }(
      "Detected usage of `_.to[B]`, `_.fallibleTo[B]`, `_.into[B].transform()` or `_.into[B].fallible.transform()` in a given Transformer definition which results in a self-looping Transformer. Please use `Transformer.define[A, B]` or `Transformer.define[A, B].fallible` (for some types A and B) to create Transformer definitions @ B"
    )
  }

  test("rejects _.into.falible in given Trasformer.Fallible definitions") {
    assertFailsToCompileWith {
      """
      given Mode.FailFast.Option with {}

      case class A(a: Int)
      case class B(b: Int)
      given Transformer.Fallible[Option, A, B] = _.into[B].fallible.transform()
      """
    }(
      "Detected usage of `_.to[B]`, `_.fallibleTo[B]`, `_.into[B].transform()` or `_.into[B].fallible.transform()` in a given Transformer definition which results in a self-looping Transformer. Please use `Transformer.define[A, B]` or `Transformer.define[A, B].fallible` (for some types A and B) to create Transformer definitions @ B"
    )
  }
}
