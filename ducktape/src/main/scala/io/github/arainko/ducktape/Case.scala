package io.github.arainko.ducktape

import scala.annotation.compileTimeOnly

opaque type Case[A, B] <: Case.Fallible[Nothing, A, B] = Case.Fallible[Nothing, A, B]

object Case {
  opaque type Fallible[+F[x], A, B] = Unit

  @compileTimeOnly("Case.fallibleConst is only useable as a case configuration for transformations")
  def fallibleConst[F[+x], A, B, SourceTpe, DestTpe](
    selector: Selector ?=> A => SourceTpe,
    value: F[DestTpe]
  ): Case.Fallible[F, A, B] = ???

  @compileTimeOnly("Case.fallibleConst is only useable as a case configuration for transformations")
  def fallibleComputed[F[+x], A, B, SourceTpe, DestTpe](
    selector: Selector ?=> A => SourceTpe,
    function: SourceTpe => F[DestTpe]
  ): Case.Fallible[F, A, B] = ???

  @compileTimeOnly("Case.const is only useable as a case configuration for transformations")
  def const[A, B, SourceTpe, DestTpe](selector: Selector ?=> A => SourceTpe, value: DestTpe): Case[A, B] = ???

  @compileTimeOnly("Case.computed is only useable as a case configuration for transformations")
  def computed[A, B, SourceTpe, DestTpe](selector: Selector ?=> A => SourceTpe, function: SourceTpe => DestTpe): Case[A, B] = ???

  @deprecated(
    message = "Use the variant that accepts a path selector instead (Case.const(_.at[SourceSubtype], ...))",
    since = "0.2.0-M1"
  )
  @compileTimeOnly("'Case.const' needs to be erased from the AST with a macro.")
  def const[SourceSubtype]: Case.Const[SourceSubtype] = ???

  opaque type Const[SourceSubtype] = Unit

  object Const {
    extension [SourceSubtype](inst: Const[SourceSubtype]) {

      @compileTimeOnly("'Case.const' needs to be erased from the AST with a macro.")
      def apply[Source >: SourceSubtype, Dest](const: Dest): Case[Source, Dest] = ???
    }
  }

  @deprecated(
    message = "Use the variant that accepts a path selector instead (Case.computed(_.at[SourceSubtype], ...))",
    since = "0.2.0-M1"
  )
  @compileTimeOnly("Case.computed is only useable as a case configuration for transformations")
  def computed[SourceSubtype]: Case.Computed[SourceSubtype] = ???

  opaque type Computed[SourceSubtype] = Unit

  object Computed {
    extension [SourceSubtype](inst: Computed[SourceSubtype]) {

      @compileTimeOnly("Case.computed is only useable as a case configuration for transformations")
      def apply[Source >: SourceSubtype, Dest](f: SourceSubtype => Dest): Case[Source, Dest] = ???
    }
  }

  @deprecated(
    message = "Use the variant that accepts a path selector instead (Case.fallibleConst(_.at[SourceSubtype], ...))",
    since = "0.2.0-M3"
  )
  @compileTimeOnly("Case.fallibleConst is only useable as a case configuration for transformations")
  def fallibleConst[SourceSubtype]: Case.FallibleConst[SourceSubtype] = ???

  opaque type FallibleConst[SourceSubtype] = Unit

  object FallibleConst {
    extension [SourceSubtype](inst: FallibleConst[SourceSubtype]) {

      @compileTimeOnly("'Case.fallibleConst' needs to be erased from the AST with a macro.")
      def apply[F[+x], Source, Dest](const: F[Dest]): Case.Fallible[F, Source, Dest] = ???
    }
  }

  @deprecated(
    message = "Use the variant that accepts a path selector instead (Case.fallibleComputed(_.at[SourceSubtype], ...))",
    since = "0.2.0-M3"
  )
  @compileTimeOnly("Case.fallibleComputed is only useable as a case configuration for transformations")
  def fallibleComputed[SourceSubtype]: Case.FallibleComputed[SourceSubtype] = ???

  opaque type FallibleComputed[SourceSubtype] = Unit

  object FallibleComputed {
    extension [SourceSubtype](inst: FallibleComputed[SourceSubtype]) {

      @compileTimeOnly("Case.fallibleComputed is only useable as a case configuration for transformations")
      def apply[F[+x], Source, Dest](const: SourceSubtype => F[Dest]): Case.Fallible[F, Source, Dest] = ???
    }
  }
}
