package io.github.arainko.ducktape.internal.modules

import scala.quoted.*
import scala.deriving.*
import io.github.arainko.ducktape.Transformer
import io.github.arainko.ducktape.function.NamedArgument
import scala.compiletime.*

private[internal] trait FieldModule { self: Module & MirrorModule =>
  import quotes.reflect.*

  case class Field(name: String, tpe: TypeRepr) {

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

  object Field {
    def fromMirror[A: Type](mirror: Expr[Mirror.ProductOf[A]]): List[Field] = {
      val materializedMirror = MaterializedMirror.createOrAbort(mirror)

      materializedMirror.mirroredElemLabels
        .zip(materializedMirror.mirroredElemTypes)
        .map(Field.apply)
    }

    def fromNamedArguments[NamedArgs <: Tuple: Type]: List[Field] =
      Type.of[NamedArgs] match {
        case '[EmptyTuple] => List.empty
        case '[NamedArgument[name, tpe] *: tail] =>
          val name = Type.valueOfConstant[name].getOrElse(report.errorAndAbort("Not a constant named arg name"))
          val field = Field(name, TypeRepr.of[tpe])
          field :: fromNamedArguments[tail]
      }

    def fromValDef(valDef: ValDef): Field = Field(valDef.name, valDef.tpt.tpe)
  }

  case class Case(name: String, tpe: TypeRepr, ordinal: Int) {

    def materializeSingleton: Option[Term] =
      Option.when(tpe.isSingleton) {
        tpe match { case TermRef(a, b) => Ident(TermRef(a, b)) }
      }
  }

  object Case {
    def fromMirror[A: Type](mirror: Expr[Mirror.SumOf[A]]): List[Case] = {
      val materializedMirror = MaterializedMirror.createOrAbort(mirror)

      materializedMirror.mirroredElemLabels
        .zip(materializedMirror.mirroredElemTypes)
        .zipWithIndex
        .map { case name -> tpe -> ordinal => Case(name, tpe, ordinal) }
    }
  }

}
