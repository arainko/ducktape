package io.github.arainko.ducktape.internal

import scala.deriving.Mirror

private[ducktape] object Unsafe:
  inline def constructInstance[To](
    from: Product,
    toMirror: Mirror.ProductOf[To]
  )(unsafeMapper: (Map[String, Any], String) => Any) = {
    val labelsToValuesOfFrom = from.productElementNames.zip(from.productIterator).toMap
    val labelIndicesOfTo = Derivation.labelIndices[toMirror.MirroredElemLabels, 0]
    val valueArrayOfTo = new Array[Any](labelIndicesOfTo.size)
    labelIndicesOfTo.foreach { (label, idx) =>
      val valueForLabel = unsafeMapper(labelsToValuesOfFrom, label)
      valueArrayOfTo.update(idx, valueForLabel)
    }
    toMirror.fromProduct(Tuple.fromArray(valueArrayOfTo))
  }

end Unsafe
