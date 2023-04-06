package io.github.arainko.ducktape.internal.modules

import io.github.arainko.ducktape.function.FunctionArguments

import scala.deriving.Mirror
import scala.quoted.*

private[ducktape] sealed trait Fields {
  export byName.{ apply => unsafeGet, contains => containsFieldWithName, get }

  val value: List[Field]

  val byName: Map[String, Field] = value.map(f => f.name -> f).toMap
}

private[ducktape] object Fields {
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

      val defaults = defaultParams[A]
      val fields = materializedMirror.mirroredElemLabels
        .zip(materializedMirror.mirroredElemTypes)
        .map((name, tpe) => Field(name, tpe.asType, defaults.get(name)))
      apply(fields)
    }

    final def fromFunctionArguments[ArgSelector <: FunctionArguments: Type](using Quotes): FieldsSubtype = {
      import quotes.reflect.*

      val fields = List.unfold(TypeRepr.of[ArgSelector]) { state =>
        PartialFunction.condOpt(state) {
          case Refinement(parent, name, fieldTpe) => Field(name, fieldTpe.asType, None) -> parent
        }
      }
      apply(fields.reverse)
    }

    final def fromValDefs(using Quotes)(valDefs: List[quotes.reflect.ValDef]): FieldsSubtype = {
      val fields = valDefs.map(vd => Field(vd.name, vd.tpt.tpe.asType, None))
      apply(fields)
    }

    private def defaultParams[T: Type](using Quotes): Map[String, Expr[Any]] = {
      import quotes.reflect.*

      val typ = TypeRepr.of[T]
      val sym = typ.typeSymbol
      val typeArgs = typ.typeArgs
      val mod = Ref(sym.companionModule)
      val names = sym.caseFields.filter(_.flags.is(Flags.HasDefault)).map(_.name)
      val body = sym.companionClass.tree.asInstanceOf[ClassDef].body
      val idents: List[Term] = body.collect {
        case deff @ DefDef(name, _, _, _) if name.startsWith("$lessinit$greater$default") =>
          mod.select(deff.symbol).appliedToTypes(typeArgs)
      }

      names.zip(idents.map(_.asExpr)).toMap
    }
  }
}
