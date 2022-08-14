package io.github.arainko.ducktape.internal.modules

import scala.quoted.*
import scala.deriving.*
import io.github.arainko.ducktape.Transformer
import scala.deriving.*

private[internal] trait Module {
  val quotes: Quotes

  given Quotes = quotes

  import quotes.reflect.*

  given Printer[TypeRepr] = Printer.TypeReprShortCode
  given Printer[Tree] = Printer.TreeShortCode

  def mirrorOf[A: Type]: Option[Expr[Mirror.Of[A]]] = Expr.summon[Mirror.Of[A]]

  def abort(error: Failure): Nothing = report.errorAndAbort(error.render)

  opaque type Suggestion = String

  object Suggestion {
    def apply(text: String): Suggestion = text
  }

  sealed trait Failure {
    def render: String
  }

  object Failure {
    final case class MirrorMaterialization[A: Type](notFoundTypeMemberName: String) extends Failure {

      def render = s"Mirror materialization for ${Type.show[A]} failed. Member type not found: '$notFoundTypeMemberName'."
    }

    

  }

}
