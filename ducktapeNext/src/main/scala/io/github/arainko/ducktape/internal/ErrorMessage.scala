package io.github.arainko.ducktape.internal

import scala.quoted.Quotes

sealed abstract class ErrorMessage(val relatesTo: ErrorMessage.RelatesTo) {
  def render(using Quotes): String
}

object ErrorMessage {
  enum RelatesTo {
    case Source, Dest
  }

  case class InvalidFieldAccessor(fieldName: String) extends ErrorMessage(RelatesTo.Dest) {
    def render(using Quotes): String = s"'$fieldName' is not a valid field accessor"
  }

  case class InvalidArgAccessor(fieldName: String) extends ErrorMessage(RelatesTo.Dest) {
    def render(using Quotes): String = s"'$fieldName' is not a valid arg accessor"
  }

  case class InvalidCaseAccessor(tpe: Type[?]) extends ErrorMessage(RelatesTo.Dest) {
    def render(using Quotes): String = s"'$fieldName' is not a valid arg accessor"
  }

}

// final case class ErrorMessage()
