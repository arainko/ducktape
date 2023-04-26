package io.github.arainko.ducktape.internal.macros

private[ducktape] object Errors {
  inline def cannotDetermineTransformationMode: Nothing =
    scala.compiletime.error("""ducktape was not able to determine the exact mode of fallible transformations.

Make sure you have an instance of either Transformer.Mode.Accumulating[F] or Transformer.Mode.FailFast[F] (and not its supertype Transformer.Mode[F]) in implicit scope.
""")
}
