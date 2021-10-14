package io.github.arainko.ducktape

import scala.deriving.Mirror

trait Field[Label <: String, Type]

object Field:

  type FromLabelsAndTypes[Labels <: Tuple, Types <: Tuple] <: Tuple =
    (Labels, Types) match {
      case (EmptyTuple, EmptyTuple) => EmptyTuple
      case (labelHead *: labelTail, typeHead *: typeTail) =>
        Field[labelHead, typeHead] *: FromLabelsAndTypes[labelTail, typeTail]
    }

  type DropByLabel[Label <: String, Fields <: Tuple] <: Tuple =
    Fields match {
      case EmptyTuple                => EmptyTuple
      case Field[Label, tpe] *: tail => tail
      case head *: tail              => head *: DropByLabel[Label, tail]
    }

  type TypeForLabel[Label <: String, Fields <: Tuple] =
    Fields match {
      case EmptyTuple                => Nothing
      case Field[Label, tpe] *: tail => tpe
      case head *: tail              => TypeForLabel[Label, tail]
    }

  type ExtractLabels[Fields <: Tuple] <: Tuple =
    Fields match {
      case EmptyTuple              => EmptyTuple
      case Field[label, _] *: tail => label *: ExtractLabels[tail]
    }

  type ExtractTypes[Fields <: Tuple] <: Tuple =
    Fields match {
      case EmptyTuple            => EmptyTuple
      case Field[_, tpe] *: tail => tpe *: ExtractTypes[tail]
    }

end Field
