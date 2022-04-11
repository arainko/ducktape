package io.github.arainko.ducktape

import io.github.arainko.ducktape.Builder.Applied
import io.github.arainko.ducktape.internal.macros.*
import scala.runtime.FunctionXXL
import javax.swing.DebugGraphics
import scala.deriving.Mirror

extension [From](value: From) {
  def into[To]: Applied[From, To, EmptyTuple] = Builder.applied[From, To](value)

  def to[To](using Transformer[From, To]): To = Transformer[From, To].transform(value)

  transparent inline def via[F](inline f: F)(using F: FunctionMirror[F], A: Mirror.ProductOf[From]) =
    ProductTransformerMacros.via(value, f)
}

final case class Costam(int: Int)

trait FunctionMirror[F] {
  type Args
  type Return
}

object FunctionMirror {
  type Aux[F, A, R] = FunctionMirror[F] {
    type Args = A
    type Return = R
  }

  transparent inline given [F]: FunctionMirror[F] = DebugMacros.functionMirror[F]
}

@main def run = {
  import io.github.arainko.ducktape.*

  val costam = [A] => (int: A) => int.toString

  DebugMacros.code {
    val asd = Costam(1).via(costam[Int])
  }

  // val cos = DebugMacros.methodParams(Costam.apply)

  // val cos = func(Costam.apply)
}
