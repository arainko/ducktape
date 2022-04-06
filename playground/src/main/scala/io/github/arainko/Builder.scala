package io.github.arainko

import io.github.arainko.Builder.Applied
import io.github.arainko.Builder.Definition

import scala.compiletime.*
import scala.deriving.Mirror as DerivingMirror
import scala.util.NotGiven

sealed trait Builder[F[_, _, _ <: Tuple], From, To, Config <: Tuple] { self =>
  import Configuration.*

  val constants: Map[String, Any]
  val computeds: Map[String, From => Any]
  val caseInstances: Map[Int, From => To]

  transparent inline def withCaseInstance[Type <: From](
    f: Type => To
  )(using DerivingMirror.SumOf[From], NotGiven[Type =:= From]) = {
    val ordinal = SelectorMacros.caseOrdinal[From, Type]
    val withCaseInstance = self.construct(caseInstances = caseInstances + (ordinal -> f.asInstanceOf[From => To]))
    BuilderMacros.withConfigEntryForInstance[F, From, To, Config, Type](withCaseInstance)
  }

  transparent inline def withFieldConstant[FieldType, ConstType](
    inline selector: To => FieldType,
    const: ConstType
  )(using DerivingMirror.ProductOf[From], DerivingMirror.ProductOf[To], ConstType <:< FieldType) = {
    val fieldName = SelectorMacros.selectedField(selector)
    val withConst = self.construct(constants = constants + (fieldName -> const))
    BuilderMacros.withConfigEntryForField[F, From, To, Config, Product.Const](withConst, selector)
  }

  transparent inline def withFieldComputed[FieldType, ComputedType](
    inline selector: To => FieldType,
    computed: From => ComputedType
  )(using From: DerivingMirror.ProductOf[From], To: DerivingMirror.ProductOf[To])(using ComputedType <:< FieldType) = {
    val fieldName = SelectorMacros.selectedField(selector)
    val withComputed = self.construct(computeds = computeds + (fieldName -> computed.asInstanceOf[Any => Any]))
    BuilderMacros.withConfigEntryForField[F, From, To, Config, Product.Computed](withComputed, selector)
  }

  transparent inline def withFieldRenamed[FromField, ToField](
    inline toSelector: To => FromField,
    inline fromSelector: From => ToField
  )(using From: DerivingMirror.ProductOf[From], To: DerivingMirror.ProductOf[To])(using FromField <:< ToField) =
    BuilderMacros.withConfigEntryForFields[F, From, To, Config, Product.Renamed](self.instance, toSelector, fromSelector)

  protected def construct(
    constants: Map[String, Any] = constants,
    computeds: Map[String, From => Any] = computeds,
    caseInstances: Map[Int, From => To] = caseInstances
  ): F[From, To, Config]

  protected def instance: F[From, To, Config]

}

object Builder {

  def definition[From, To]: Definition[From, To, EmptyTuple] =
    Definition[From, To, EmptyTuple](Map.empty, Map.empty, Map.empty)

  def applied[From, To](source: From): Applied[From, To, EmptyTuple] =
    Applied[From, To, EmptyTuple](source, Map.empty, Map.empty, Map.empty)

  final case class Definition[From, To, Config <: Tuple] private[Builder] (
    constants: Map[String, Any],
    computeds: Map[String, From => Any],
    caseInstances: Map[Int, From => To]
  ) extends Builder[Definition, From, To, Config] { self =>

    inline def build(using From: DerivingMirror.Of[From], To: DerivingMirror.Of[To]): Transformer[From, To] =
      new {
        def transform(from: From): To =
          inline erasedValue[(From.type, To.type)] match {
            case (_: DerivingMirror.SumOf[From], _: DerivingMirror.SumOf[To]) =>
              CoproductTransformerMacros.transformWithBuilder(from, self)(using summonInline, summonInline)
            case (_: DerivingMirror.ProductOf[From], _: DerivingMirror.ProductOf[To]) =>
              Macros.transformWithBuilder(from, self)(using summonInline, summonInline)
          }
      }

    override protected def construct(
      constants: Map[String, Any],
      computeds: Map[String, From => Any],
      caseInstances: Map[Int, From => To]
    ): Definition[From, To, Config] =
      this.copy[From, To, Config](constants = constants, computeds = computeds, caseInstances = caseInstances)

    override protected def instance: Definition[From, To, Config] = this
  }

  final case class Applied[From, To, Config <: Tuple] private[Builder] (
    private val appliedTo: From,
    constants: Map[String, Any],
    computeds: Map[String, From => Any],
    caseInstances: Map[Int, From => To]
  ) extends Builder[Applied, From, To, Config] { self =>

    inline def transform(using From: DerivingMirror.Of[From], To: DerivingMirror.Of[To]): To =
      inline erasedValue[(From.type, To.type)] match {
        case (_: DerivingMirror.SumOf[From], _: DerivingMirror.SumOf[To]) =>
          CoproductTransformerMacros.transformWithBuilder(appliedTo, self)(using summonInline, summonInline)
        case (_: DerivingMirror.ProductOf[From], _: DerivingMirror.ProductOf[To]) =>
          Macros.transformWithBuilder(appliedTo, self)(using summonInline, summonInline)
      }

    override protected def construct(
      constants: Map[String, Any],
      computeds: Map[String, From => Any],
      caseInstances: Map[Int, From => To]
    ): Applied[From, To, Config] =
      this.copy[From, To, Config](constants = constants, computeds = computeds, caseInstances = caseInstances)

    override protected def instance: Applied[From, To, Config] = this
  }
}
