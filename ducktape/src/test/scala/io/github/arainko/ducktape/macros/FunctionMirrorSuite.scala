package io.github.arainko.ducktape.macros

import io.github.arainko.ducktape.function.FunctionMirror
import munit.FunSuite

class FunctionMirrorSuite extends FunSuite {
  test("derives FunctionMirror for 0 arg functions") {
    val mirror = summon[FunctionMirror[() => Int]]

    summon[mirror.Return =:= Int]
  }

  test("derives FunctionMirror for single arg functions") {
    val mirror = summon[FunctionMirror[String => Int]]

    summon[mirror.Return =:= Int]
  }

  test("derives FunctionMirror for multiple arg functions") {
    val mirror = summon[FunctionMirror[(String, Int) => Int]]

    summon[mirror.Return =:= Int]
  }
}
