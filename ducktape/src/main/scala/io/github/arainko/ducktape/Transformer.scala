package io.github.arainko.ducktape

import io.github.arainko.ducktape.builder.TransformerBuilder
import io.github.arainko.ducktape.internal.*

import scala.collection.{ BuildFrom, Factory }
import scala.deriving.Mirror

@FunctionalInterface
trait Transformer[From, To] {
  def transform(from: From): To
}

object Transformer:

  def apply[A, B](using trans: Transformer[A, B]): Transformer[A, B] = trans

  transparent inline def define[A, B] = TransformerBuilder.create[A, B]

  given [A]: Transformer[A, A] = identity

  inline given [A, B](using A: Mirror.ProductOf[A], B: Mirror.ProductOf[B]): Transformer[A, B] = from => {
    val transformers = Derivation.transformersForAllFields[
      Field.FromLabelsAndTypes[A.MirroredElemLabels, A.MirroredElemTypes],
      Field.FromLabelsAndTypes[B.MirroredElemLabels, B.MirroredElemTypes],
    ]

    Unsafe.constructInstance(from.asInstanceOf[Product], B) { (labelsToValuesOfA, label) =>
      transformers(label).transform(labelsToValuesOfA(label))
    }
  }

  inline given [A, B](using A: Mirror.SumOf[A], B: Mirror.SumOf[B]): Transformer[A, B] = from => {
    val ordinalsOfAToSingletonsOfB = Derivation.ordinalsForMatchingSingletons[
      Case.FromLabelsAndTypes[A.MirroredElemLabels, A.MirroredElemTypes],
      Case.FromLabelsAndTypes[B.MirroredElemLabels, B.MirroredElemTypes],
    ]
    val fromOrdinal = Ordinal(A.ordinal(from))
    ordinalsOfAToSingletonsOfB(fromOrdinal).asInstanceOf[B]
  }

  given [A, B](using Transformer[A, B]): Transformer[A, Option[B]] =
    Transformer[A, B].transform.andThen(Some.apply)(_)

  given [A, B](using Transformer[A, B]): Transformer[Option[A], Option[B]] =
    from => from.map(Transformer[A, B].transform)

  given [A1, A2, B1, B2](using Transformer[A1, A2], Transformer[B1, B2]): Transformer[Either[A1, B1], Either[A2, B2]] = {
    case Right(value) => Right(Transformer[B1, B2].transform(value))
    case Left(value)  => Left(Transformer[A1, A2].transform(value))
  }

  given [A, B, CollFrom[+elem] <: Iterable[elem], CollTo[+elem] <: Iterable[elem]](using
    trans: Transformer[A, B],
    fac: Factory[B, CollTo[B]]
  ): Transformer[CollFrom[A], CollTo[B]] = from => from.foldLeft(fac.newBuilder)(_ += trans.transform(_)).result

  type ValueMirror[T, A] = Mirror.Product {
    type MirroredType = T
    type MirroredMonoType = T
    type MirroredElemTypes = A *: EmptyTuple
  }

  inline given [A <: Product, SimpleType](using
    A: ValueMirror[A, SimpleType]
  ): Transformer[A, SimpleType] =
    from => from.productElement(0).asInstanceOf[SimpleType]

  inline given [A <: Product, SimpleType](using
    A: ValueMirror[A, SimpleType]
  ): Transformer[SimpleType, A] =
    from => A.fromProduct(Tuple(from))

end Transformer
