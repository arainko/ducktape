package io.github.arainko.ducktape

import scala.deriving.Mirror
import scala.compiletime.*
import scala.compiletime.ops.int.*
import scala.util.NotGiven
import io.github.arainko.ducktape.internal.*

type Ordinal = Int
type FieldName = String

final case class TransformerBuilder[
  From,
  To,
  FromSubcases <: Tuple,
  ToSubcases <: Tuple,
  DerivedFromSubcases <: Tuple,
  DerivedToSubcases <: Tuple
] private (
  private val computeds: Map[FieldName, From => Any],
  private val constants: Map[FieldName, Any],
  private val renameTransformers: Map[FieldName, (FieldName, Transformer[Any, Any])],
  private val coprodInstances: Map[Ordinal, From => To]
) {

  inline def withCaseInstance[Type <: From](
    f: Type => ? <: To
  ): TransformerBuilder[
    From,
    To,
    FromSubcases,
    ToSubcases,
    Case.DropByType[Type, DerivedFromSubcases],
    DerivedToSubcases
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
    Field.DropByLabel[Label, DerivedFromSubcases],
    Field.DropByLabel[Label, DerivedToSubcases]
  ] = this.copy(constants = constants + (constValue[Label] -> const))

  inline def withFieldRenamed[
    FromLabel <: String,
    ToLabel <: String
  ]: TransformerBuilder[
    From,
    To,
    FromSubcases,
    ToSubcases,
    Field.DropByLabel[FromLabel, DerivedFromSubcases],
    Field.DropByLabel[ToLabel, DerivedToSubcases]
  ] = {
    val transformer = summonInline[
      Transformer[
        Field.TypeForLabel[FromLabel, FromSubcases],
        Field.TypeForLabel[ToLabel, ToSubcases],
      ]
    ].asInstanceOf[Transformer[Any, Any]]
    val fromLabel = constValue[FromLabel]
    val toLabel = constValue[ToLabel]

    this.copy(renameTransformers = renameTransformers + (toLabel -> (fromLabel, transformer)))
  }

  inline def withFieldComputed[Label <: String](
    f: From => Field.TypeForLabel[Label, ToSubcases]
  ): TransformerBuilder[
    From,
    To,
    FromSubcases,
    ToSubcases,
    Field.DropByLabel[Label, DerivedFromSubcases],
    Field.DropByLabel[Label, DerivedToSubcases]
  ] = this.copy(computeds = computeds + (constValue[Label] -> f.asInstanceOf[Any => Any]))

  inline def build: Transformer[From, To] =
    summonFrom {
      case mirrors: Both[Mirror.ProductOf[From], Mirror.ProductOf[To]] =>
        new Transformer[From, To]:
          def transform(from: From) = {
            val Both(fromMirror, toMirror) = mirrors
            val transformers = Derivation.transformersForAllFields[DerivedFromSubcases, DerivedToSubcases]

            Unsafe.constructInstance(from.asInstanceOf[Product], toMirror) { (labelsToValuesOfFrom, label) =>
              lazy val maybeValueFromRename =
                renameTransformers
                  .get(label)
                  .map { (fromLabel, transformer) =>
                    transformer.transform(labelsToValuesOfFrom(fromLabel))
                  }

              lazy val maybeValueFromDerived =
                transformers
                  .get(label)
                  .map(_.transform(labelsToValuesOfFrom(label)))

              lazy val maybeValueFromComputed =
                computeds.get(label).map(f => f(from))

              maybeValueFromRename
                .orElse(maybeValueFromDerived)
                .orElse(maybeValueFromComputed)
                .getOrElse(constants(label))
            }
          }

      case fromMirror: Mirror.SumOf[From] =>
        new Transformer[From, To]:
          def transform(from: From) = {
            val ordinalsOfFromToSingletonsOfTo =
              Derivation.ordinalsForMatchingSingletons[DerivedFromSubcases, DerivedToSubcases]
            val ordinalOfA = fromMirror.ordinal(from)

            ordinalsOfFromToSingletonsOfTo
              .get(ordinalOfA)
              .getOrElse(coprodInstances(ordinalOfA)(from))
              .asInstanceOf[To]
          }
    }

  inline def transform(from: From): To = build.transform(from)
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
