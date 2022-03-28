package io.github.arainko.internal

import scala.quoted.*
import io.github.arainko.Configuration
import io.github.arainko.Configuration.*

trait ConfigurationModule { self: Module =>
  import quotes.reflect.*

  def materializeConfig[Config <: Tuple: Type]: List[Configuration] = {
    TypeRepr.of[Config].asType match {
      case '[EmptyTuple] =>
        List.empty
      case '[Const[field] *: tail] =>
        Const(materializeConstantString[field]) :: materializeConfig[tail]
      case '[Computed[field] *: tail] =>
        Computed(materializeConstantString[field]) :: materializeConfig[tail]
      case '[Renamed[dest, source] *: tail] =>
        Renamed(materializeConstantString[dest], materializeConstantString[source]) :: materializeConfig[tail]
    }
  }

  private def materializeConstantString[A <: String: Type] = TypeRepr.of[A] match {
    case ConstantType(StringConstant(value)) => value
    case other                               => report.errorAndAbort("Type is not a String!")
  }
}
