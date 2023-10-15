package io.github.arainko.ducktape

import scala.quoted.*

trait Transformer[Source, Dest] extends Transformer.Derived[Source, Dest]

object Transformer {
  // def derivedTransformer[A: Type, B: Type](using Quotes): Expr[Transformer2[A, B]] =
  //   '{ src => ${ Interpreter.createTransformation[A, B]('src) }  }

  // inline given derived[Source, Dest]: Transformer2[Source, Dest] = ${ derivedTransformer[Source, Dest] }

  def define[Source, Dest] = ???

  def defineVia[Source] = ???

  trait Derived[Source, Dest] {
    def transform(value: Source): Dest
  }
}
