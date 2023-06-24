package io.github.arainko.ducktape.internal.modules

import scala.deriving.Mirror
import scala.quoted.*

private[ducktape] sealed trait Cases {
  export byName.get

  val value: List[Case]

  val byName: Map[String, Case] = value.map(c => c.name -> c).toMap
}

private[ducktape] object Cases {
  def source(using sourceCases: Cases.Source): Cases.Source = sourceCases
  def dest(using destCases: Cases.Dest): Cases.Dest = destCases

  final case class Source(value: List[Case]) extends Cases
  object Source extends CasesCompanion[Source]

  final case class Dest(value: List[Case]) extends Cases
  object Dest extends CasesCompanion[Dest]

  sealed abstract class CasesCompanion[CasesSubtype <: Cases] {
    def apply(cases: List[Case]): CasesSubtype

    final def fromMirror[A: Type](mirror: Expr[Mirror.SumOf[A]])(using Quotes): CasesSubtype = {
      val materializedMirror = MaterializedMirror.create(mirror)

      val cases = materializedMirror.mirroredElemLabels
        .zip(materializedMirror.mirroredElemTypes)
        .map(Case.apply)

      apply(cases)
    }
  }
}
