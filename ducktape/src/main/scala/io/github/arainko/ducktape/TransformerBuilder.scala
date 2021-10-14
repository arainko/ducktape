package io.github.arainko.ducktape

import scala.deriving.Mirror
import scala.compiletime.*
import scala.compiletime.ops.int.*
import scala.util.NotGiven
import io.github.arainko.ducktape.TransformerBuilder.WithCaseInstancePartiallyApplied

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
  private val renameTransformers: Map[FieldName, (FieldName, Transformer[Any, Any])],
  private val coprodInstances: Map[Ordinal, From => To]
) {

  inline def withCaseInstance[
    Type <: From
  ]: WithCaseInstancePartiallyApplied[
    From,
    To,
    FromSubcases,
    ToSubcases,
    UnhandledFromSubcases,
    UnhandledToSubcases,
    Type
  ] = WithCaseInstancePartiallyApplied(this)

  inline def withFieldConst[Label <: String](
    const: => Field.TypeForLabel[Label, ToSubcases]
  ): TransformerBuilder[
    From,
    To,
    FromSubcases,
    ToSubcases,
    Field.DropByLabel[Label, UnhandledFromSubcases],
    Field.DropByLabel[Label, UnhandledToSubcases]
  ] = this.copy(constants = constants + (constValue[Label] -> const))

  inline def withFieldRenamed[
    FromLabel <: String,
    ToLabel <: String
  ]: TransformerBuilder[
    From,
    To,
    FromSubcases,
    ToSubcases,
    Field.DropByLabel[FromLabel, UnhandledFromSubcases],
    Field.DropByLabel[ToLabel, UnhandledToSubcases]
  ] = {
    val transformer = summonInline[
      Transformer[
        Field.TypeForLabel[FromLabel, FromSubcases],
        Field.TypeForLabel[ToLabel, ToSubcases],
      ]
    ].asInstanceOf[Transformer[Any, Any]]
    val fromLabel = constValue[FromLabel]
    val toLabel = constValue[ToLabel]

    this.copy(renameTransformers = renameTransformers + (toLabel -> (fromLabel, transformer)))
  }

  inline def withFieldComputed[Label <: String](
    f: From => Field.TypeForLabel[Label, ToSubcases]
  ): TransformerBuilder[
    From,
    To,
    FromSubcases,
    ToSubcases,
    Field.DropByLabel[Label, UnhandledFromSubcases],
    Field.DropByLabel[Label, UnhandledToSubcases]
  ] = this.copy(computeds = computeds + (constValue[Label] -> f.asInstanceOf[Any => Any]))

  inline def transformProdcutToProduct(from: From)(using
    From: Mirror.ProductOf[From],
    To: Mirror.ProductOf[To]
  ): To = {
    val fromAsProd = from.asInstanceOf[Product]

    val transformers = TransformerBuilder.transformersForAllFields[UnhandledFromSubcases, UnhandledToSubcases]
    val labelIndicesOfTo = TransformerBuilder.labelIndices[Field.ExtractLabels[ToSubcases], 0]
    val labelsToValuesOfFrom = fromAsProd.productElementNames.zip(fromAsProd.productIterator).toMap
    val valueArrayOfTo = Array.fill(labelIndicesOfTo.size)(null.asInstanceOf[Any])

    println(transformers)

    labelIndicesOfTo.foreach {
      case (label, idx) =>
        lazy val maybeValueFromRename =
          renameTransformers.get(label).map { (fromLabel, transformer) =>
            transformer.transform(labelsToValuesOfFrom(fromLabel))
          }

        lazy val maybeValueFromDerived =
          transformers.get(label).map(_.transform(labelsToValuesOfFrom(label)))

        lazy val maybeValueFromComputed =
          computeds.get(label).map(f => f(from))

        val valueForLabel =
          maybeValueFromRename.orElse(maybeValueFromDerived).orElse(maybeValueFromComputed).getOrElse(constants(label))

        valueArrayOfTo.update(idx, valueForLabel)
    }

    To.fromProduct(Tuple.fromArray(valueArrayOfTo))
  }
}

