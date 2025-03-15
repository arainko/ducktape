package io.github.arainko.ducktape

import scala.annotation.compileTimeOnly

opaque type Case[A, B] <: Case.Fallible[Nothing, A, B] = Case.Fallible[Nothing, A, B]

object Case {
  opaque type Fallible[+F[x], A, B] = Unit

  /**
   * Fills out (or overwrites) a subtype transformation in an enum/sealed trait with a constant value:
   *
   * {{{
   * enum Source {
   *   case One, Two, Three
   * }
   *
   * enum Dest {
   *   case One, Two
   * }
   *
   * val source: Source = Source.Three
   *
   * source
   *   .into[Dest]
   *   .transform(Case.const(_.at[Source.Three.type], Dest.Two))
   * // Dest.Two
   * }}}
   *
   * @param selector path to the configured field
   * @param value the value of the constant
   * @see [[io.github.arainko.ducktape.Selector]] for the path selector DSL
   */
  @compileTimeOnly("Case.const is only useable as a case configuration for transformations")
  def const[A, B, SourceTpe, DestTpe](selector: Selector ?=> A => SourceTpe, value: DestTpe): Case[A, B] = ???

  /**
   * Fills out (or overwrites) a subtype transformation in an enum/sealed trait by computing its value from the source value:
   *
   * {{{
   * enum Source {
   *   case One, Two
   *   case Three(value: Int)
   * }
   *
   * enum Dest {
   *   case One, Two
   * }
   *
   * val source: Source = Source.Three(10)
   *
   * source
   *   .into[Dest]
   *   .transform(Case.computed(_.at[Source.Three], three => if three.value > 5 then Dest.One else Dest.Two))
   * // Dest.One
   * }}}
   *
   * @param selector path to the configured field
   * @param function the function that computes the value
   * @see [[io.github.arainko.ducktape.Selector]] for the path selector DSL
   */
  @compileTimeOnly("Case.computed is only useable as a case configuration for transformations")
  def computed[A, B, SourceTpe, DestTpe](selector: Selector ?=> A => SourceTpe, function: SourceTpe => DestTpe): Case[A, B] = ???

  /**
   * Fills out (or overwrites) a subtype transformation in an enum/sealed trait with a fallible constant value:
   *
   * {{{
   * enum Source {
   *   case One, Two, Three
   * }
   *
   * enum Dest {
   *   case One, Two
   * }
   *
   * val source: Source = Source.Three
   *
   * Mode.FailFast.either[String].locally {
   *  source
   *    .into[Dest]
   *    .fallible
   *    .transform(Case.fallibleConst(_.at[Source.Three.type], Right(Dest.Two)))
   * }
   * // Right(Dest.Two)
   * }}}
   *
   * @param selector path to the configured field
   * @param value the fallible constant value
   * @see [[io.github.arainko.ducktape.Selector]] for the path selector DSL
   */
  @compileTimeOnly("Case.fallibleConst is only useable as a case configuration for transformations")
  def fallibleConst[F[+x], A, B, SourceTpe, DestTpe](
    selector: Selector ?=> A => SourceTpe,
    value: F[DestTpe]
  ): Case.Fallible[F, A, B] = ???

  /**
   * Fills out (or overwrites) a subtype transformation in an enum/sealed trait by computing its fallible value from the source value:
   *
   * {{{
   * enum Source {
   *   case One, Two
   *   case Three(value: Int)
   * }
   *
   * enum Dest {
   *   case One, Two
   * }
   *
   * val source: Source = Source.Three(10)
   *
   * Mode.Either.either[String].locally {
   *  source
   *   .into[Dest]
   *   .fallible
   *   .transform(Case.computed(_.at[Source.Three], three => if three.value > 5 then Right(Dest.One)else Left("too low")))
   * // Right(Dest.One)
   * }
   * }}}
   *
   * @param selector path to the configured field
   * @param function the function that computes the fallible value
   * @see [[io.github.arainko.ducktape.Selector]] for the path selector DSL
   */
  @compileTimeOnly("Case.fallibleConst is only useable as a case configuration for transformations")
  def fallibleComputed[F[+x], A, B, SourceTpe, DestTpe](
    selector: Selector ?=> A => SourceTpe,
    function: SourceTpe => F[DestTpe]
  ): Case.Fallible[F, A, B] = ???

   /**
   * Transforms subtype names names of the source's side (on all nesting levels).
   * 
   * To constrain the blast radius of this config you can apply one of the 3 modifiers:
   *  * `.regional` to constrain the region of this config option (see [[io.github.arainko.ducktape.Regional]])
   *  * `.local` to constrain the config option to a single transformation level or an enum child/family (see [[io.github.arainko.ducktape.Local]])
   *  * `.typeSpecific` to constrain the config option to subtypes of a type (see [[io.github.arainko.ducktape.TypeSpecific]])
   * 
   * {{{
   * 
   * enum Source {
   *   case one, two, three
   * }
   * 
   * enum Dest {
   *   case ONE, TWO, THREE
   * }
   *
   * val source = Source.one
   *
   * source.into[Dest].transform(Case.modifySourceNames(_.toUpperCase))
   * // Dest.ONE
   * }}}
   * @param renamer the transformation function (needs to be known at compile time)
   * @see [[io.github.arainko.ducktape.Renamer]]
   */
  @compileTimeOnly("Case.modifySourceNames is only useable as a case configuration for transformations")
  def modifySourceNames[A, B](renamer: Renamer => Renamer): Case[A, B] & Regional[A] & Local[A] & TypeSpecific = ???

  /**
   * Transforms subtype names names of the destination's side (on all nesting levels).
   * 
   * To constrain the blast radius of this config you can apply one of the 3 modifiers:
   *  * `.regional` to constrain the region of this config option (see [[io.github.arainko.ducktape.Regional]])
   *  * `.local` to constrain the config option to a single transformation level or an enum child/family (see [[io.github.arainko.ducktape.Local]])
   *  * `.typeSpecific` to constrain the config option to subtypes of a type (see [[io.github.arainko.ducktape.TypeSpecific]])
   * 
   * {{{
   * 
   * enum Source {
   *   case one, two, three
   * }
   * 
   * enum Dest {
   *   case ONE, TWO, THREE
   * }
   *
   * val source = Source.one
   *
   * source.into[Dest].transform(Case.modifyDestNames(_.toLowerCase))
   * // Dest.ONE
   * }}}
   * @param renamer the transformation function (needs to be known at compile time)
   * @see [[io.github.arainko.ducktape.Renamer]]
   */
  @compileTimeOnly("Case.modifyDestNames is only useable as a case configuration for transformations")
  def modifyDestNames[A, B](renamer: Renamer => Renamer): Case[A, B] & Regional[B] & Local[B] & TypeSpecific = ???

  @deprecated(
    message = "Use the variant that accepts a path selector instead (Case.const(_.at[SourceSubtype], ...))",
    since = "ducktape 0.2.0-M1"
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
    since = "ducktape 0.2.0-M1"
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
    since = "ducktape 0.2.0-M3"
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
    since = "ducktape 0.2.0-M3"
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
