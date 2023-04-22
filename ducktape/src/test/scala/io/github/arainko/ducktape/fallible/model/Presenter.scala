package io.github.arainko.ducktape.fallible.model

final case class Presenter(name: Presenter.Name, bio: Presenter.Bio, pronouns: Option[Pronouns])

object Presenter {
  object Name extends NewtypeValidated[String](MaxSize(20, "Presenter.Name"))
  export Name.Type as Name

  object Bio extends NewtypeValidated[String](MaxSize(300, "Presenter.Bio"))
  export Bio.Type as Bio
}
