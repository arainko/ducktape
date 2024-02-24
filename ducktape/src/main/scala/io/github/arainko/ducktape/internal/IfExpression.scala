package io.github.arainko.ducktape.internal

import scala.quoted.*

private[ducktape] def IsInstanceOf(value: Expr[Any], tpe: Type[?])(using Quotes) =
  tpe match {
    case '[tpe] => '{ $value.isInstanceOf[tpe] }
  }

private[ducktape] object IfExpression {
  def apply(branches: List[Branch], orElse: Expr[Nothing])(using Quotes): quotes.reflect.Term = {
    import quotes.reflect.*

    branches match {
      case Branch(cond, value) :: xs => If(cond.asTerm, value.asTerm, IfExpression(xs, orElse))
      case Nil                       => orElse.asTerm
    }
  }

  case class Branch(cond: Expr[Boolean], value: Expr[Any])
}
