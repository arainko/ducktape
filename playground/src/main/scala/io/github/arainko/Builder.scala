package io.github.arainko

import scala.util.NotGiven
import scala.deriving.Mirror as DerivingMirror
import scala.compiletime.summonInline

final case class Builder[From, To, Config <: Tuple](
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

  inline def run(source: From)(using From: DerivingMirror.Of[From]): To =
    inline From match {
      case given DerivingMirror.ProductOf[From] =>
        given DerivingMirror.ProductOf[To] = summonInline
        Macros.transformWithBuilder(source, this)
      case given DerivingMirror.SumOf[From] =>
        given DerivingMirror.SumOf[To] = summonInline
        CoproductTransformerMacros.transformWithBuilder(source, this)
    }
}

@main def main = {
  // Macros.code {
    Builder[TraitColor, Color, EmptyTuple](Map.empty, Map.empty, Map.empty)
      .withCaseInstance[TraitColor.Red.type](_ => Color.Blue)
      .withCaseInstance[TraitColor.Blue.type](_ => Color.Blue)
      .run(TraitColor.Red)
  // }

  // .withFieldConstant(_.age, 1)
  // .withFieldComputed(_.name, _.name)
  // .withFieldRenamed(_.costam, _.name)
  // .withFieldComputed(_.age, _ => 1)

}
