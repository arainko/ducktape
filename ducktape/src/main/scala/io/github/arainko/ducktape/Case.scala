package io.github.arainko.ducktape

import scala.compiletime.*
import scala.compiletime.ops.int.*
import scala.deriving.Mirror

trait Case[Label <: String, Type, Ordinal <: Int]

object Case {

  type FromLabelsAndTypes[Labels <: Tuple, Types <: Tuple] = FromLabelsAndTypesWithAcc[Labels, Types, 0]

  type OrdinalForType[Type, Cases <: Tuple] <: Int =
    Cases match {
      case EmptyTuple                  => Nothing
      case Case[_, Type, ordinal] *: _ => ordinal
      case _ *: tail                   => OrdinalForType[Type, tail]
    }

  type DropByType[Type, Cases <: Tuple] <: Tuple =
    Cases match {
      case EmptyTuple               => EmptyTuple
      case Case[_, Type, _] *: tail => tail
      case head *: tail             => head *: DropByType[Type, tail]
    }

  type FromLabelsAndTypesWithAcc[Labels <: Tuple, Types <: Tuple, Ord <: Int] <: Tuple =
    (Labels, Types) match {
      case (EmptyTuple, EmptyTuple) => EmptyTuple
      case (labelHead *: labelTail, typeHead *: typeTail) =>
        Case[labelHead, typeHead, Ord] *: FromLabelsAndTypesWithAcc[labelTail, typeTail, S[Ord]]
    }

}
