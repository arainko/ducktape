package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.builder.applied.Builder
import io.github.arainko.ducktape.internal.*

import scala.compiletime.*
import scala.compiletime.ops.int.*
import scala.deriving.Mirror

final case class TransformerBuilder[
  From,
  To,
  FromSubcases <: Tuple,
  ToSubcases <: Tuple,
  DerivedFromSubcases <: Tuple,
  DerivedToSubcases <: Tuple
] private (
  override private[builder] val computeds: Map[FieldName, From => Any],
  override private[builder] val constants: Map[FieldName, Any],
  override private[builder] val renameTransformers: Map[FieldName, RenamedField],
  override private[builder] val coprodInstances: Map[Ordinal, From => To]
) extends Builder[TransformerBuilder, From, To, FromSubcases, ToSubcases, DerivedFromSubcases, DerivedToSubcases]:

  private[builder] def construct[
    DerivedFromSubcases <: Tuple,
    DerivedToSubcases <: Tuple
  ](
    computeds: Map[FieldName, From => Any],
    constants: Map[FieldName, Any],
    renameTransformers: Map[FieldName, RenamedField],
    coprodInstances: Map[Ordinal, From => To]
  ): TransformerBuilder[From, To, FromSubcases, ToSubcases, DerivedFromSubcases, DerivedToSubcases] =
    this.copy(
      computeds = computeds,
      constants = constants,
      renameTransformers = renameTransformers,
      coprodInstances = coprodInstances
    )

end TransformerBuilder

object TransformerBuilder:

  transparent inline def create[From, To] =
    summonFrom {
      case from: Mirror.ProductOf[From] => createFromProduct[From, To](from)
      case from: Mirror.SumOf[From]     => createFromSum[From, To](from)
    }

  private transparent inline def createFromProduct[From, To](from: Mirror.ProductOf[From]) =
    summonFrom {
      case to: Mirror.ProductOf[To] =>
        TransformerBuilder[
          From,
          To,
          Field.FromLabelsAndTypes[from.MirroredElemLabels, from.MirroredElemTypes],
          Field.FromLabelsAndTypes[to.MirroredElemLabels, to.MirroredElemTypes],
          Field.FromLabelsAndTypes[from.MirroredElemLabels, from.MirroredElemTypes],
          Field.FromLabelsAndTypes[to.MirroredElemLabels, to.MirroredElemTypes],
        ](Map.empty, Map.empty, Map.empty, Map.empty)
    }

  private transparent inline def createFromSum[From, To](from: Mirror.SumOf[From]) =
    summonFrom {
      case to: Mirror.SumOf[To] =>
        TransformerBuilder[
          From,
          To,
          Case.FromLabelsAndTypes[from.MirroredElemLabels, from.MirroredElemTypes],
          Case.FromLabelsAndTypes[to.MirroredElemLabels, to.MirroredElemTypes],
          Case.FromLabelsAndTypes[from.MirroredElemLabels, from.MirroredElemTypes],
          Case.FromLabelsAndTypes[to.MirroredElemLabels, to.MirroredElemTypes],
        ](Map.empty, Map.empty, Map.empty, Map.empty)

      case to: Mirror.ProductOf[To] =>
        TransformerBuilder[
          From,
          To,
          Case.FromLabelsAndTypes[from.MirroredElemLabels, from.MirroredElemTypes],
          Field.FromLabelsAndTypes[to.MirroredElemLabels, to.MirroredElemTypes],
          Case.FromLabelsAndTypes[from.MirroredElemLabels, from.MirroredElemTypes],
          Field.FromLabelsAndTypes[to.MirroredElemLabels, to.MirroredElemTypes],
        ](Map.empty, Map.empty, Map.empty, Map.empty)
    }

end TransformerBuilder
