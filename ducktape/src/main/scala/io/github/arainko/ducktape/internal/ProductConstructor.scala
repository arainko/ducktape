package io.github.arainko.ducktape.internal

import scala.quoted.*

private[ducktape] sealed trait ProductConstructor {
  def apply(fields: Map[String, Expr[Any]])(using Quotes): Expr[Any]
}

private[ducktape] object ProductConstructor {
  final class Primary(structure: Structure.Product) extends ProductConstructor {
    def apply(fields: Map[String, Expr[Any]])(using Quotes): Expr[Any] = {
      import quotes.reflect.*

      Constructor(structure.tpe.repr)
        .appliedToArgs(fields.map((name, value) => NamedArg(name, value.asTerm)).toList)
        .asExpr
    }
  }

  final class Func(function: Function) extends ProductConstructor {
    def apply(fields: Map[String, Expr[Any]])(using Quotes): Expr[Any] = {
      import quotes.reflect.*
      val args = function.args.map((name, _) => fields(name).asTerm).toList
      function.appliedTo(args)
    }
  }
}
