package io.github.arainko.ducktape.internal.modules

import scala.quoted.*
import scala.deriving.*
import io.github.arainko.ducktape.Transformer
import io.github.arainko.ducktape.function.NamedArgument
import scala.compiletime.*
import io.github.arainko.ducktape.function.FunctionArguments

private[internal] trait FieldModule { self: Module & MirrorModule =>
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

    final def fromNamedArguments[ArgSelector <: FunctionArguments[?]: Type]: FieldsSubtype = {
      val fields = List.unfold(TypeRepr.of[ArgSelector]) { state =>
        PartialFunction.condOpt(state) {
          case Refinement(parent, name, fieldTpe) => Field(name, fieldTpe) -> parent
        }
      }
      apply(fields)
    }

    final def fromValDefs(valDefs: List[ValDef]): FieldsSubtype = {
      val fields = valDefs.map(vd => Field(vd.name, vd.tpt.tpe))
      apply(fields)
    }

  }

  final case class Field(name: String, tpe: TypeRepr) {

    /**
     * Workaround for Expr.summon failing with eg.:
     *
     * given instance forProducts in object Transformer does not match type Transformer[Inside, Inside2]
     *
     * But `summonInline` will actually succeed in this case but that results in terrible error messages in case
     * of failure and givens summoned with `summonInline` do not retain the exact type of the summoned instance (eg. Transformer.Identity)
     * which makes it impossible to optimize (eg. replacing the runtime instance of Transformer with the transformation itself)
     *
     * TODO: Investigate it further and find what causes that problem.
     *
     * 13.08.2022 update:
     *    This is definitely a compiler bug, `Expr.summon` and `summonInline`
     *    should function the same (https://github.com/lampepfl/dotty/issues/12359).
     *    Not-a-real-workaround: Marking ProductTransformerMacros.transform as `transparent inline` allows for a direct
     *    call to that macro to work. Still doesn't work inside a `given` (be it inline or transparent inline).
     *
     * TODO2: Minimaze the issue and try to open a ticket in the `dotty` repo.
     */
    def transformerTo(that: Field): Expr[Transformer[?, ?]] = {
      (tpe.asType -> that.tpe.asType) match {
        case '[src] -> '[dest] =>
          Implicits.search(TypeRepr.of[Transformer[src, dest]]) match {
            case success: ImplicitSearchSuccess  => success.tree.asExprOf[Transformer[src, dest]]
            case noMatching: NoMatchingImplicits => report.errorAndAbort(noMatching.explanation)
            case diverging: DivergingImplicit    => report.errorAndAbort(diverging.explanation)
            case ambigious: AmbiguousImplicits   => report.errorAndAbort(ambigious.explanation)
            case _: ImplicitSearchFailure        => '{ compiletime.summonInline[Transformer[src, dest]] }
          }
      }
    }
  }

  extension (companion: Suggestion.type) {
    def fromFields(fields: Fields): List[Suggestion] = fields.value.map(f => Suggestion(s"_.${f.name}"))
  }
}
