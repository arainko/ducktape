package io.github.arainko.ducktape

import scala.deriving.Mirror
import scala.compiletime.*
import scala.compiletime.ops.int.*

trait Case[Label <: String, Type, Ordinal <: Int]

object Case {

  type FromLabelsAndTypes[Labels <: Tuple, Types <: Tuple] = FromLabelsAndTypesWithAcc[Labels, Types, 0]

  type FromLabelsAndTypesWithAcc[Labels <: Tuple, Types <: Tuple, Ord <: Int] <: Tuple =
    (Labels, Types) match
      case (EmptyTuple, EmptyTuple) => EmptyTuple
      case (labelHead *: labelTail, typeHead *: typeTail) =>
        Case[labelHead, typeHead, Ord] *: FromLabelsAndTypesWithAcc[labelTail, typeTail, S[Ord]]
}
