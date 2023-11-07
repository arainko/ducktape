package io.github.arainko.ducktape

import scala.quoted.*
import io.github.arainko.ducktape.internal.Transformations
import io.github.arainko.ducktape.DefinitionViaBuilder.PartiallyApplied

trait Transformer[Source, Dest] extends Transformer.Derived[Source, Dest]

object Transformer {
  inline given derive[Source, Dest]: Transformer.Derived[Source, Dest] = new {
    def transform(value: Source): Dest = Transformations.between[Source, Dest](value)
  } 

  def define[Source, Dest]: DefinitionBuilder[Source, Dest] = 
    DefinitionBuilder[Source, Dest]

  def defineVia[Source]: DefinitionViaBuilder.PartiallyApplied[Source] = 
    DefinitionViaBuilder.create[Source]

  sealed trait Derived[Source, Dest] {
    def transform(value: Source): Dest
  }
}

object Test extends App {
  // given t: Transformer[Int, Int] = ???

  internal.CodePrinter.code:
    summon[Transformer.Derived[Int, Int]]

  // Transformer.derive[Int, Int].transform(1)
}
