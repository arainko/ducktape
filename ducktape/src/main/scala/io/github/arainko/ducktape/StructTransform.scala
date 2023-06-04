package io.github.arainko.ducktape

import scala.quoted.*

object StructTransform {
  import Structure.*

  def createTransformation[Source: Type, Dest: Type](source: Expr[Source])(using Quotes) = ???

  def recurse[A, B](source: Structure, dest: Structure, value: Expr[A])(using Quotes): Expr[B] = {
    import quotes.reflect.*
    (source.force -> dest.force) match {
      case (source: Product, dest: Product) => ???
      case (source: Coproduct, dest: Coproduct) => ???
      case (source: Singleton, dest: Singleton) => ???
      case (source: Ordinary, dest: Ordinary) => ???
    }
  }
}
