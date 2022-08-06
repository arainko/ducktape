package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.macros.*
import io.github.arainko.ducktape.function.*
import scala.deriving.Mirror
import io.github.arainko.ducktape.builder.*

extension [Source](value: Source) {
  def into[Dest]: AppliedBuilder[Source, Dest] = AppliedBuilder(value)

  def to[Dest](using Transformer[Source, Dest]): Dest = Transformer[Source, Dest].transform(value)

  transparent inline def intoVia[Func](inline function: Func)(using Source: Mirror.ProductOf[Source], Func: FunctionMirror[Func]) =
    AppliedViaBuilder.create(value, function)

  inline def via[Func](inline function: Func)(using
    Func: FunctionMirror[Func],
    Source: Mirror.ProductOf[Source]
  ): Func.Return = ProductTransformerMacros.via(value, function)
}

final case class Person(name: String, age: Int)

enum Color {
  case Red, Green, Blue
}

enum ColorExtra {
  case Red, Green, Blue, Orange
}

@main def test = {
  def someMethod(age: Int, name: String, addArg: String) =
    Person("asd" + addArg, age)

  val mirror = summon[FunctionMirror[Any => Any]]

  val cos = Person("asd", 1)

  DebugMacros.codeCompiletime {
  ColorExtra.Red.into[Color](Case.const[ColorExtra.Orange.type](Color.Blue))
  }

  val builder = AppliedViaBuilder.create(cos, someMethod)

  DebugMacros.codeCompiletime {
    val aaaa = cos.intoVia(someMethod)(Arg.renamed(_.addArg, _.name))
  }
  
}