// package io.github.arainko

// sealed trait Const[Label]

// final case class Builder[From, To, Config <: Tuple](val constants: Map[String, Any]) {

//   transparent inline def withFieldConstant[FieldType, ConstType](inline selector: To => FieldType, const: ConstType)(using
//     ConstType <:< FieldType
//   ) = {
//     val fieldName = BuilderMacros.selectedField(selector)
//     val withConst = this.copy[From, To, Config](constants = constants + (fieldName -> const))
//     BuilderMacros.withConfigEntry[To, Config, Const, [NewConfig <: Tuple] =>> Builder[From, To, NewConfig]](withConst, selector)
//   }

//   inline def run(source: From): To = Macros.transformWithBuilder(source, this)
// }

// @main def main = {
//   val builder =
//     Builder[Person, SecondPerson, EmptyTuple](Map.empty)
//       .withFieldConstant(_.age, 1)
//       .withFieldConstant(_.name, " ")

//   println(builder)
// }
