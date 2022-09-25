package io.github.arainko.ducktape.internal

private[ducktape] final class NotQuotedException(name: String)
    extends Exception(
      s"""
    |'$name' was not lifted away from the AST with a macro but also skirted past the compiler into the runtime.
    |This is not good.
    |Please file an issue on the 'ducktape' repository.""".stripMargin
    )
