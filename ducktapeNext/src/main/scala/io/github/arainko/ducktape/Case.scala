package io.github.arainko.ducktape

import scala.annotation.compileTimeOnly

opaque type Case[A, B] = Unit

object Case {
  @compileTimeOnly("Case.const is only useable as a case configuration for transformations")
  def const[A, B, SourceTpe, DestTpe](selector: Selector ?=> A => SourceTpe, value: DestTpe): Case[A, B] = ???

  @compileTimeOnly("Case.computed is only useable as a case configuration for transformations")
  def computed[A, B, SourceTpe, DestTpe](selector: Selector ?=> A => SourceTpe, function: SourceTpe => DestTpe): Case[A, B] = ???
}
