package io.github.arainko.ducktape

import munit.*
import io.github.arainko.DucktapeSuite
import io.github.arainko.ducktape.internal.standalone.MakeTransformer
import io.github.arainko.ducktape.internal.macros.*
import scala.quoted.*


class MakeTransformerSpec extends DucktapeSuite {

  test("should match ForProduct.make") {
    final case class Person(age: Int, name: String)
    final case class Person2(age: Int, name: String)

    MakeTransformerTestMacros.check(Transformer.forProducts[Person, Person2])
  }

  test("should match FromAnyVal.make") {
    MakeTransformerTestMacros.check(Transformer.fromAnyVal[MakeTransformerSpec.Wrapped, Int])
  }

  test("should match ToAnyVal.make") {
    MakeTransformerTestMacros.check(Transformer.toAnyVal[Int, MakeTransformerSpec.Wrapped])
  }

}

object MakeTransformerSpec {
  final case class Wrapped(int: Int) extends AnyVal

  inline def check(inline transformer: Transformer[?, ?]): Unit = ${ checkMacro('transformer) }

  def checkMacro(transformer: Expr[Transformer[?, ?]])(using Quotes): Expr[Unit] = {
    import quotes.reflect.*

    MakeTransformer
      .unapply(transformer.asTerm)
      .map(_ => '{ () })
      .getOrElse(report.errorAndAbort(s"Not matched: ${transformer.asTerm.show(using Printer.TreeStructure)}"))
  }
}
