package io.github.arainko.ducktape.fallible.model

import java.time.LocalDate

final case class DateSpan private (start: LocalDate, end: LocalDate)

object DateSpan {
  def create(start: LocalDate, end: LocalDate): Either[List[String], DateSpan] =
    Either
      .cond(start.isBefore(end), DateSpan(start, end), "Invalid DateSpan - start is not before the end")
      .left.map(::(_, Nil))

  def unsafe(start: LocalDate, end: LocalDate): DateSpan = DateSpan(start, end)
}
