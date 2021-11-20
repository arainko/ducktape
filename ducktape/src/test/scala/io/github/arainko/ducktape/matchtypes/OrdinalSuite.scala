package io.github.arainko.ducktape.matchtypes

import scala.compiletime.*
import scala.compiletime.ops.*
import munit.FunSuite
import io.github.arainko.ducktape.Case
import scala.deriving.Mirror

enum Enum:
  case Case1
  case Case2
  case Case3
  case Case4
  case Case5

class OrdinalSuite extends FunSuite {

  inline def materializeCompiletimeOrdinals[Cases <: Tuple]: Map[Enum, Int] =
    inline erasedValue[Cases] match {
      case _: EmptyTuple => Map.empty
      case _: (Case[label, tpe, ordinal] *: tail) =>
        materializeCompiletimeOrdinals[tail] + (Enum.valueOf(constValue[label]) -> constValue[ordinal])
    }

  test("compiletime ordinals match runtime ordinals") {
    val mirror = summon[Mirror.Of[Enum]]
    val runtimeOrdinals = Enum.values.map(instance => instance -> instance.ordinal).toMap
    val compiletimeOrdinals = materializeCompiletimeOrdinals[
      Case.FromLabelsAndTypes[mirror.MirroredElemLabels, mirror.MirroredElemTypes]
    ]

    runtimeOrdinals.foreach { case (instance, ordinal) =>
      val compiletimeOrdinal = compiletimeOrdinals(instance)
      assertEquals(ordinal, compiletimeOrdinal)  
    }
  }
}
