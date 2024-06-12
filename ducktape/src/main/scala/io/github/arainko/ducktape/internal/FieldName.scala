package io.github.arainko.ducktape.internal

opaque type FieldName <: String = String

object FieldName {
  extension (self: FieldName) {
    def asDest: Dest = self
    def asSource: Source = self
  }

  opaque type Dest <: FieldName = FieldName

  object Dest {
    def apply(name: String): Dest = name

    def wrapKeys[A, M[a, b] <: Map[a, b]](map: M[String, A]): M[Dest, A] = map
  }

  opaque type Source <: FieldName = FieldName

  object Source {
    def apply(name: String): Source = name

    def wrapKeys[A, M[a, b] <: Map[a, b]](map: M[String, A]): M[Source, A] = map
  }
}
