package io.github.arainko.ducktape

type Ordinal = Int
type FieldName = String

case class TransformerBuilder[
  From,
  To,
  FromSubcases <: Tuple,
  ToSubcases <: Tuple,
  UnhandledFromSubcases <: Tuple,
  UnhandledToSubcases <: Tuple
](
  private val computeds: Map[FieldName, From => Any],
  private val constants: Map[FieldName, Any],
  private val coprodInstances: Map[Ordinal, Any]
) {

  def withCoprodInstance = ???

  def withFieldConst = ???

  def withFieldRenamed = ???

  def withFieldComputed = ???
}


