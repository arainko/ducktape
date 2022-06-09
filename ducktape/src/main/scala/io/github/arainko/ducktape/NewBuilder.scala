package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.macros.*
import scala.deriving.Mirror

final case class NewBuilder[Source, Dest](private val appliedTo: Source) {
  inline def transform(inline config: FieldConfig[Source, Dest]*)(using Mirror.ProductOf[Source], Mirror.ProductOf[Dest]) = 
    ProductTransformerMacros.transformNewConfig(appliedTo, config*)

}

final case class Costam(int: Int, str: String)
final case class Costam2(int: Int, str: String)

@main def run = {
  val builder = NewBuilder[Costam, Costam2](Costam(1, "hello")).transform(
    const(_.int, 1),
    computed(_.int, _.int * 2),
    renamed(_.str, _.str),
  )

  println(builder)
}
