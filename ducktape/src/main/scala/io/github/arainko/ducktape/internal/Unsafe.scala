package io.github.arainko.ducktape.internal

import scala.deriving.Mirror

object Unsafe {
  inline def constructInstance[To](
    from: Product, To: Mirror.ProductOf[To] 
  )(unsafeMapper: (String, Int) => Any) = {
    val labelsToValuesOfFrom = from.productElementNames.zip(from.productIterator).toMap
    val labelIndicesOfTo = Derivation.labelIndices[To.MirroredElemLabels, 0]
    val valueArrayOfTo = new Array[Any](labelIndicesOfTo.size)
    labelIndicesOfTo.foreach { (label, idx) =>
      val valueForLabel = unsafeMapper(label, idx)
      valueArrayOfTo.update(idx, valueForLabel)
    }
    To.fromProduct(Tuple.fromArray(valueArrayOfTo))
  }
}
