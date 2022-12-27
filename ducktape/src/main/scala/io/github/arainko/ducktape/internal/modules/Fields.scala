package io.github.arainko.ducktape.internal.modules

import scala.quoted.*
import scala.deriving.Mirror
import io.github.arainko.ducktape.function.FunctionArguments
import io.github.arainko.ducktape.internal.modules.mirror.*

sealed trait Fields {
  export byName.{ apply => unsafeGet, contains => containsFieldWithName, get }

  val value: List[Field]

  val byName: Map[String, Field] = value.map(f => f.name -> f).toMap
}

object Fields {
  def source(using sourceFields: Fields.Source): Fields.Source = sourceFields
  def dest(using destFields: Fields.Dest): Fields.Dest = destFields

  final case class Source(value: List[Field]) extends Fields
  object Source extends FieldsCompanion[Source]

  final case class Dest(value: List[Field]) extends Fields
  object Dest extends FieldsCompanion[Dest]

  sealed abstract class FieldsCompanion[FieldsSubtype <: Fields] {

    def apply(fields: List[Field]): FieldsSubtype

    final def fromMirror[A: Type](mirror: Expr[Mirror.ProductOf[A]])(using Quotes): FieldsSubtype = {
      val materializedMirror = MaterializedMirror.createOrAbort(mirror)

      val fields = materializedMirror.mirroredElemLabels
        .zip(materializedMirror.mirroredElemTypes)
        .map((name, tpe) => Field(name, tpe.asType))
      apply(fields)
    }

    final def fromFunctionArguments[ArgSelector <: FunctionArguments: Type](using Quotes): FieldsSubtype = {
      import quotes.reflect.*

      val fields = List.unfold(TypeRepr.of[ArgSelector]) { state =>
        PartialFunction.condOpt(state) {
          case Refinement(parent, name, fieldTpe) => Field(name, fieldTpe.asType) -> parent
        }
      }
      apply(fields.reverse)
    }

    final def fromValDefs(using Quotes)(valDefs: List[quotes.reflect.ValDef]): FieldsSubtype = {
      val fields = valDefs.map(vd => Field(vd.name, vd.tpt.tpe.asType))
      apply(fields)
    }

  }
}
