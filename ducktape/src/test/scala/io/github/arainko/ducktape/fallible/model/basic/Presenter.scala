package io.github.arainko.ducktape.fallible.model.basic

case class Presenter(name: String, bio: String, pronouns: Option[Presenter.Pronouns])

object Presenter {
  sealed abstract class Pronouns(val value: String) extends Product with Serializable {
    override def toString: String = value.toString
  }

  object Pronouns {
    case object `They/them` extends Pronouns("they/them")
    case object `She/her` extends Pronouns("she/her")
    case object `He/him` extends Pronouns("he/him")
  }
}
