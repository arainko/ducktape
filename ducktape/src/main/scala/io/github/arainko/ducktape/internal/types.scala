package io.github.arainko.ducktape.internal

opaque type Ordinal <: Int = Int
opaque type FieldName <: String = String

object Ordinal:
  def apply(value: Int): Ordinal = value
  
end Ordinal

object FieldName:
  def apply(value: String): FieldName = value
  def wrapAll[A](map: Map[String, A]): Map[FieldName, A] = map

end FieldName