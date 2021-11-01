package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.*

import scala.compiletime.*
import scala.compiletime.ops.int.*
import scala.deriving.Mirror

private[ducktape] object Derivation:
  inline def summonSingleton[T]: T =
    inline summonInline[Mirror.ProductOf[T]] match {
      case m: Mirror.Singleton      => m.fromProduct(EmptyTuple)
      case m: Mirror.SingletonProxy => m.fromProduct(EmptyTuple)
      case _                        => error("Cannot summon a singleton of type " + Macros.showType[T])
    }

  inline def transformersForAllFields[
    FromFields <: Tuple,
    ToFields <: Tuple
  ]: Map[FieldName, Transformer[Any, Any]] =
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
  ]: (FieldName, Transformer[Any, Any]) =
    inline erasedValue[FromFields] match {
      case _: EmptyTuple =>
        error("Transformer not found for field '" + constValue[ToLabel] + "' with type " + Macros.showType[ToType])
      case _: (Field[ToLabel, tpe] *: _) =>
        FieldName(constValue[ToLabel]) -> summonInline[Transformer[tpe, ToType]].asInstanceOf[Transformer[Any, Any]]
      case _: (_ *: tail) =>
        transformerForField[ToLabel, ToType, tail]
    }

  // return ordinal of `From` and instance of ToType
  inline def ordinalWithSingletonLabel[
    FromLabel <: String,
    FromType,
    ToCases <: Tuple
  ]: (Ordinal, Any) =
    inline erasedValue[ToCases] match {
      case _: EmptyTuple =>
        error("Singleton Transformer not found for case: " + Macros.showType[FromType])
      case _: (Case[FromLabel, tpe, ordinal] *: _) =>
        Ordinal(constValue[ordinal]) -> summonSingleton[tpe]
      case _: (_ *: t) =>
        ordinalWithSingletonLabel[FromLabel, FromType, t]
    }

  inline def ordinalsForMatchingSingletons[
    FromCases <: Tuple,
    ToCases <: Tuple
  ]: Map[Ordinal, Any] =
    inline erasedValue[FromCases] match {
      case _: EmptyTuple => Map.empty
      case _: (Case[label, tpe, ordinal] *: tail) =>
        ordinalsForMatchingSingletons[tail, ToCases] + ordinalWithSingletonLabel[label, tpe, ToCases]
    }

  inline def labelIndices[ // TODO: make it prettier
    Labels <: Tuple,
    Acc <: Int
  ]: Map[FieldName, Int] =
    inline erasedValue[Labels] match {
      case _: EmptyTuple => Map.empty
      case _: (h *: t) =>
        val labelToIndex = FieldName(constValue[h].asInstanceOf[String]) -> constValue[Acc]
        labelIndices[t, S[Acc]] + labelToIndex
    }

end Derivation
