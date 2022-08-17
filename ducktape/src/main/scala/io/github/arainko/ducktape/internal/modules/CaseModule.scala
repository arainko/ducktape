package io.github.arainko.ducktape.internal.modules

import scala.deriving.*
import scala.quoted.*

private[ducktape] trait CaseModule { self: Module & MirrorModule =>
  import quotes.reflect.*

  sealed trait Cases {
    export byName.get

    val value: List[Case]

    val byName: Map[String, Case] = value.map(c => c.name -> c).toMap
  }

  object Cases {

    def source(using sourceCases: Cases.Source): Cases.Source = sourceCases
    def dest(using destCases: Cases.Dest): Cases.Dest = destCases

    final case class Source(value: List[Case]) extends Cases
    object Source extends CasesCompanion[Source]

    final case class Dest(value: List[Case]) extends Cases
    object Dest extends CasesCompanion[Dest]
  }

  protected trait CasesCompanion[CasesSubtype <: Cases] {
    def apply(cases: List[Case]): CasesSubtype

    final def fromMirror[A: Type](mirror: Expr[Mirror.SumOf[A]]): CasesSubtype = {
      val materializedMirror = MaterializedMirror.createOrAbort(mirror)

      val cases = materializedMirror.mirroredElemLabels
        .zip(materializedMirror.mirroredElemTypes)
        .zipWithIndex
        .map { case name -> tpe -> ordinal => Case(name, tpe, ordinal) }

      apply(cases)
    }
  }

  final case class Case(name: String, tpe: TypeRepr, ordinal: Int) {

    def materializeSingleton: Option[Term] =
      Option.when(tpe.isSingleton) {
        tpe match { case TermRef(a, b) => Ident(TermRef(a, b)) }
      }
  }
}
