package io.github.arainko.ducktape

import scala.collection.Factory
import scala.deriving.Mirror
import scala.compiletime.*
import io.github.arainko.ducktape.internal.macros.*
import scala.collection.BuildFrom
import io.github.arainko.ducktape.builder.*

@FunctionalInterface
trait Transformer[From, To] {
  def transform(from: From): To
}

object Transformer {
  def apply[A, B](using trans: Transformer[A, B]): Transformer[A, B] = trans

  def define[A, B]: DefinitionBuilder[A, B] = DefinitionBuilder[A, B]

  // def defineVia[A]: ViaBuilder.DefinitionViaPartiallyApplied[A] =
  //   ViaBuilder.DefinitionViaPartiallyApplied[A]

  sealed trait Identity[A] extends Transformer[A, A]

  given [A]: Identity[A] = new {
    def transform(from: A): A = from
  }

  inline given forProducts[A, B](using Mirror.ProductOf[A], Mirror.ProductOf[B]): Transformer[A, B] =
    from => ProductTransformerMacros.transform(from)

  inline given forCoproducts[A, B](using Mirror.SumOf[A], Mirror.SumOf[B]): Transformer[A, B] =
    from => CoproductTransformerMacros.transform(from)

  given [A, B](using Transformer[A, B]): Transformer[A, Option[B]] =
    from => Transformer[A, B].transform.andThen(Some.apply)(from)

  given [A, B](using Transformer[A, B]): Transformer[Option[A], Option[B]] =
    from => from.map(Transformer[A, B].transform)

  given [A1, A2, B1, B2](using Transformer[A1, A2], Transformer[B1, B2]): Transformer[Either[A1, B1], Either[A2, B2]] = {
    case Right(value) => Right(Transformer[B1, B2].transform(value))
    case Left(value)  => Left(Transformer[A1, A2].transform(value))
  }

  given [A, B, CollFrom[+elem] <: Iterable[elem], CollTo[+elem] <: Iterable[elem]](using
    trans: Transformer[A, B],
    factory: Factory[B, CollTo[B]]
  ): Transformer[CollFrom[A], CollTo[B]] = from => from.map(trans.transform).to(factory)

  inline given [A <: Product, SimpleType](using
    A: Mirror.ProductOf[A]
  )(using
    A.MirroredElemLabels <:< NonEmptyTuple,
    A.MirroredElemTypes =:= (SimpleType *: EmptyTuple)
  ): Transformer[A, SimpleType] =
    from => from.productElement(0).asInstanceOf[SimpleType]

  inline given [A <: Product, SimpleType](using
    A: Mirror.ProductOf[A]
  )(using
    A.MirroredElemLabels <:< NonEmptyTuple,
    A.MirroredElemTypes =:= (SimpleType *: EmptyTuple)
  ): Transformer[SimpleType, A] =
    from => A.fromProduct(Tuple(from))

}
