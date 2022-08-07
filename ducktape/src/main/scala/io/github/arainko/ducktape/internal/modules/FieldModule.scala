package io.github.arainko.ducktape.internal.modules

import scala.quoted.*
import scala.deriving.*
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
            // .orElse(derivedTransformer[source, dest])
      }

    private def derivedTransformer[A: Type, B: Type]: Option[Expr[Transformer[A, B]]] =
      mirrorOf[A].zip(mirrorOf[B]).collect {
        case ('{ $src: Mirror.ProductOf[A] }, '{ $dest: Mirror.ProductOf[B] }) =>
          '{ Transformer.forProducts[A, B](using $src, $dest) }
        case ('{ $src: Mirror.SumOf[A] }, '{ $dest: Mirror.SumOf[B] }) =>
          '{ Transformer.forCoproducts[A, B](using $src, $dest) }
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
