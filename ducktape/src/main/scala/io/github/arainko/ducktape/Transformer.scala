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

  object Debug {
    inline def showCode[A](inline value: A): A = DebugMacros.code(value)
  }

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

  @deprecated(message = "Use 'Transformer.identity' instead", since = "0.1.5")
  final def given_Identity_Source_Dest[Source, Dest >: Source]: Identity[Source, Dest] = identity[Source, Dest]

  given identity[Source, Dest >: Source]: Identity[Source, Dest] = Identity[Source, Dest]

  @deprecated(message = "Use 'Transformer.betweenProducts' instead", since = "0.1.5")
  inline def forProducts[Source, Dest](using Mirror.ProductOf[Source], Mirror.ProductOf[Dest]): ForProduct[Source, Dest] =
    ForProduct.make(DerivedTransformers.product[Source, Dest])

  @deprecated(message = "Use 'Transformer.betweenCoproducts' instead", since = "0.1.5")
  inline def forCoproducts[Source, Dest](using Mirror.SumOf[Source], Mirror.SumOf[Dest]): ForCoproduct[Source, Dest] =
    ForCoproduct.make(DerivedTransformers.coproduct[Source, Dest])

  inline given betweenProducts[Source, Dest](using
    Mirror.ProductOf[Source],
    Mirror.ProductOf[Dest]
  ): Transformer.ForProduct[Source, Dest] =
    Transformer.ForProduct.make(DerivedTransformers.product[Source, Dest])

  inline given betweenCoproducts[Source, Dest](using
    Mirror.SumOf[Source],
    Mirror.SumOf[Dest]
  ): Transformer.ForCoproduct[Source, Dest] =
    Transformer.ForCoproduct.make(DerivedTransformers.coproduct[Source, Dest])

  @deprecated(message = "Use 'Transformer.betweenNonOptionOption' instead", since = "0.1.5")
  final def given_Transformer_Source_Option[Source, Dest](using Transformer[Source, Dest]): Transformer[Source, Option[Dest]] =
    betweenNonOptionOption[Source, Dest]

  given betweenNonOptionOption[Source, Dest](using Transformer[Source, Dest]): Transformer[Source, Option[Dest]] =
    from => Transformer[Source, Dest].transform.andThen(Some.apply)(from)

  @deprecated(message = "Use 'Transformer.betweenOptions' instead", since = "0.1.5")
  final def given_Transformer_Option_Option[Source, Dest](using
    Transformer[Source, Dest]
  ): Transformer[Option[Source], Option[Dest]] =
    from => from.map(Transformer[Source, Dest].transform)

  given betweenOptions[Source, Dest](using
    Transformer[Source, Dest]
  ): Transformer[Option[Source], Option[Dest]] =
    from => from.map(Transformer[Source, Dest].transform)

  @deprecated(message = "Use 'Transformer.betweenEithers' instead", since = "0.1.5")
  final def given_Transformer_Either_Either[A1, A2, B1, B2](using
    Transformer[A1, A2],
    Transformer[B1, B2]
  ): Transformer[Either[A1, B1], Either[A2, B2]] = {
    case Right(value) => Right(Transformer[B1, B2].transform(value))
    case Left(value)  => Left(Transformer[A1, A2].transform(value))
  }

  given betweenEithers[A1, A2, B1, B2](using
    Transformer[A1, A2],
    Transformer[B1, B2]
  ): Transformer[Either[A1, B1], Either[A2, B2]] = {
    case Right(value) => Right(Transformer[B1, B2].transform(value))
    case Left(value)  => Left(Transformer[A1, A2].transform(value))
  }

  @deprecated(message = "Use 'Transformer.betweenCollections' instead", since = "0.1.5")
  final def given_Transformer_SourceCollection_DestCollection[
    Source,
    Dest,
    SourceCollection[elem] <: Iterable[elem],
    DestCollection[elem] <: Iterable[elem]
  ](using
    trans: Transformer[Source, Dest],
    factory: Factory[Dest, DestCollection[Dest]]
  ): Transformer[SourceCollection[Source], DestCollection[Dest]] =
    betweenCollections[Source, Dest, SourceCollection, DestCollection]

  given betweenCollections[Source, Dest, SourceCollection[elem] <: Iterable[elem], DestCollection[elem] <: Iterable[elem]](using
    trans: Transformer[Source, Dest],
    factory: Factory[Dest, DestCollection[Dest]]
  ): Transformer[SourceCollection[Source], DestCollection[Dest]] = from => from.map(trans.transform).to(factory)

  @deprecated
  inline def fromAnyVal[Source <: AnyVal, Dest]: FromAnyVal[Source, Dest] =
    FromAnyVal.make(DerivedTransformers.fromAnyVal[Source, Dest])

  @deprecated
  inline def toAnyVal[Source, Dest <: AnyVal]: ToAnyVal[Source, Dest] =
    ToAnyVal.make(DerivedTransformers.toAnyVal[Source, Dest])

  inline given costamToAnyVal[Source, Dest](using
    Dest <:< AnyVal,
    Dest <:< Product
  ): Transformer.ToAnyVal[Source, Dest] =
    Transformer.ToAnyVal.make(DerivedTransformers.toAnyVal[Source, Dest])

  inline given costamFromAnyVal[Source <: AnyVal, Dest](using
    Source <:< AnyVal,
    Source <:< Product
  ): Transformer.FromAnyVal[Source, Dest] =
    Transformer.FromAnyVal.make(DerivedTransformers.fromAnyVal[Source, Dest])
}