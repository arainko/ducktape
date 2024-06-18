package io.github.arainko.ducktape.internal

import scala.quoted.*

private[ducktape] sealed trait ProductConstructor {
  def apply(fields: Seq[Expr[Any]])(using Quotes): Expr[Any]
}

private[ducktape] object ProductConstructor {
  final class Primary(structure: Structure.Product) extends ProductConstructor {
    def apply(fields: Seq[Expr[Any]])(using Quotes): Expr[Any] = {
      import quotes.reflect.*

      Constructor(structure.tpe.repr)
        .appliedToArgs(fields.map(value => value.asTerm).toList)
        .asExpr
    }
  }

  case object Tuple extends ProductConstructor {
    def apply(fields: Seq[Expr[Any]])(using Quotes): Expr[Any] = 
      Expr.ofTupleFromSeq(fields)
  }

  final class Func(function: Function) extends ProductConstructor {
    def apply(fields: Seq[Expr[Any]])(using Quotes): Expr[Any] = {
      import quotes.reflect.*
      function.appliedTo(fields.map(_.asTerm).toList)
    }
  }
}
