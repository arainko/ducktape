package io.github.arainko.ducktape

import io.github.arainko.ducktape.Builder.Applied
import io.github.arainko.ducktape.internal.macros.DebugMacros
import scala.runtime.FunctionXXL
import javax.swing.DebugGraphics

extension [From](value: From) {
  def into[To]: Applied[From, To, EmptyTuple] = Builder.applied[From, To](value)

  def to[To](using Transformer[From, To]): To = Transformer[From, To].transform(value)
}

final case class Costam(int: Int, value: String)

trait FunctionMirror[F] {
  type Args
  type Return
}

object FunctionMirror {
  transparent inline def values[F] = DebugMacros.functionMirror[F]
}


@main def run = {

  DebugMacros.structure {
    new Function1[Int, Int] {
      def apply(int: Int): Int = int
    }
  }

  // DebugMacros.code {

  // }



  // summon[cos.sReturn =:= Int]
  // summon[cos.Args =:= (Int, String)]

  // val res: Costam = cos.tupled((1, "asd"))


  // println(res)
  // val res = DebugMacros.methodParams(Costam.apply)
  // println(res)
}
