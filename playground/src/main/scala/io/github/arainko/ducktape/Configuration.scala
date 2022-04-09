package io.github.arainko.ducktape

sealed trait Configuration

object Configuration {

  sealed trait Product extends Configuration

  object Product {
    sealed trait Const[Label <: String] extends Configuration.Product
    sealed trait Computed[Label <: String] extends Configuration.Product
    sealed trait Renamed[Dest <: String, Source <: String] extends Configuration.Product

    type RemoveByLabel[Label <: String, Config <: Tuple] <: Tuple =
      Config match {
        case EmptyTuple                        => EmptyTuple
        case Product.Const[Label] *: tail      => RemoveByLabel[Label, tail]
        case Product.Computed[Label] *: tail   => RemoveByLabel[Label, tail]
        case Product.Renamed[Label, _] *: tail => RemoveByLabel[Label, tail]
        case head *: tail                      => head *: RemoveByLabel[Label, tail]
      }
  }

  sealed trait Coproduct extends Configuration

  object Coproduct {
    sealed trait Instance[Type] extends Configuration.Coproduct

    type RemoveByType[Type, Config <: Tuple] <: Tuple =
      Config match {
        case EmptyTuple                          => EmptyTuple
        case Coproduct.Instance[Type] *: tail    => RemoveByType[Type, tail]
        case head *: tail                        => head *: RemoveByType[Type, tail]
      }
  }
}
