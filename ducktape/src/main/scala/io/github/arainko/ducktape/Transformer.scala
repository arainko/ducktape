package io.github.arainko.ducktape

import scala.deriving.Mirror
import io.github.arainko.ducktape.internal.Derivation

@FunctionalInterface
trait Transformer[From, To] {
  def transform(from: From): To
}

object Transformer {
  inline given [A, B](using A: Mirror.ProductOf[A], B: Mirror.ProductOf[B]): Transformer[A, B] = from => {
    val fromAsProd = from.asInstanceOf[Product]
    val labelsToValuesOfA = fromAsProd.productElementNames.zip(fromAsProd.productIterator).toMap
    val transformers = Derivation.transformersForAllFields[
      Field.FromLabelsAndTypes[A.MirroredElemLabels, A.MirroredElemTypes],
      Field.FromLabelsAndTypes[B.MirroredElemLabels, B.MirroredElemTypes],
    ]
    val labelIndicesOfB = Derivation.labelIndices[B.MirroredElemLabels, 0]
    val valuesOfB = Array.fill(labelIndicesOfB.size)(null.asInstanceOf[Any])
    labelIndicesOfB.foreach { (label, idx) =>
      val valueForLabel = transformers(label).transform(labelsToValuesOfA(label))
      valuesOfB.update(idx, valueForLabel)
    }
    B.fromProduct(Tuple.fromArray(valuesOfB))
  }

  given [A]: Transformer[A, A] = identity

  given [A, B](using transformer: Transformer[A, B]): Transformer[A, Option[B]] =
    transformer.transform.andThen(Some.apply)(_)
}
