package io.github.arainko.ducktape.internal.modules

import scala.quoted.*
import scala.deriving.*
import io.github.arainko.ducktape.Transformer
import io.github.arainko.ducktape.function.NamedArgument
import scala.compiletime.*

private[internal] trait FieldModule { self: Module & MirrorModule =>
  import quotes.reflect.*

  case class Field(name: String, tpe: TypeRepr) {
    def transformerTo(that: Field): Option[Expr[Transformer[?, ?]]] =
      Implicits.search(TypeRepr.of[Transformer].appliedTo(List(tpe, that.tpe))) match {
        case iss: ImplicitSearchSuccess => Some(iss.tree.asExprOf[Transformer[?, ?]])
        case iss: ImplicitSearchFailure => None
      }
  }

  object Field {
    def fromMirror[A](mirror: Expr[Mirror.ProductOf[A]]): List[Field] = {
      val materializedMirror =
        MaterializedMirror(mirror).getOrElse(report.errorAndAbort("### Failed to materialize a mirror ###"))

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
    def fromMirror[A](mirror: Expr[Mirror.SumOf[A]]): List[Case] = {
      val materializedMirror =
        MaterializedMirror(mirror).getOrElse(report.errorAndAbort("### Failed to materialize a mirror ###"))

      materializedMirror.mirroredElemLabels
        .zip(materializedMirror.mirroredElemTypes)
        .zipWithIndex
        .map { case name -> tpe -> ordinal => Case(name, tpe, ordinal) }
    }
  }

}
