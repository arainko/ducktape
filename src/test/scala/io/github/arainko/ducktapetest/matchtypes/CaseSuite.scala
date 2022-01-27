package io.github.arainko.ducktapetest.matchtypes

import scala.compiletime.*
import scala.compiletime.ops.*
import munit.FunSuite
import io.github.arainko.ducktape.Case
import scala.deriving.Mirror
import io.github.arainko.ducktapetest.matchtypes.CaseSuite

object CaseSuite:
  enum Enum:
    case Case1
    case Case2
    case Case3
    case Case4
    case Case5

end CaseSuite

class CaseSuite extends FunSuite {
  import CaseSuite.*

  private val mirror = summon[Mirror.Of[Enum]]

  test("compiletime ordinals match runtime ordinals") {
    val runtimeOrdinals = Enum.values.map(instance => instance -> instance.ordinal).toMap
    val compiletimeOrdinals = materializeCompiletimeOrdinals[
      Case.FromLabelsAndTypes[mirror.MirroredElemLabels, mirror.MirroredElemTypes]
    ]

    runtimeOrdinals.foreach {
      case (instance, ordinal) =>
        val compiletimeOrdinal = compiletimeOrdinals(instance)
        assertEquals(ordinal, compiletimeOrdinal)
    }
  }

  test("Case.FromLabelsAndTypes has a proper type") {
    type Expected =
      Case["Case1", Enum.Case1.type, 0] *:
        Case["Case2", Enum.Case2.type, 1] *:
        Case["Case3", Enum.Case3.type, 2] *:
        Case["Case4", Enum.Case4.type, 3] *:
        Case["Case5", Enum.Case5.type, 4] *:
        EmptyTuple

    type Actual = Case.FromLabelsAndTypes[mirror.MirroredElemLabels, mirror.MirroredElemTypes]

    summon[Actual =:= Expected]
  }

  test("Case.DropByType drops an element of a tuple based on its type") {
    type Expected =
      Case["Case1", Enum.Case1.type, 0] *:
        Case["Case2", Enum.Case2.type, 1] *:
        Case["Case4", Enum.Case4.type, 3] *:
        Case["Case5", Enum.Case5.type, 4] *:
        EmptyTuple

    type Cases = Case.FromLabelsAndTypes[mirror.MirroredElemLabels, mirror.MirroredElemTypes]
    type Actual = Case.DropByType[Enum.Case3.type, Cases]

    summon[Actual =:= Expected]
  }

  test("Case.OrdinalForType returns a compiletime ordinal for a given type") {
    type Expected = 2

    type Cases = Case.FromLabelsAndTypes[mirror.MirroredElemLabels, mirror.MirroredElemTypes]
    type Actual = Case.OrdinalForType[Enum.Case3.type, Cases]

    summon[Actual =:= Expected]
  }

  test("Case.OrdinalForType returns Nothing if a given type is not found") {
    type Expected = Nothing

    type Cases = Case.FromLabelsAndTypes[mirror.MirroredElemLabels, mirror.MirroredElemTypes]
    type Actual = Case.OrdinalForType[Int, Cases]

    summon[Actual =:= Expected]
  }

  private inline def materializeCompiletimeOrdinals[Cases <: Tuple]: Map[Enum, Int] =
    inline erasedValue[Cases] match {
      case _: EmptyTuple => Map.empty
      case _: (Case[label, tpe, ordinal] *: tail) =>
        materializeCompiletimeOrdinals[tail] + (Enum.valueOf(constValue[label]) -> constValue[ordinal])
    }
}
