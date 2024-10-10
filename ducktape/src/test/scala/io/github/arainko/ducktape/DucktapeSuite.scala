package io.github.arainko.ducktape

import munit.{ Compare, FunSuite, Location }

import scala.compiletime.ops.int.*
import scala.reflect.ClassTag

trait DucktapeSuite extends FunSuite {
  def assertEachEquals[Source, Dest](head: Source, tail: Source*)(expected: Dest)(using Location, Compare[Source, Dest]) = {
    (head :: tail.toList).foreach(actual => assertEquals(actual, expected))
  }

  transparent inline def assertFailsToCompile(inline code: String)(using Location) = {
    assert(compiletime.testing.typeChecks(code), "Code snippet compiled despite expecting not to")
  }

  transparent inline def assertFailsToCompileWith(inline code: String)(expected: String*)(using Location) = {
    val errors = compiletime.testing.typeCheckErrors(code).map(_.message).toSet
    assertEquals(errors, expected.toSet, "Error did not contain expected value")
  }

  transparent inline def assertFailsToCompileContains(inline code: String)(head: String, tail: String*)(using Location) = {
    val errors = compiletime.testing.typeCheckErrors(code).map(_.message).toSet
    (head :: tail.toList).foreach(expected => errors.contains(expected))
  }

  inline def assertTransforms[A, B](source: A, expected: B)(using loc: Location) =
    assertEachEquals(
      source.to[B],
      source.into[B].transform(),
      Transformer.define[A, B].build().transform(source)
    )(expected)

  // transparent inline def assertTransformsVia[A, B, Func, Args <: FunctionArguments](
  //   source: A,
  //   inline func: A => B,
  //   inline func1: DefinitionViaBuilder.PartiallyApplied[A] => DefinitionViaBuilder[A, B, Func, Args],
  //   inline func2: A => AppliedViaBuilder[A, B, Func, Args],
  //   expected: B
  // )(using Location) =
  //   assertEachEquals(
  //     source.via(func),
  //     func1(DefinitionViaBuilder.create[A]).build().transform(source),
  //     func2(source).transform()
  //   )(expected)

  inline def assertTransformsFallible[F[+x], M <: Mode[F], A, B](using M)(source: A, expected: F[B])(using loc: Location) =
    assertEachEquals(
      source.fallibleTo[B],
      source.into[B].fallible.transform(),
      Transformer.define[A, B].fallible.build().transform(source)
    )(expected)

  inline def assertTransformsConfigured[A, B](source: A, expected: B)(
    inline config: (Field[A, B] | Case[A, B])*
  )(using loc: Location) =
    assertEachEquals(
      source.into[B].transform(config*),
      Transformer.define[A, B].build(config*).transform(source)
    )(expected)

  inline def assertTransformsFallibleConfigured[F[+x], M <: Mode[F], A, B](using M)(
    source: A,
    expected: F[B]
  )(inline config: (Field.Fallible[F, A, B] | Case.Fallible[F, A, B])*) =
    assertEachEquals(
      source.into[B].fallible.transform(config*),
      Transformer.define[A, B].fallible.build(config*).transform(source)
    )(expected)

  def homogenousTupleOf[A: ClassTag](size: Int, indexToValue: Int => A): Fill[A, size.type] = {
    val values = (0 until size).map(indexToValue).toArray
    Tuple.fromArray(values).asInstanceOf[Fill[A, size.type]]
  }

  type Fill[Tpe, N <: Int] <: Tuple =
    N match {
      case 0     => EmptyTuple
      case S[n1] => Tpe *: Fill[Tpe, n1]
    }

  extension [A](inline self: A) {
    inline def code: A = internal.CodePrinter.code(self)

    inline def structure: A = internal.CodePrinter.structure(self)
  }
}
