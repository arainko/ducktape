package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.Structure.Product.Kind

import scala.quoted.*

private[ducktape] sealed trait ProductConstructor {
  def apply(fields: Seq[Expr[Any]])(using Quotes): Expr[Any]
}

private[ducktape] object ProductConstructor {
  final class Primary(structure: Structure.Product) extends ProductConstructor {
    def apply(fields: Seq[Expr[Any]])(using Quotes): Expr[Any] = {
      import quotes.reflect.*

      structure.kind match
        case Kind.CaseClass =>
          Constructor(structure.tpe.repr)
            .appliedToArgs(fields.map(value => value.asTerm).toList)
            .asExpr
        case Kind.NamedTuple(erasedTupleTpe) =>
          Typed(Expr.ofTupleFromSeq(fields).asTerm, TypeTree.of(using structure.tpe)).asExpr
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
