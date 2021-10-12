package io.github.arainko.ducktape

import scala.deriving.Mirror
import scala.compiletime.*

type Ordinal = Int
type FieldName = String

case class TransformerBuilder[
  From,
  To,
  FromSubcases <: Tuple,
  ToSubcases <: Tuple,
  UnhandledFromSubcases <: Tuple,
  UnhandledToSubcases <: Tuple
](
  private val computeds: Map[FieldName, From => Any],
  private val constants: Map[FieldName, Any],
  private val coprodInstances: Map[Ordinal, From => To]
) {

  inline def withCaseInstance[Type <: From, ToCase <: To]( // use the partially applied trick
    f: Type => ToCase
  ): TransformerBuilder[
    From,
    To,
    FromSubcases,
    ToSubcases,
    Case.DropByType[Type, UnhandledFromSubcases],
    UnhandledToSubcases
  ] = {
    val ordinal = constValue[Case.OrdinalForType[Type, FromSubcases]]
    this.copy(coprodInstances = coprodInstances + (ordinal -> f.asInstanceOf[From => To]))
  }

  inline def withFieldConst[Label <: String](
    const: Field.TypeForLabel[Label, ToSubcases]
  ): TransformerBuilder[
    From,
    To,
    FromSubcases,
    ToSubcases,
    Field.DropByLabel[Label, UnhandledFromSubcases],
    Field.DropByLabel[Label, UnhandledToSubcases]
  ] = this.copy(constants = constants + (constValue[Label] -> const))

  inline def withFieldRenamed[FromLabel <: String, ToLabel <: String] = ???

  inline def withFieldComputed[Label <: String](
    f: From => Field.TypeForLabel[Label, ToSubcases]
  ): TransformerBuilder[
    From,
    To,
    FromSubcases,
    ToSubcases,
    Field.DropByLabel[Label, UnhandledFromSubcases],
    Field.DropByLabel[Label, UnhandledToSubcases]
  ] = this.copy(computeds = computeds + (constValue[Label] -> f.asInstanceOf[Any => Any]))
}

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
        ](Map.empty, Map.empty, Map.empty)
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
        ](Map.empty, Map.empty, Map.empty)

      case to: Mirror.ProductOf[To] =>
        TransformerBuilder[
          From,
          To,
          Case.FromLabelsAndTypes[from.MirroredElemLabels, from.MirroredElemTypes],
          Field.FromLabelsAndTypes[to.MirroredElemLabels, to.MirroredElemTypes],
          Case.FromLabelsAndTypes[from.MirroredElemLabels, from.MirroredElemTypes],
          Field.FromLabelsAndTypes[to.MirroredElemLabels, to.MirroredElemTypes],
        ](Map.empty, Map.empty, Map.empty)
    }

end TransformerBuilder
