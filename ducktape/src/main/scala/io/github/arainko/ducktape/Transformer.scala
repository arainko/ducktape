package io.github.arainko.ducktape

import io.github.arainko.ducktape.builder.*
import io.github.arainko.ducktape.internal.macros.*

import scala.collection.BuildFrom
import scala.collection.Factory
import scala.compiletime.*
import scala.deriving.Mirror

@FunctionalInterface
trait Transformer[Source, Dest] {
  def transform(from: Source): Dest
}

object Transformer {
  def apply[Source, Dest](using transformer: Transformer[Source, Dest]): Transformer[Source, Dest] = transformer

  def define[Source, Dest]: DefinitionBuilder[Source, Dest] = DefinitionBuilder[Source, Dest]

  def defineVia[A]: DefinitionViaBuilder.PartiallyApplied[A] = DefinitionViaBuilder.create[A]

  sealed trait Identity[Source] extends Transformer[Source, Source]

  given [Source]: Identity[Source] = new {
    def transform(from: Source): Source = from
  }

  inline given forProducts[Source, Dest](using Mirror.ProductOf[Source], Mirror.ProductOf[Dest]): Transformer[Source, Dest] =
    from => ProductTransformerMacros.transform(from)

  inline given forCoproducts[Source, Dest](using Mirror.SumOf[Source], Mirror.SumOf[Dest]): Transformer[Source, Dest] =
    from => CoproductTransformerMacros.transform(from)

  given [Source, Dest](using Transformer[Source, Dest]): Transformer[Source, Option[Dest]] =
    from => Transformer[Source, Dest].transform.andThen(Some.apply)(from)

  given [Source, Dest](using Transformer[Source, Dest]): Transformer[Option[Source], Option[Dest]] =
    from => from.map(Transformer[Source, Dest].transform)

  given [A1, A2, B1, B2](using Transformer[A1, A2], Transformer[B1, B2]): Transformer[Either[A1, B1], Either[A2, B2]] = {
    case Right(value) => Right(Transformer[B1, B2].transform(value))
    case Left(value)  => Left(Transformer[A1, A2].transform(value))
  }

  given [Source, Dest, SourceCollection[+elem] <: Iterable[elem], DestCollection[+elem] <: Iterable[elem]](using
    trans: Transformer[Source, Dest],
    factory: Factory[Dest, DestCollection[Dest]]
  ): Transformer[SourceCollection[Source], DestCollection[Dest]] = from => from.map(trans.transform).to(factory)

  inline given fromAnyVal[Source <: AnyVal, Dest]: Transformer[Source, Dest] =
    from => ProductTransformerMacros.transformFromAnyVal[Source, Dest](from)

  inline given toAnyVal[Source, Dest <: AnyVal]: Transformer[Source, Dest] =
    from => ProductTransformerMacros.transfromToAnyVal[Source, Dest](from)

}
