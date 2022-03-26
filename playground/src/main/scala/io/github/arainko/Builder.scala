package io.github.arainko

final case class Builder[From, To, Config <: Tuple](
  val constants: Map[String, Any],
  val computeds: Map[String, From => Any],
  val renames: Map[String, String]
) {
  import Configuration.*

  transparent inline def withFieldConstant[FieldType, ConstType](
    inline selector: To => FieldType,
    const: ConstType
  )(using ConstType <:< FieldType) = {
    val fieldName = SelectorMacros.selectedField(selector)
    val withConst = this.copy[From, To, Config](constants = constants + (fieldName -> const))
    BuilderMacros.withConfigEntryForField[Builder, From, To, Config, Const](withConst, selector)
  }

  transparent inline def withFieldComputed[FieldType, ComputedType](
    inline selector: To => FieldType,
    computed: From => ComputedType
  )(using ComputedType <:< FieldType) = {
    val fieldName = SelectorMacros.selectedField(selector)
    val withComputed = this.copy[From, To, Config](computeds = computeds + (fieldName -> computed.asInstanceOf[Any => Any]))
    BuilderMacros.withConfigEntryForField[Builder, From, To, Config, Computed](withComputed, selector)
  }

  transparent inline def withFieldRenamed[FromField, ToField](
    inline toSelector: To => FromField,
    inline fromSelector: From => ToField
  )(using FromField <:< ToField) = {
    val fromFieldName = SelectorMacros.selectedField(fromSelector)
    val toFieldName = SelectorMacros.selectedField(toSelector)
    val withRenamed = this.copy[From, To, Config](renames = renames + (fromFieldName -> toFieldName))
    BuilderMacros.withConfigEntryForFields[Builder, From, To, Config, Renamed](withRenamed, toSelector, fromSelector)
  }

  inline def run(source: From): To = Macros.transformWithBuilder(source, this)
}

@main def main = {
  val builder =
    Builder[Person, SecondPerson, EmptyTuple](Map.empty, Map.empty, Map.empty)
      .withFieldConstant(_.age, 1)
      .withFieldComputed(_.name, _.name)
      .withFieldRenamed(_.costam, _.name)
      .withFieldComputed(_.age, _ => 1)

  println(builder)
}
