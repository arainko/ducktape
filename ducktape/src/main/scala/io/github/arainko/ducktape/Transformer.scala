package io.github.arainko.ducktape

import io.github.arainko.ducktape.builder.*
import io.github.arainko.ducktape.internal.macros.*

import scala.collection.{ BuildFrom, Factory }
import scala.compiletime.*
import scala.deriving.Mirror

@FunctionalInterface
trait Transformer[Source, Dest] {
  def transform(from: Source): Dest
}

object Transformer {
  def apply[Source, Dest](using transformer: Transformer[Source, Dest]): Transformer[Source, Dest] = transformer

  def define[Source, Dest]: DefinitionBuilder[Source, Dest] = DefinitionBuilder[Source, Dest]

  def defineVia[Source]: DefinitionViaBuilder.PartiallyApplied[Source] = DefinitionViaBuilder.create[Source]

  final class Identity[Source, Dest >: Source] private[Transformer] extends Transformer[Source, Dest] {
    def transform(from: Source): Dest = from
  }

  sealed trait ForProduct[Source, Dest] extends Transformer[Source, Dest]

  object ForProduct {
    @deprecated(message = "Use the variant with a Transformer instead", since = "0.1.1")
    private[ducktape] def make[Source, Dest](f: Source => Dest): ForProduct[Source, Dest] =
      new {
        def transform(from: Source): Dest = f(from)
      }

    private[ducktape] def make[Source, Dest](transfomer: Transformer[Source, Dest]): ForProduct[Source, Dest] =
      new {
        def transform(from: Source): Dest = transfomer.transform(from)
      }
  }

  sealed trait ForCoproduct[Source, Dest] extends Transformer[Source, Dest]

  object ForCoproduct {
    @deprecated(message = "Use the variant with a Transformer instead", since = "0.1.1")
    private[ducktape] def make[Source, Dest](f: Source => Dest): ForCoproduct[Source, Dest] =
      new {
        def transform(from: Source): Dest = f(from)
      }

    private[ducktape] def make[Source, Dest](transformer: Transformer[Source, Dest]): ForCoproduct[Source, Dest] =
      new {
        def transform(from: Source): Dest = transformer.transform(from)
      }
  }

  sealed trait FromAnyVal[Source <: AnyVal, Dest] extends Transformer[Source, Dest]

  object FromAnyVal {
    @deprecated(message = "Use the variant with a Transformer instead", since = "0.1.1")
    private[ducktape] def make[Source <: AnyVal, Dest](f: Source => Dest): FromAnyVal[Source, Dest] =
      new {
        def transform(from: Source): Dest = f(from)
      }

    private[ducktape] def make[Source <: AnyVal, Dest](transformer: Transformer[Source, Dest]): FromAnyVal[Source, Dest] =
      new {
        def transform(from: Source): Dest = transformer.transform(from)
      }
  }

  sealed trait ToAnyVal[Source, Dest <: AnyVal] extends Transformer[Source, Dest]

  object ToAnyVal {
    @deprecated(message = "Use the variant with a Transformer instead", since = "0.1.1")
    private[ducktape] def make[Source, Dest <: AnyVal](f: Source => Dest): ToAnyVal[Source, Dest] =
      new {
        def transform(from: Source): Dest = f(from)
      }

    private[ducktape] def make[Source, Dest <: AnyVal](transformer: Transformer[Source, Dest]): ToAnyVal[Source, Dest] =
      new {
        def transform(from: Source): Dest = transformer.transform(from)
      }
  }

  given [Source, Dest >: Source]: Identity[Source, Dest] = Identity[Source, Dest]

  inline given forProducts[Source, Dest](using Mirror.ProductOf[Source], Mirror.ProductOf[Dest]): ForProduct[Source, Dest] =
    ForProduct.make(DerivationMacros.deriveProductTransformer[Source, Dest])

  inline given forCoproducts[Source, Dest](using Mirror.SumOf[Source], Mirror.SumOf[Dest]): ForCoproduct[Source, Dest] =
    ForCoproduct.make(DerivationMacros.deriveCoproductTransformer[Source, Dest])

  given [Source, Dest](using Transformer[Source, Dest]): Transformer[Source, Option[Dest]] =
    from => Transformer[Source, Dest].transform.andThen(Some.apply)(from)

  given [Source, Dest](using Transformer[Source, Dest]): Transformer[Option[Source], Option[Dest]] =
    from => from.map(Transformer[Source, Dest].transform)

  given [A1, A2, B1, B2](using Transformer[A1, A2], Transformer[B1, B2]): Transformer[Either[A1, B1], Either[A2, B2]] = {
    case Right(value) => Right(Transformer[B1, B2].transform(value))
    case Left(value)  => Left(Transformer[A1, A2].transform(value))
  }

  given [Source, Dest, SourceCollection[elem] <: Iterable[elem], DestCollection[elem] <: Iterable[elem]](using
    trans: Transformer[Source, Dest],
    factory: Factory[Dest, DestCollection[Dest]]
  ): Transformer[SourceCollection[Source], DestCollection[Dest]] = from => from.map(trans.transform).to(factory)

  inline given fromAnyVal[Source <: AnyVal, Dest]: FromAnyVal[Source, Dest] =
    FromAnyVal.make(DerivationMacros.deriveFromAnyValTransformer[Source, Dest])

  inline given toAnyVal[Source, Dest <: AnyVal]: ToAnyVal[Source, Dest] =
    ToAnyVal.make(DerivationMacros.deriveToAnyValTransformer[Source, Dest])

}
