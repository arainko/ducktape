package io.github.arainko.ducktape

import scala.deriving.Mirror
import io.github.arainko.ducktape.internal.{ Derivation, Unsafe }
import scala.collection.Factory
import scala.collection.BuildFrom

@FunctionalInterface
trait Transformer[From, To] {
  def transform(from: From): To
}

object Transformer {

  def apply[A, B](using trans: Transformer[A, B]): Transformer[A, B] = trans

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
    ordinalsOfAToSingletonsOfB(A.ordinal(from)).asInstanceOf[B]
  }

  inline given [A, B](using A: Mirror.SumOf[A], B: Mirror.SumOf[B]): Transformer[A, B] = from => {
    val ordinalsOfAToSingletonsOfB = Derivation.ordinalsForMatchingSingletons[
      Case.FromLabelsAndTypes[A.MirroredElemLabels, A.MirroredElemTypes],
      Case.FromLabelsAndTypes[B.MirroredElemLabels, B.MirroredElemTypes],
    ]
    ordinalsOfAToSingletonsOfB(A.ordinal(from)).asInstanceOf[B]
  }

  given [A]: Transformer[A, A] = identity

  given [A, B](using Transformer[A, B]): Transformer[A, Option[B]] =
    Transformer[A, B].transform.andThen(Some.apply)(_)

  given [A, B, CollFrom[+elem] <: Iterable[elem], CollTo[+elem] <: Iterable[elem]](using
    trans: Transformer[A, B],
    fac: Factory[B, CollTo[B]]
  ): Transformer[CollFrom[A], CollTo[B]] = from => from.foldLeft(fac.newBuilder)(_ += trans.transform(_)).result

}
