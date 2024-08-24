package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.MacroRepro

case class From(name: String)

object scoped {

  opaque type Ident = String

  case class To(name: String, id: Ident)

  def f(
    f: From,
    theId: scoped.Ident
  ): scoped.To = f.into[scoped.To].transform(Field.const(_.id, theId))
}

object demo {
  def f(
    f: From,
    theId: scoped.Ident
  ): scoped.To = 
    MacroRepro.iterateOverMirroredElemTypes[scoped.To]
    MacroRepro.iterateOverMirroredElemTypes[scoped.To]
    f.into[scoped.To].transform(Field.const(_.id, theId))


}

// @main def main = {
//   println(scoped.f(From("a"), "ident".asInstanceOf[scoped.Ident]))
//   println(demo.f(From("a"), "ident".asInstanceOf[scoped.Ident]))
// }
