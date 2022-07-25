package io.github.arainko.ducktape

import io.github.arainko.ducktape.function.FunctionArguments
import scala.deriving.Mirror

opaque type ArgConfig[Source, Dest, NamedArgs <: Tuple] = Unit

def argConst[Source, Dest, ArgType, ActualType, NamedArgs <: Tuple](
  selector: FunctionArguments[NamedArgs] => ArgType,
  const: ActualType
)(using Mirror.ProductOf[Source], ActualType <:< ArgType): ArgConfig[Source, Dest, NamedArgs] = ArgConfig.instance

object ArgConfig {
  private[ducktape] def instance[Source, Dest, NamedArgs <: Tuple]: ArgConfig[Source, Dest, NamedArgs] = ()
}
