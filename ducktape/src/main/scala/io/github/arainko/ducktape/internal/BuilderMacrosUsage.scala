package io.github.arainko.ducktape.internal

import scala.deriving.Mirror
import io.github.arainko.ducktape.*

final case class Costam(costam: Int, field: String, field1: Int)


object BuilderMacrosUsage {
  val mirror = summon[Mirror.Of[Costam]]
  val cos/*: Field["field", String] *: Field["field1", Int] *: EmptyTuple*/ = 
    BuilderMacros.droppedWithLambda[
    Costam,
    Int,
    Field.FromLabelsAndTypes[mirror.MirroredElemLabels, mirror.MirroredElemTypes]
    ](_.costam)
}
