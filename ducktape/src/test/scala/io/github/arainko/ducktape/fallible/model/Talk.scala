package io.github.arainko.ducktape.fallible.model

import java.util.UUID

final case class Talk(id: Talk.Id, name: Talk.Name, elevatorPitch: Talk.ElevatorPitch, presenter: Presenter)

object Talk {
  object Id extends NewtypeValidated[UUID](AlwaysValid)
  export Id.Type as Id

  object Name extends NewtypeValidated[String](MaxSize(20))
  export Name.Type as Name

  object ElevatorPitch extends NewtypeValidated[String](MaxSize(300))
  export ElevatorPitch.Type as ElevatorPitch
}
