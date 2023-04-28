package io.github.arainko.ducktape.fallible.model.basic

import java.util.UUID

case class GetConference(id: UUID, name: String, dateSpan: DateSpan, city: String, talks: Vector[GetTalk])
