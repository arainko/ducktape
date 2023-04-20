package io.github.arainko.ducktape.fallible.model

final case class Presenter(name: Presenter.Name, bio: Presenter.Bio, pronouns: Option[Pronouns])

object Presenter {
  object Name extends NewtypeValidated[String](MaxSize(20))
  export Name.Type as Name

  object Bio extends NewtypeValidated[String](MaxSize(300))
  export Bio.Type as Bio
}
