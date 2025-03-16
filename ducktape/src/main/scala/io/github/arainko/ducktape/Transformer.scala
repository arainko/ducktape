package io.github.arainko.ducktape

import io.github.arainko.ducktape
import io.github.arainko.ducktape.Transformer.Derived.FromFunction
import io.github.arainko.ducktape.internal.{ FallibleTransformations, TotalTransformations }

/**
  * A `Transformer[Source, Dest]` describes a total transformation from `Source` to `Dest`.
  * 
  * `ducktape` uses `Transformers` as a user-controlled extension point of the library for transforming 
  * types that are not handled out of the box (or if the user wants to override the library-defined transformation).
  * 
  * The recommended way of defining a `Transformer` is a SAM lambda:
  * {{{
  * given Transformer[Int, String] = _.toString
  * }}}
  */
@FunctionalInterface
trait Transformer[Source, Dest] extends Transformer.Derived[Source, Dest]

object Transformer {
  inline given derive[Source, Dest]: Transformer.Derived[Source, Dest] =
    Derived.FromFunction(value => TotalTransformations.between[Source, Dest](value, "definition"))

  def define[Source, Dest]: DefinitionBuilder[Source, Dest] =
    DefinitionBuilder[Source, Dest]

  def defineVia[Source]: DefinitionViaBuilder.PartiallyApplied[Source] =
    DefinitionViaBuilder.create[Source]

  /**
    * `Transformer.Derived[Source, Dest]` is a last resort mechanism for deriving transformations for types that are not known
    * at definition site of the transformation, for example:
    * {{{
    * final case class Source[A](field1: Int, field2: String, generic: A)
    * final case class Dest[A](field1: Int, field2: String, generic: A)
    *
    * def transformSource[A, B](source: Source[A])(using Transformer.Derived[A, B]): Dest[B] = 
    *   source.to[Dest[B]]
    *
    * transformSource[Int, Option[Int]](Source(1, "2", 3))
    * // Dest(1, "2", Some(3))
    * }}}  
    * 
    * Instances of `Transformer.Derived[Source, Dest]` are automatically derived but the derivation only kicks in as a last resort when requested by the user (like in the above example).
    * 
    * Users should NOT provide their own `Transformer.Derived[Source, Dest]` instances - `Transformer[Source, Dest]` is fit for exactly that purpose.
    */
  sealed trait Derived[Source, Dest] {
    def transform(value: Source): Dest
  }

  object Derived {
    final class FromFunction[Source, Dest](f: Source => Dest) extends Transformer[Source, Dest] {
      def transform(value: Source): Dest = f(value)
    }
  }

  /**
  * A `Transformer.Fallible[F, Source, Dest]` describes a fallible transformation from `Source` to `F[Dest]`.
  * 
  * `ducktape` uses `Transformer.Fallible` as a user-controlled extension point of the library for transforming 
  * types that are not handled out of the box in fallible transformations (or if the user wants to override the library-defined transformation).
  * 
  * The recommended way of defining a `Transformer.Fallible` is a SAM lambda:
  * {{{
  * given Transformer.Fallible[[a] =>> Either[String, a], Int, String] = 
  *   int => if int > 10 then Right(int.toString) else Left("lesser than 10 :(")
  * }}}
  */
  @FunctionalInterface
  trait Fallible[F[+x], Source, Dest] extends Fallible.Derived[F, Source, Dest]

  object Fallible {
    /**
    * `Transformer.Fallible.Derived[F, Source, Dest]` is a last resort mechanism for deriving transformations for types that are not known
    * at definition site of the transformation, for example:
    * {{{
    * final case class Source[A](field1: Int, field2: String, generic: A)
    * final case class Dest[A](field1: Int, field2: String, generic: A)
    *
    * def transformSource[F[+x], A, B](source: Source[A])(using Mode[F], Transformer.Derived[F, A, B]): F[Dest[B]] = 
    *   source.fallibleTo[Dest[B]]
    *
    * transformSource[[a] =>> Either[String, a], Int, Option[Int]](Source(1, "2", 3))
    * // Right(Dest(1, "2", Some(3)))
    * }}}  
    * 
    * Instances of `Transformer.Fallible.Derived[Source, Dest]` are automatically derived but the derivation only kicks in as a last resort when requested by the user (like in the above example).
    * 
    * Users should NOT provide their own `Transformer.Fallible.Derived[F, Source, Dest]` instances - `Transformer.Fallible[F, Source, Dest]` is fit for exactly that purpose.
    */
    sealed trait Derived[F[+x], Source, Dest] {
      def transform(source: Source): F[Dest]
    }

    object Derived {
      final class FromFunction[F[+x], Source, Dest](f: Source => F[Dest]) extends Transformer.Fallible[F, Source, Dest] {
        def transform(source: Source): F[Dest] = f(source)
      }
    }

    inline given derive[F[+x], Source, Dest](using F: ducktape.Mode[F]): Transformer.Fallible.Derived[F, Source, Dest] =
      Derived.FromFunction(source => FallibleTransformations.between[F, Source, Dest](source, F, "definition"))
  }

  object Debug {
    inline def showCode[A](inline value: A): A = internal.CodePrinter.code(value)
  }

  @deprecated(message = "Use io.github.arainko.ducktape.Mode instead", since = "ducktape 0.2.0-M3")
  type Mode[F[+x]] = io.github.arainko.ducktape.Mode[F]

  @deprecated(message = "Use io.github.arainko.ducktape.Mode instead", since = "ducktape 0.2.0-M3")
  val Mode = io.github.arainko.ducktape.Mode

}
