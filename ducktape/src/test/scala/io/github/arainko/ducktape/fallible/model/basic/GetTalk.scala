package io.github.arainko.ducktape.fallible.model.basic

import java.util.UUID

case class GetTalk(id: UUID, name: String, elevatorPitch: String, presenter: Presenter)

