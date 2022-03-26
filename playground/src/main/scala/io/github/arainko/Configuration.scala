package io.github.arainko

sealed abstract class Configuration(val name: String)

object Configuration {
  final case class Const[Label <: String](label: Label) extends Configuration(label)
  final case class Computed[Label <: String](label: Label) extends Configuration(label)
  final case class Renamed[Dest <: String, Source <: String](dest: Dest, source: Source) extends Configuration(dest)

  type RemoveByLabel[Label <: String, Config <: Tuple] <: Tuple =
    Config match {
      case EmptyTuple                => EmptyTuple
      case Const[Label] *: tail      => RemoveByLabel[Label, tail]
      case Computed[Label] *: tail   => RemoveByLabel[Label, tail]
      case Renamed[Label, _] *: tail => RemoveByLabel[Label, tail]
      case head *: tail              => head *: RemoveByLabel[Label, tail]
    }
}
