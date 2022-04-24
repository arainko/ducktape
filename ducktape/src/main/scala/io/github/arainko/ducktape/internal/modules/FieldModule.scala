package io.github.arainko.ducktape.internal.modules

import scala.quoted.*
import scala.deriving.Mirror as DerMirror
import io.github.arainko.ducktape.Transformer
import io.github.arainko.ducktape.function.NamedArgument

private[internal] trait FieldModule { self: Module & MirrorModule =>
  import quotes.reflect.*

  case class Field(name: String, tpe: TypeRepr) {
    def transformerTo(that: Field): Option[Expr[Transformer[?, ?]]] =
      (tpe.asType, that.tpe.asType) match {
        case ('[source], '[dest]) =>
          Expr
            .summon[Transformer[source, dest]]
            .orElse(derivedTransformer[source, dest])
      }

    private def derivedTransformer[A: Type, B: Type]: Option[Expr[Transformer[A, B]]] =
      DerivingMirror
        .of[A]
        .zip(DerivingMirror.of[B])
        .map {
          case ('{ $src: deriving.Mirror.Of[A] }, '{ $dest: deriving.Mirror.Of[B] }) =>
            '{ Transformer.derived[A, B](using $src, $dest) }
        }
  }

  object Field {
    def fromMirror[A](mirror: DerivingMirror.ProductOf[A]): List[Field] = {
      val materializedMirror = Mirror(mirror).getOrElse(report.errorAndAbort("### Failed to materialize a mirror ###"))

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
    def fromMirror[A](mirror: DerivingMirror.SumOf[A]): List[Case] = {
      val materializedMirror = Mirror(mirror).getOrElse(report.errorAndAbort("### Failed to materialize a mirror ###"))

      materializedMirror.mirroredElemLabels
        .zip(materializedMirror.mirroredElemTypes)
        .zipWithIndex
        .map { case ((name, tpe), ordinal) => Case(name, tpe, ordinal) }
    }
  }

}
