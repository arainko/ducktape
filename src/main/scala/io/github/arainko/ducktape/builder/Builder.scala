package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
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

  final transparent inline def withFieldConst[FieldType, Provided](
    inline selector: To => FieldType,
    const: Provided
  )(using Provided <:< FieldType) = {
    val selectedField = BuilderMacros.selectedField(selector)
    val constantField = FieldName(selectedField) -> const
    val modifiedBuilder = this.constructWithSameTypes(constants = constants + constantField)
    BuilderMacros.dropCompiletimeField(modifiedBuilder, selector)
  }

  final transparent inline def withFieldComputed[FieldType, Provided](
    inline selector: To => FieldType,
    f: From => Provided
  )(using Provided <:< FieldType) = {
    val selectedField = BuilderMacros.selectedField(selector)
    val computedField = FieldName(selectedField) -> f.asInstanceOf[From => Any]
    val modifiedBuilder = this.constructWithSameTypes(computeds = computeds + computedField)
    BuilderMacros.dropCompiletimeField(modifiedBuilder, selector)
  }

  final transparent inline def withFieldRenamed[FromFieldType, ToFieldType](
    inline toSelector: To => ToFieldType,
    inline fromSelector: From => FromFieldType
  )(using FromFieldType <:< ToFieldType) = {
    val selectedToField = BuilderMacros.selectedField(toSelector)
    val selectedFromField = BuilderMacros.selectedField(fromSelector)
    val transformer = summonInline[Transformer[FromFieldType, ToFieldType]].asInstanceOf[Transformer[Any, Any]]
    val renamedField = FieldName(selectedToField) -> RenamedField(FieldName(selectedFromField), transformer)
    val modifiedBuilder = this.constructWithSameTypes(renameTransformers = renameTransformers + renamedField)
    BuilderMacros.dropCompiletimeFieldsForRename(modifiedBuilder, toSelector, fromSelector)
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

  private[ducktape] def construct[
    DerivedFromSubcases <: Tuple,
    DerivedToSubcases <: Tuple
  ](
    computeds: Map[FieldName, From => Any] = this.computeds,
    constants: Map[FieldName, Any] = this.constants,
    renameTransformers: Map[FieldName, RenamedField] = this.renameTransformers,
    coprodInstances: Map[Ordinal, From => To] = this.coprodInstances
  ): SpecificBuilder[From, To, FromSubcases, ToSubcases, DerivedFromSubcases, DerivedToSubcases]

  final private[ducktape] def constructWithSameTypes(
    computeds: Map[FieldName, From => Any] = this.computeds,
    constants: Map[FieldName, Any] = this.constants,
    renameTransformers: Map[FieldName, RenamedField] = this.renameTransformers,
    coprodInstances: Map[Ordinal, From => To] = this.coprodInstances
  ): SpecificBuilder[From, To, FromSubcases, ToSubcases, DerivedFromSubcases, DerivedToSubcases] =
    this.construct[DerivedFromSubcases, DerivedToSubcases](
      computeds = computeds,
      constants = constants,
      renameTransformers = renameTransformers,
      coprodInstances = coprodInstances
    )

end Builder