object TransformerBuilder:

  transparent inline def create[From, To] =
    summonFrom {
      case from: Mirror.ProductOf[From] => createFromProduct[From, To](from)
      case from: Mirror.SumOf[From]     => createFromSum[From, To](from)
    }

  private transparent inline def createFromProduct[From, To](from: Mirror.ProductOf[From]) =
    summonFrom {
      case to: Mirror.ProductOf[To] =>
        TransformerBuilder[
          From,
          To,
          Field.FromLabelsAndTypes[from.MirroredElemLabels, from.MirroredElemTypes],
          Field.FromLabelsAndTypes[to.MirroredElemLabels, to.MirroredElemTypes],
          Field.FromLabelsAndTypes[from.MirroredElemLabels, from.MirroredElemTypes],
          Field.FromLabelsAndTypes[to.MirroredElemLabels, to.MirroredElemTypes],
        ](Map.empty, Map.empty, Map.empty, Map.empty)
    }

  private transparent inline def createFromSum[From, To](from: Mirror.SumOf[From]) =
    summonFrom {
      case to: Mirror.SumOf[To] =>
        TransformerBuilder[
          From,
          To,
          Case.FromLabelsAndTypes[from.MirroredElemLabels, from.MirroredElemTypes],
          Case.FromLabelsAndTypes[to.MirroredElemLabels, to.MirroredElemTypes],
          Case.FromLabelsAndTypes[from.MirroredElemLabels, from.MirroredElemTypes],
          Case.FromLabelsAndTypes[to.MirroredElemLabels, to.MirroredElemTypes],
        ](Map.empty, Map.empty, Map.empty, Map.empty)

      case to: Mirror.ProductOf[To] =>
        TransformerBuilder[
          From,
          To,
          Case.FromLabelsAndTypes[from.MirroredElemLabels, from.MirroredElemTypes],
          Field.FromLabelsAndTypes[to.MirroredElemLabels, to.MirroredElemTypes],
          Case.FromLabelsAndTypes[from.MirroredElemLabels, from.MirroredElemTypes],
          Field.FromLabelsAndTypes[to.MirroredElemLabels, to.MirroredElemTypes],
        ](Map.empty, Map.empty, Map.empty, Map.empty)
    }

  inline def transformersForAllFields[
    UnhandledFromFields <: Tuple,
    UnhandledToFields <: Tuple
  ]: Map[String, Transformer[Any, Any]] =
    inline erasedValue[UnhandledToFields] match {
      case _: EmptyTuple =>
        Map.empty
      case _: (Field[label, tpe] *: tail) =>
        transformersForAllFields[UnhandledFromFields, tail] + transformerForField[label, tpe, UnhandledFromFields]
    }

  inline def transformerForField[
    ToLabel <: String,
    ToType,
    UnhandledFromFields <: Tuple
  ]: (String, Transformer[Any, Any]) =
    inline erasedValue[UnhandledFromFields] match {
      case _: EmptyTuple =>
        error("No Transformer found! - at field '" + constValue[ToLabel] + "'")
      case _: (Field[ToLabel, tpe] *: _) =>
        constValue[ToLabel] -> summonInline[Transformer[tpe, ToType]].asInstanceOf[Transformer[Any, Any]]
      case _: (_ *: tail) =>
        transformerForField[ToLabel, ToType, tail]
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

  case class WithCaseInstancePartiallyApplied[
    From,
    To,
    FromSubcases <: Tuple,
    ToSubcases <: Tuple,
    UnhandledFromSubcases <: Tuple,
    UnhandledToSubcases <: Tuple,
    Type <: From
  ](
    builder: TransformerBuilder[
      From,
      To,
      FromSubcases,
      ToSubcases,
      UnhandledFromSubcases,
      UnhandledToSubcases
    ]
  ) {
    inline def apply[ToCase <: To](
      f: Type => ToCase
    ): TransformerBuilder[
      From,
      To,
      FromSubcases,
      ToSubcases,
      Case.DropByType[Type, UnhandledFromSubcases],
      UnhandledToSubcases
    ] = {
      val ordinal = constValue[Case.OrdinalForType[Type, FromSubcases]]
      builder.copy(coprodInstances = builder.coprodInstances + (ordinal -> f.asInstanceOf[From => To]))
    }
  }

end TransformerBuilder
