package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.*

import scala.compiletime.*
import scala.compiletime.ops.int.*
import scala.deriving.Mirror

final case class AppliedTransformerBuilder[
  From,
  To,
  FromSubcases <: Tuple,
  ToSubcases <: Tuple,
  DerivedFromSubcases <: Tuple,
  DerivedToSubcases <: Tuple
] private (
  private val appliedTo: From,
  override private[builder] val computeds: Map[FieldName, From => Any],
  override private[builder] val constants: Map[FieldName, Any],
  override private[builder] val renameTransformers: Map[FieldName, RenamedField],
  override private[builder] val coprodInstances: Map[Ordinal, From => To]
) extends Builder[AppliedTransformerBuilder, From, To, FromSubcases, ToSubcases, DerivedFromSubcases, DerivedToSubcases]:

  private[builder] def construct(
    computeds: Map[FieldName, From => Any] = this.computeds,
    constants: Map[FieldName, Any] = this.constants,
    renameTransformers: Map[FieldName, RenamedField] = this.renameTransformers,
    coprodInstances: Map[Ordinal, From => To] = this.coprodInstances
  ): AppliedTransformerBuilder[From, To, FromSubcases, ToSubcases, DerivedFromSubcases, DerivedToSubcases] =
    this.copy(
      computeds = computeds,
      constants = constants,
      renameTransformers = renameTransformers,
      coprodInstances = coprodInstances
    )

  inline def transform: To = build.transform(appliedTo)

end AppliedTransformerBuilder

object AppliedTransformerBuilder:

  transparent inline def create[From, To](appliedTo: From) =
    summonFrom {
      case from: Mirror.ProductOf[From] => createFromProduct[From, To](appliedTo, from)
      case from: Mirror.SumOf[From]     => createFromSum[From, To](appliedTo, from)
    }

  private transparent inline def createFromProduct[From, To](appliedTo: From, from: Mirror.ProductOf[From]) =
    summonFrom {
      case to: Mirror.ProductOf[To] =>
        AppliedTransformerBuilder[
          From,
          To,
          Field.FromLabelsAndTypes[from.MirroredElemLabels, from.MirroredElemTypes],
          Field.FromLabelsAndTypes[to.MirroredElemLabels, to.MirroredElemTypes],
          Field.FromLabelsAndTypes[from.MirroredElemLabels, from.MirroredElemTypes],
          Field.FromLabelsAndTypes[to.MirroredElemLabels, to.MirroredElemTypes],
        ](appliedTo, Map.empty, Map.empty, Map.empty, Map.empty)
    }

  private transparent inline def createFromSum[From, To](appliedTo: From, from: Mirror.SumOf[From]) =
    summonFrom {
      case to: Mirror.SumOf[To] =>
        AppliedTransformerBuilder[
          From,
          To,
          Case.FromLabelsAndTypes[from.MirroredElemLabels, from.MirroredElemTypes],
          Case.FromLabelsAndTypes[to.MirroredElemLabels, to.MirroredElemTypes],
          Case.FromLabelsAndTypes[from.MirroredElemLabels, from.MirroredElemTypes],
          Case.FromLabelsAndTypes[to.MirroredElemLabels, to.MirroredElemTypes],
        ](appliedTo, Map.empty, Map.empty, Map.empty, Map.empty)

      case to: Mirror.ProductOf[To] =>
        AppliedTransformerBuilder[
          From,
          To,
          Case.FromLabelsAndTypes[from.MirroredElemLabels, from.MirroredElemTypes],
          Field.FromLabelsAndTypes[to.MirroredElemLabels, to.MirroredElemTypes],
          Case.FromLabelsAndTypes[from.MirroredElemLabels, from.MirroredElemTypes],
          Field.FromLabelsAndTypes[to.MirroredElemLabels, to.MirroredElemTypes],
        ](appliedTo, Map.empty, Map.empty, Map.empty, Map.empty)
    }

end AppliedTransformerBuilder
