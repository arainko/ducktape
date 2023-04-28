package io.github.arainko.ducktape.fallible.model

import java.util.UUID

final case class Talk(name: Talk.Name, elevatorPitch: Talk.ElevatorPitch, presenter: Presenter)

object Talk {
  object Name extends NewtypeValidated[String](MaxSize(20, "Talk.Name"))
  export Name.Type as Name

  object ElevatorPitch extends NewtypeValidated[String](MaxSize(300, "Talk.ElevatorPitch"))
  export ElevatorPitch.Type as ElevatorPitch
}
