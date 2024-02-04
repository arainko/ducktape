package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.*

import scala.collection.immutable.ListMap
import scala.quoted.*

private[ducktape] final case class Function(args: ListMap[String, Type[?]], returnTpe: Type[?], expr: Expr[Any]) derives Debug {

  def appliedTo(using Quotes)(terms: List[quotes.reflect.Term]): Expr[Any] = {
    import quotes.reflect.*
    Expr.betaReduce(
      Select.unique(expr.asTerm, "apply").appliedToArgs(terms).asExpr
    )
  }

  // TODO: This should probably not live here
  def encodeAsType[TypeFn[args <: FunctionArguments, retTpe]: Type](initial: Expr[TypeFn[Nothing, Nothing]])(using Quotes) = {
    import quotes.reflect.*

    Logger.info("Encoding a Function as type for later usage...")
    val refinedArgs = args.foldLeft(TypeRepr.of[FunctionArguments]) {
      case (acc, name -> tpe) =>
        Refinement(acc, name, tpe.repr)
    }
    returnTpe -> refinedArgs.asType match {
      case '[retTpe] -> '[Function.IsFuncArgs[args]] => '{ $initial.asInstanceOf[TypeFn[args, retTpe]] }
    }
  }

}

private[ducktape] object Function {
  private type IsFuncArgs[A <: FunctionArguments] = A

  def fromExpr(expr: Expr[Any])(using Quotes): Option[Function] = {
    import quotes.reflect.*

    Logger.debug("Trying to construct a Function from an Expr", expr.asTerm)

    unapply(expr.asTerm).map { (vals, term) =>
      val returnTpe = term.tpe.asType
      val args = vals.map(valDef => valDef.name -> valDef.tpt.tpe.asType).to(ListMap)
      Logger.loggedDebug("Result"):
        Function(args, returnTpe, expr)
    }
  }

  def fromFunctionArguments[Args <: FunctionArguments: Type, Func: Type](
    functionExpr: Expr[Func]
  )(using Quotes): Option[Function] = {
    import quotes.reflect.*
    val repr = TypeRepr.of[Args]
    val func = TypeRepr.of[Func]

    Logger.debug("Trying to build a Function from FunctionArguments", Type.of[Args])
    Logger.debug("... and functionExpr of type", Type.of[Func])

    Option
      .when(!(repr =:= TypeRepr.of[Nothing]) && func.isFunctionType)(func.typeArgs.last)
      .map { retTpe =>
        val args =
          List
            .unfold(Type.of[Args].repr) {
              case Refinement(leftover, name, tpe) => Some(name -> tpe.asType, leftover)
              case other                           => None
            }
            .reverse
            .to(ListMap)

        Logger.loggedDebug("Result"):
          Function(args, retTpe.asType, functionExpr)
      }

  }

  // TODO: This should probably not live here
  transparent inline def encodeAsType[TypeFn[args <: FunctionArguments, retTpe]](
    inline function: Any,
    initial: TypeFn[Nothing, Nothing]
  ) = ${ encodeAsTypeMacro('function, 'initial) }

  private def encodeAsTypeMacro[TypeFn[args <: FunctionArguments, retTpe]: Type](
    function: Expr[Any],
    initial: Expr[TypeFn[Nothing, Nothing]]
  )(using Quotes) = {
    import quotes.reflect.*

    val func = fromExpr(function).getOrElse(report.errorAndAbort(s"Couldn't construct a function TODO ERROR MESSAGE"))
    func.encodeAsType(initial)
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
