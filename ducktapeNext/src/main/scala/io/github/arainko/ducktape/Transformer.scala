package io.github.arainko.ducktape

import scala.quoted.*

trait Transformer[Source, Dest] {
  def transform(value: Source): Dest
}

object Transformer {
  def derivedTransformer[A: Type, B: Type](using Quotes): Expr[Transformer[A, B]] =
    '{ src => ${ Interpreter.createTransformation[A, B]('src) }  }

  inline given derived[Source, Dest]: Transformer[Source, Dest] = ${ derivedTransformer[Source, Dest] }

  trait Custom[Source, Dest] extends Transformer[Source, Dest]
}
