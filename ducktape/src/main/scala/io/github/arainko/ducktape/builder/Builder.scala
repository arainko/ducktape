package io.github.arainko.ducktape.builder.applied

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.builder.Partial
import io.github.arainko.ducktape.internal.*

import scala.compiletime.*
import scala.deriving.*

trait Builder[
  SpecificBuilder[_, _, _ <: Tuple, _ <: Tuple, _ <: Tuple, _ <: Tuple],
  From,
  To,
  FromSubcases <: Tuple,
  ToSubcases <: Tuple,
  DerivedFromSubcases <: Tuple,
  DerivedToSubcases <: Tuple
]:
  private[builder] val computeds: Map[FieldName, From => Any]
  private[builder] val constants: Map[FieldName, Any]
  private[builder] val renameTransformers: Map[FieldName, RenamedField]
  private[builder] val coprodInstances: Map[Ordinal, From => To]

  final inline def withCaseInstance[Type <: From](
    f: Type => ? <: To
  ): SpecificBuilder[
    From,
    To,
    FromSubcases,
    ToSubcases,
    Case.DropByType[Type, DerivedFromSubcases],
    DerivedToSubcases
  ] = {
    val ordinal = Ordinal(constValue[Case.OrdinalForType[Type, FromSubcases]])
    this.construct(coprodInstances = coprodInstances + (ordinal -> f.asInstanceOf[From => To]))
  }

  final inline def withFieldConst[
    Label <: String
  ]: Partial.WithFieldConst[
    SpecificBuilder,
    From,
    To,
    FromSubcases,
    ToSubcases,
    DerivedFromSubcases,
    DerivedToSubcases,
    Label
  ] = Partial.WithFieldConst(this)

  final inline def withFieldComputed[
    Label <: String
  ]: Partial.WithFieldComputed[
    SpecificBuilder,
    From,
    To,
    FromSubcases,
    ToSubcases,
    DerivedFromSubcases,
    DerivedToSubcases,
    Label
  ] = Partial.WithFieldComputed(this)

  final inline def withFieldRenamed[
    FromLabel <: String,
    ToLabel <: String
  ]: SpecificBuilder[
    From,
    To,
    FromSubcases,
    ToSubcases,
    Field.DropByLabel[FromLabel, DerivedFromSubcases],
    Field.DropByLabel[ToLabel, DerivedToSubcases]
  ] = {
    Macros.verifyFieldExists[FromLabel, FromSubcases, From]
    Macros.verifyFieldExists[ToLabel, ToSubcases, To]

    val transformer = summonInline[
      Transformer[
        Field.TypeForLabel[FromLabel, FromSubcases],
        Field.TypeForLabel[ToLabel, ToSubcases],
      ]
    ].asInstanceOf[Transformer[Any, Any]]

    val fromLabel = FieldName(constValue[FromLabel])
    val toLabel = FieldName(constValue[ToLabel])
    val renamedField = RenamedField(fromLabel, transformer)
    this.construct(renameTransformers = renameTransformers + (toLabel -> renamedField))
  }

  final inline def build: Transformer[From, To] =
    summonFrom {
      case fromMirror: Mirror.ProductOf[From] =>
        summonFrom {
          case toMirror: Mirror.ProductOf[To] =>
            from => {
              val transformers = Derivation.transformersForAllFields[DerivedFromSubcases, DerivedToSubcases]

              Unsafe.constructInstance(from.asInstanceOf[Product], toMirror) { (labelsToValuesOfFrom, label) =>
                lazy val maybeValueFromRename =
                  renameTransformers
                    .get(label)
                    .map {
                      case RenamedField(fromLabel, transformer) =>
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
        }

      case fromMirror: Mirror.SumOf[From] =>
        from => {
          val ordinalsOfFromToSingletonsOfTo =
            Derivation.ordinalsForMatchingSingletons[DerivedFromSubcases, DerivedToSubcases]
          val ordinalOfFrom = Ordinal(fromMirror.ordinal(from))

          ordinalsOfFromToSingletonsOfTo
            .get(ordinalOfFrom)
            .getOrElse(coprodInstances(ordinalOfFrom)(from))
            .asInstanceOf[To]
        }
    }

  private[builder] def construct[
    DerivedFromSubcases <: Tuple,
    DerivedToSubcases <: Tuple
  ](
    computeds: Map[FieldName, From => Any] = this.computeds,
    constants: Map[FieldName, Any] = this.constants,
    renameTransformers: Map[FieldName, RenamedField] = this.renameTransformers,
    coprodInstances: Map[Ordinal, From => To] = this.coprodInstances
  ): SpecificBuilder[From, To, FromSubcases, ToSubcases, DerivedFromSubcases, DerivedToSubcases]

end Builder
