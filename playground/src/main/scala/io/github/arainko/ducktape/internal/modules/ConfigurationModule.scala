package io.github.arainko.ducktape.internal.modules

import scala.quoted.*
import io.github.arainko.ducktape.Configuration
import io.github.arainko.ducktape.Configuration.*

private[internal] trait ConfigurationModule { self: Module =>
  import quotes.reflect.*

  sealed trait MaterializedConfiguration

  object MaterializedConfiguration {
    enum Product extends MaterializedConfiguration {
      val name: String

      case Const(name: String)
      case Computed(name: String)
      case Renamed(name: String, source: String)
    }

    enum Coproduct extends MaterializedConfiguration {
      val tpe: TypeRepr

      case Instance(tpe: TypeRepr)
    }
  }

  def materializeConfig[Config <: Tuple: Type]: List[MaterializedConfiguration] = {
    TypeRepr.of[Config].asType match {
      case '[EmptyTuple] =>
        List.empty

      case '[Product.Const[field] *: tail] =>
        MaterializedConfiguration.Product.Const(materializeConstantString[field]) :: materializeConfig[tail]

      case '[Product.Computed[field] *: tail] =>
        MaterializedConfiguration.Product.Computed(materializeConstantString[field]) :: materializeConfig[tail]

      case '[Product.Renamed[dest, source] *: tail] =>
        MaterializedConfiguration.Product.Renamed(
          materializeConstantString[dest],
          materializeConstantString[source]
        ) :: materializeConfig[tail]

      case '[Coproduct.Instance[tpe] *: tail] =>
        MaterializedConfiguration.Coproduct.Instance(TypeRepr.of[tpe]) :: materializeConfig[tail]

      case '[head *: _] =>
        report.errorAndAbort(s"Unsupported configuration type: ${TypeRepr.of[head].show(using Printer.TypeReprShortCode)}")
    }
  }

  def materializeProductConfig[Config <: Tuple: Type]: List[MaterializedConfiguration.Product] =
    materializeConfig.collect {
      case config: MaterializedConfiguration.Product => config
    }

  def materializeCoproductConfig[Config <: Tuple: Type]: List[MaterializedConfiguration.Coproduct] =
    materializeConfig.collect {
      case config: MaterializedConfiguration.Coproduct => config
    }

  private def materializeConstantString[A <: String: Type] = TypeRepr.of[A] match {
    case ConstantType(StringConstant(value)) => value
    case other                               => report.errorAndAbort("Type is not a String!")
  }
}
