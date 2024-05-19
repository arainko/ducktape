package io.github.arainko.ducktape

import io.github.arainko.ducktape
import io.github.arainko.ducktape.DefinitionViaBuilder.PartiallyApplied
import io.github.arainko.ducktape.Transformer.Derived.FromFunction
import io.github.arainko.ducktape.internal.{ FallibleTransformations, TotalTransformations }

@FunctionalInterface
trait Transformer[Source, Dest] extends Transformer.Derived[Source, Dest]

object Transformer {
  inline given derive[Source, Dest]: Transformer.Derived[Source, Dest] =
    Derived.FromFunction(value => TotalTransformations.between[Source, Dest](value, "definition"))

  def define[Source, Dest]: DefinitionBuilder[Source, Dest] =
    DefinitionBuilder[Source, Dest]

  def defineVia[Source]: DefinitionViaBuilder.PartiallyApplied[Source] =
    DefinitionViaBuilder.create[Source]

  sealed trait Derived[Source, Dest] {
    def transform(value: Source): Dest
  }

  object Derived {
    final class FromFunction[Source, Dest](f: Source => Dest) extends Transformer[Source, Dest] {
      def transform(value: Source): Dest = f(value)
    }
  }

  @FunctionalInterface
  trait Fallible[F[+x], Source, Dest] extends Fallible.Derived[F, Source, Dest]

  object Fallible {
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
