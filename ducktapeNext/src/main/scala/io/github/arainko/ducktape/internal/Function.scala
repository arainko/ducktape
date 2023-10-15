package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.Debug

import scala.collection.immutable.ListMap
import scala.quoted.*

final case class Function(args: ListMap[String, Type[?]], returnTpe: Type[?], expr: Expr[Any]) derives Debug {

  def appliedTo(using Quotes)(terms: List[quotes.reflect.Term]): Expr[Any] = {
    import quotes.reflect.*
    Expr.betaReduce(Select.unique(expr.asTerm, "apply").appliedToArgs(terms).asExpr)
  }

  // TODO: This should probably not live here
  def fillInExprTypes[TypeFn[args <: FunctionArguments]: Type](initial: Expr[TypeFn[Nothing]])(using Quotes) = {
    import quotes.reflect.*

    val refinedArgs = args.foldLeft(TypeRepr.of[FunctionArguments]) {
      case (acc, name -> tpe) =>
        Refinement(acc, name, tpe.repr)
    }
    returnTpe -> refinedArgs.asType match {
      case '[retTpe] -> '[Function.IsFuncArgs[args]] => '{ $initial.asInstanceOf[TypeFn[args]] }
    }
  }

}

object Function {
  private type IsFuncArgs[A <: FunctionArguments] = A

  def fromExpr(expr: Expr[Any])(using Quotes): Option[Function] = {
    import quotes.reflect.*

    Logger.debug("Trying to construct a Function from an Expr", expr.asTerm)

    unapply(expr.asTerm).map { (vals, term) =>
      val returnTpe = term.tpe.asType
      val args = vals.map(valDef => valDef.name -> valDef.tpt.tpe.asType).to(ListMap)
      Function(args, returnTpe, expr)
    }
  }

  def fromFunctionArguments[Args <: FunctionArguments: Type](functionExpr: Expr[Any])(using Quotes): Option[Function] = {
    import quotes.reflect.*
    val repr = TypeRepr.of[Args]

    Option.when(Type.of[Args])

    val args =
      List
        .unfold(Type.of[Args].repr) {
          case Refinement(leftover, name, tpe) => Some(name -> tpe.asType, leftover)
          case other                           => None
        }
        .reverse
        .to(ListMap)

    Function(args, ???, functionExpr)
  }

  // TODO: This should probably not live here
  transparent inline def fillInTypes[TypeFn[args <: FunctionArguments]](
    inline function: Any,
    initial: TypeFn[Nothing]
  ) = ${ fillInTypesMacro('function, 'initial) }

  private def fillInTypesMacro[TypeFn[args <: FunctionArguments]: Type](
    function: Expr[Any],
    initial: Expr[TypeFn[Nothing]]
  )(using Quotes) = {
    import quotes.reflect.*

    val func = fromExpr(function).getOrElse(report.errorAndAbort(s"Couldn't construct a function TODO ERROR MESSAGE"))
    func.fillInExprTypes(initial)
  }

  private def unapply(using Quotes)(arg: quotes.reflect.Term): Option[(List[quotes.reflect.ValDef], quotes.reflect.Term)] = {
    import quotes.reflect.*

    arg match {
      case Lambda(vals, term) =>
        Logger.debug(s"Matched Lambda with vals = ${vals.map(_.name)}")
        Some(vals -> term)
      case Inlined(_, _, nested) =>
        Logger.debug("Matched Inlined, recursing...")
        unapply(nested)
      case term =>
        Logger.debug("Encountered unexpected tree", term)
        None
    }
  }
}
