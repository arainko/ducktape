package io.github.arainko.ducktape.macros

import scala.quoted.*

object AstInstanceCounter {

  /**
   * Counts how many times a Term with a given type appears in the AST.
   * Note that a single instance can be counted multiple times due to how the tree is traversed, hence 'roughly' in the name.
   *
   * The way to really use it is measure how many instances you get before doing something with the AST and inspecting it and then
   * running it after inspecting manually to have a rough ballpark of what this should be.
   *
   * If a test fails because the number of instances is bigger than expected it is an alert for the maintainer to inspect the AST and
   * make a call - something akin to a fire alarm, right.
   */
  inline def roughlyCount[A](inline value: Any): Int = ${ impl[A]('value) }

  def impl[A: Type](expr: Expr[Any])(using Quotes) = {
    import quotes.reflect.*
    val tpe = TypeRepr.of[A]

    val acc = new TreeAccumulator[Int] {
      override def foldTree(x: Int, tree: Tree)(owner: Symbol): Int =
        tree match {
          case term: Term if term.tpe <:< tpe =>
            foldOverTree(x + 1, term)(owner)
          case other =>
            foldOverTree(x, other)(owner)
        }
    }

    Expr(acc.foldOverTree(0, expr.asTerm)(Symbol.spliceOwner))
  }
}
