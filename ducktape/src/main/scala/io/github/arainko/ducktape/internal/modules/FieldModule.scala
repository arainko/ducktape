package io.github.arainko.ducktape.internal.modules

import io.github.arainko.ducktape.Transformer
import io.github.arainko.ducktape.function.FunctionArguments

import scala.compiletime.*
import scala.deriving.*
import scala.quoted.*

private[ducktape] trait FieldModule { self: Module & MirrorModule =>
  import quotes.reflect.*

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
  }

  protected trait FieldsCompanion[FieldsSubtype <: Fields] {

    def apply(fields: List[Field]): FieldsSubtype

    final def fromMirror[A: Type](mirror: Expr[Mirror.ProductOf[A]]): FieldsSubtype = {
      val materializedMirror = MaterializedMirror.createOrAbort(mirror)

      val fields = materializedMirror.mirroredElemLabels
        .zip(materializedMirror.mirroredElemTypes)
        .map(Field.apply)

      apply(fields)
    }

    final def fromFunctionArguments[ArgSelector <: FunctionArguments: Type]: FieldsSubtype = {
      val fields = List.unfold(TypeRepr.of[ArgSelector]) { state =>
        PartialFunction.condOpt(state) {
          case Refinement(parent, name, fieldTpe) => Field(name, fieldTpe) -> parent
        }
      }
      apply(fields.reverse)
    }

    final def fromValDefs(valDefs: List[ValDef]): FieldsSubtype = {
      val fields = valDefs.map(vd => Field(vd.name, vd.tpt.tpe))
      apply(fields)
    }

  }

  final case class Field(name: String, tpe: TypeRepr) {

    def transformerTo(that: Field): Expr[Transformer[?, ?]] =
      (tpe.asType -> that.tpe.asType) match {
        case '[src] -> '[dest] =>
          Implicits.search(TypeRepr.of[Transformer[src, dest]]) match {
            case success: ImplicitSearchSuccess => success.tree.asExprOf[Transformer[src, dest]]
            case err: ImplicitSearchFailure     => report.errorAndAbort(err.explanation)
          }
      }

  }

  extension (companion: Suggestion.type) {
    def fromFields(fields: Fields): List[Suggestion] = fields.value.map(f => Suggestion(s"_.${f.name}"))
  }
}
