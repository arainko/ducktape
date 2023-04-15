package io.github.arainko.ducktape.issues

import io.github.arainko.ducktape.macros.AstInstanceCounter
import io.github.arainko.ducktape.{ DucktapeSuite, Transformer }

// https://github.com/arainko/ducktape/issues/41
class Issue41Spec extends DucktapeSuite {

  test("Nested transformers are optimized away when case class' companion doesn't have vals inside") {
    final case class TestClass(value: String, in: Inside)
    final case class Inside(str: String)

    final case class TestClass2(value: String, in: Inside2)
    final case class Inside2(str: String)

    val roughAstCount = AstInstanceCounter.roughlyCount[Transformer[?, ?]](summon[Transformer[TestClass, TestClass2]])
    assert(clue(roughAstCount) == 10)
  }

  test("Nested transformers are optimized away when case class' companion has vals inside") {
    final case class TestClass(value: String, in: Inside)

    object TestClass {
      val a = 1
    }

    final case class Inside(str: String)

    object Inside {
      val a = 1
    }

    final case class TestClass2(value: String, in: Inside2)

    object TestClass2 {
      val a = 1
    }

    final case class Inside2(str: String)

    object Inside2 {
      val a = 1
    }

    val roughAstCount =
      AstInstanceCounter.roughlyCount[Transformer[?, ?]](summon[Transformer[TestClass, TestClass2]])

    assert(clue(roughAstCount) == 10)
  }
}
