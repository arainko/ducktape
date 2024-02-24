package io.github.arainko.ducktape.fallible.model

enum Pronouns {
  case `They/them`, `She/her`, `He/him`
}

object Pronouns {
  def fromString(value: String): Option[Pronouns] =
    PartialFunction.condOpt(value) {
      case "They/them" => Pronouns.`They/them`
      case "She/her"   => Pronouns.`She/her`
      case "He/him"    => Pronouns.`He/him`
    }
}
