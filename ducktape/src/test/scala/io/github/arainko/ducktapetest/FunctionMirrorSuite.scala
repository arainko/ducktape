package io.github.arainko.ducktapetest

import munit.FunSuite
import io.github.arainko.ducktape.function.FunctionMirror

class FunctionMirrorSuite extends FunSuite {
  test("derives FunctionMirror for 0 arg functions") {
    val mirror = summon[FunctionMirror[() => Int]]

    summon[mirror.Args =:= EmptyTuple]
    summon[mirror.Return =:= Int]
  }

  test("derives FunctionMirror for single arg functions") {
    val mirror = summon[FunctionMirror[String => Int]]

    summon[mirror.Args =:= (String *: EmptyTuple)]
    summon[mirror.Return =:= Int]
  }

  test("derives FunctionMirror for multiple arg functions") {
    val mirror = summon[FunctionMirror[(String, Int) => Int]]
    
    summon[mirror.Args =:= (String *: Int *: EmptyTuple)]
    summon[mirror.Return =:= Int]
  }
}
