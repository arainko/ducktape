package io.github.arainko.ducktape

import scala.annotation.compileTimeOnly

opaque type Case[A, B] = Unit

object Case {
  @compileTimeOnly("Case.const is only useable as a case configuration for transformations")
  def const[A, B, SourceTpe, DestTpe](selector: Selector ?=> A => SourceTpe, value: DestTpe): Case[A, B] = ???

  @compileTimeOnly("Case.computed is only useable as a case configuration for transformations")
  def computed[A, B, SourceTpe, DestTpe](selector: Selector ?=> A => SourceTpe, function: SourceTpe => DestTpe): Case[A, B] = ???

  @deprecated
  @compileTimeOnly("'Case.const' needs to be erased from the AST with a macro.")
  def const[SourceSubtype]: Case.Const[SourceSubtype] = ???

  opaque type Const[SourceSubtype] = Unit

  object Const {
    extension [SourceSubtype](inst: Const[SourceSubtype]) {

      @compileTimeOnly("'Case.const' needs to be erased from the AST with a macro.")
      def apply[Source >: SourceSubtype, Dest](const: Dest): Case[Source, Dest] = ???
    }
  }

  @deprecated
  @compileTimeOnly("'Case.computed' needs to be erased from the AST with a macro.")
  def computed[SourceSubtype]: Case.Computed[SourceSubtype] = ???

  opaque type Computed[SourceSubtype] = Unit

  object Computed {
    extension [SourceSubtype](inst: Computed[SourceSubtype]) {

      @compileTimeOnly("'Case.computed' needs to be erased from the AST with a macro.")
      def apply[Source >: SourceSubtype, Dest](f: SourceSubtype => Dest): Case[Source, Dest] = ???
    }
  }
}
