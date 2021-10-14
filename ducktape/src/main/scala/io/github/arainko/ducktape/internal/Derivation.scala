package io.github.arainko.ducktape.internal

import scala.compiletime.*
import scala.compiletime.ops.int.*
import io.github.arainko.ducktape.*
import scala.collection.mutable.ArrayBuilder
import scala.deriving.Mirror

object Derivation {
  inline def transformersForAllFields[
    FromFields <: Tuple,
    ToFields <: Tuple
  ]: Map[String, Transformer[Any, Any]] =
    inline erasedValue[ToFields] match {
      case _: EmptyTuple =>
        Map.empty
      case _: (Field[label, tpe] *: tail) =>
        transformersForAllFields[FromFields, tail] + transformerForField[label, tpe, FromFields]
    }

  inline def transformerForField[
    ToLabel <: String,
    ToType,
    FromFields <: Tuple
  ]: (String, Transformer[Any, Any]) =
    inline erasedValue[FromFields] match {
      case _: EmptyTuple =>
        error("No Transformer found! - at field '" + constValue[ToLabel] + "'")
      case _: (Field[ToLabel, tpe] *: _) =>
        constValue[ToLabel] -> summonInline[Transformer[tpe, ToType]].asInstanceOf[Transformer[Any, Any]]
      case _: (_ *: tail) =>
        transformerForField[ToLabel, ToType, tail]
    }

  // return ordinal of `From` and instance of ToType
  inline def singletonToSingletonCase[
    ToLabel <: String,
    ToType,
    FromCases <: Tuple
  ]: (Int, ToType) =
    inline erasedValue[FromCases] match {
      case _: EmptyTuple =>
        error("No Transformer found! - at case '" + constValue[ToLabel] + "'")

      case _: (Case[ToLabel, tpe, ordinal] *: _) =>
        inline summonInline[Mirror.Of[ToType]] match {
          case m: Mirror.Singleton =>
            constValue[ordinal] -> m.fromProduct(EmptyTuple).asInstanceOf[ToType]
          case _ =>
            error("welp")
        }
      
      case _: (_ *: t) =>
        singletonToSingletonCase[ToLabel, ToType, t]
    }

  inline def labelIndices[ // TODO: make it prettier
    Labels <: Tuple,
    Acc <: Int
  ]: Map[String, Int] =
    inline erasedValue[Labels] match {
      case _: EmptyTuple => Map.empty
      case _: (h *: t) =>
        val labelToIndex = constValue[h].asInstanceOf[String] -> constValue[Acc]
        labelIndices[t, S[Acc]] + labelToIndex
    }

}
