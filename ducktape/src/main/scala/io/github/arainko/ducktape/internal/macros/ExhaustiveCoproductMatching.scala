package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.internal.modules.{ Case, Cases }

import scala.quoted.*

private[ducktape] object ExhaustiveCoproductMatching {

  /**
   * This utility constructs an if-then-else for the given coproduct.
   *
   * For a given example coproduct
   *
   * enum Example:
   *   case One, Two
   *
   * it will generate code like this:
   *
   * if(sourceValue.isInstanceOf[One])
   *    f(One)
   * else if(sourceValue.isInstanceOf[Two])
   *    f(Two)
   * else
   *    ifNoMatch
   */

  def apply[Src: Type, Dest: Type](cases: Cases, sourceValue: Expr[Src], ifNoMatch: Expr[Dest])(f: Case => Expr[Dest])(using
    Quotes
  ): Expr[Dest] =
    ifStatement(
      cases.value.map { c =>
        val cond = IsInstanceOf(sourceValue, c.tpe)
        val branchCode = f(c)
        IfBranch(cond, branchCode)
      },
      ifNoMatch
    ).asExprOf[Dest]

  private def ifStatement(using Quotes)(branches: List[IfBranch], ifNoMatch: Expr[_]): quotes.reflect.Term = {
    import quotes.reflect.*

    branches
      .foldRight(ifNoMatch.asTerm) { (nextBranch, soFar) =>
        If(nextBranch.cond.asTerm, nextBranch.value.asTerm, soFar)
      }
  }

  private def IsInstanceOf(value: Expr[Any], tpe: Type[?])(using Quotes): Expr[Boolean] =
    tpe match {
      case '[tpe] => '{ $value.isInstanceOf[tpe] }
    }

  private case class IfBranch(cond: Expr[Boolean], value: Expr[Any])
}
