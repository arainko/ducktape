package io.github.arainko.ducktape

case class From(name: String)

object scoped {

  opaque type Ident = String

  case class To(name: String, id: Ident)
}

object demo {
  def f(
    f: From,
    theId: scoped.Ident
  ): scoped.To =
    f.into[scoped.To].transform(Field.const(_.id, theId))
}
