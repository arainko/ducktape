package io.github.arainko.ducktape.function

sealed trait NamedArgument[Name <: String, Type]

object NamedArgument {
  type FindByName[Name <: String, NamedArgs <: Tuple] =
    NamedArgs match {
      case EmptyTuple                    => Nothing
      case NamedArgument[Name, tpe] *: _ => tpe
      case head *: tail                  => FindByName[Name, tail]
    }
}
