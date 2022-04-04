package io.github.arainko

import scala.util.NotGiven
import scala.deriving.Mirror as DerivingMirror
import scala.compiletime.*

final case class Builder[From, To, Config <: Tuple] private (
  val constants: Map[String, Any],
  val computeds: Map[String, From => Any],
  val caseInstances: Map[Int, From => To]
) {
  import Configuration.*

  transparent inline def withCaseInstance[Type <: From](
    f: Type => To
  )(using DerivingMirror.SumOf[From], NotGiven[Type =:= From]) = {
    val ordinal = SelectorMacros.caseOrdinal[From, Type]
    val withCaseInstance = this.copy[From, To, Config](caseInstances = caseInstances + (ordinal -> f.asInstanceOf[From => To]))
    BuilderMacros.withConfigEntryForInstance[Builder, From, To, Config, Type](withCaseInstance)
  }

  transparent inline def withFieldConstant[FieldType, ConstType](
    inline selector: To => FieldType,
    const: ConstType
  )(using DerivingMirror.ProductOf[From], DerivingMirror.ProductOf[To], ConstType <:< FieldType) = {
    val fieldName = SelectorMacros.selectedField(selector)
    val withConst = this.copy[From, To, Config](constants = constants + (fieldName -> const))
    BuilderMacros.withConfigEntryForField[Builder, From, To, Config, Product.Const](withConst, selector)
  }

  transparent inline def withFieldComputed[FieldType, ComputedType](
    inline selector: To => FieldType,
    computed: From => ComputedType
  )(using From: DerivingMirror.ProductOf[From], To: DerivingMirror.ProductOf[To])(using ComputedType <:< FieldType) = {
    val fieldName = SelectorMacros.selectedField(selector)
    val withComputed = this.copy[From, To, Config](computeds = computeds + (fieldName -> computed.asInstanceOf[Any => Any]))
    BuilderMacros.withConfigEntryForField[Builder, From, To, Config, Product.Computed](withComputed, selector)
  }

  transparent inline def withFieldRenamed[FromField, ToField](
    inline toSelector: To => FromField,
    inline fromSelector: From => ToField
  )(using From: DerivingMirror.ProductOf[From], To: DerivingMirror.ProductOf[To])(using FromField <:< ToField) =
    BuilderMacros.withConfigEntryForFields[Builder, From, To, Config, Product.Renamed](this, toSelector, fromSelector)

  inline def run(source: From): To =
    summonFrom {
      case given DerivingMirror.SumOf[From] =>
        summonFrom {
          case given DerivingMirror.SumOf[To] =>
            CoproductTransformerMacros.transformWithBuilder(source, this)
        }
      case given DerivingMirror.ProductOf[From] =>
        summonFrom {
          case given DerivingMirror.ProductOf[To] =>
            Macros.transformWithBuilder(source, this)
        }
    }
}

object Builder {
  def create[From, To]: Builder[TraitColor, Color, EmptyTuple] =
    Builder[TraitColor, Color, EmptyTuple](Map.empty, Map.empty, Map.empty)
}

@main def main = {

  val b = Builder
    .create[TraitColor, Color]
    .withCaseInstance[TraitColor.Green.type](_ => Color.Blue)
    .withCaseInstance[TraitColor.Blue.type](_ => Color.Blue)

  // Macros.code {
  //   Builder
  //     .create[TraitColor, Color]
  //     .withCaseInstance[TraitColor.Green.type](_ => Color.Blue)
  //     .withCaseInstance[TraitColor.Blue.type](_ => Color.Blue)
  //     .run(TraitColor.Blue)
  // }

}
