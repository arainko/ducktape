package io.github.arainko.ducktape.internal

import scala.compiletime.*
import scala.compiletime.ops.int.*
import io.github.arainko.ducktape.*
import scala.collection.mutable.ArrayBuilder
import scala.deriving.Mirror

object Derivation {
  inline def summonSingleton[T]: T =
    inline summonInline[Mirror.ProductOf[T]] match {
      case m: Mirror.Singleton      => m.fromProduct(EmptyTuple)
      case m: Mirror.SingletonProxy => m.fromProduct(EmptyTuple)
      case _ => error("Cannot summon a singleton!")
    }

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
  inline def ordinalWithSingletonLabel[
    FromLabel <: String,
    FromType,
    ToCases <: Tuple
  ]: (Int, Any) =
    inline erasedValue[ToCases] match {
      case _: EmptyTuple =>
        error("No Transformer found! - at case '" + constValue[FromLabel] + "'")
      case _: (Case[FromLabel, tpe, ordinal] *: _) =>
        constValue[ordinal] -> summonSingleton[tpe]
      case _: (_ *: t) =>
        ordinalWithSingletonLabel[FromLabel, FromType, t]
    }

  inline def ordinalsForMatchingSingletons[
    FromCases <: Tuple,
    ToCases <: Tuple
  ]: Map[Int, Any] = 
  inline erasedValue[FromCases] match {
    case _: EmptyTuple => Map.empty
    case _: (Case[label, tpe, ordinal] *: tail) =>
      ordinalsForMatchingSingletons[tail, ToCases] + ordinalWithSingletonLabel[label, tpe, ToCases]
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
