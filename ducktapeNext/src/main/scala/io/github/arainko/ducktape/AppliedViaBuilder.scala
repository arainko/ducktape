package io.github.arainko.ducktape

final class AppliedViaBuilder[A, B, Args <: FunctionArguments] private (value: A, function: Any) {
  inline def transform(inline config: Arg2[A, B, Args] | Case2[A, B]*): B = ???
}

object AppliedViaBuilder {
  private def instance[A](source: A,function: Any): AppliedViaBuilder[A, Nothing, Nothing] =
    AppliedViaBuilder[A, Nothing, Nothing](source, function)

  transparent inline def create[A](source: A, inline function: Any): Any = {
    val inst = instance(source, function)
    Function.fillInTypes(function, inst)
  }
}
