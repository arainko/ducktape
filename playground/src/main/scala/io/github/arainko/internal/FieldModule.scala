package io.github.arainko.internal

import scala.quoted.*
import scala.deriving.*

trait FieldModule { self: Module & MirrorModule =>
  import quotes.reflect.*

  case class Field(name: String, tpe: TypeRepr)

  object Field {
    def fromMirror[A](mirror: DerivingMirror.ProductOf[A]): List[Field] = {
      val materializedMirror = Mirror(mirror).getOrElse(report.errorAndAbort("### Failed to materialize a mirror ###"))

      materializedMirror.mirroredElemLabels
        .zip(materializedMirror.mirroredElemTypes)
        .map(Field.apply)
    }
  }

  case class Case(name: String, tpe: TypeRepr, ordinal: Int) {

    def materializeSingleton: Option[Term] =
      tpe.asType match {
        case '[caseTpe] => Type.valueOfConstant[caseTpe]
          Expr.summon[ValueOf[caseTpe]].map { case '{ $valueOf } => '{ $valueOf.value }.asTerm }
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
