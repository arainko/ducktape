package io.github.arainko.ducktape.fallible.model

import java.util.UUID

final case class Conference(id: Conference.Id, info: Conference.Info, talks: Vector[Talk])

object Conference {
  final case class Info(name: Conference.Name, dateSpan: DateSpan, city: Conference.City)

  object Id extends NewtypeValidated[UUID](AlwaysValid)
  export Id.Type as Id

  object Name extends NewtypeValidated[String](MaxSize(20, "Conference.Name"))
  export Name.Type as Name

  object City extends NewtypeValidated[String](MaxSize(20, "Conference.City"))
  export City.Type as City
}
