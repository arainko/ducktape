package io.github.arainko.ducktape.internal.modules

import scala.quoted.*
import scala.deriving.Mirror
import io.github.arainko.ducktape.internal.modules.mirror.MaterializedMirror

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

  sealed abstract class CasesCompanion[CasesSubtype <: Cases] {
    def apply(cases: List[Case]): CasesSubtype

    final def fromMirror[A: Type](mirror: Expr[Mirror.SumOf[A]])(using Quotes): CasesSubtype = {
      val materializedMirror = MaterializedMirror.createOrAbort(mirror)

      val cases = materializedMirror.mirroredElemLabels
        .zip(materializedMirror.mirroredElemTypes)
        .zipWithIndex
        .map { case name -> tpe -> ordinal => Case(name, tpe.asType, ordinal) }

      apply(cases)
    }
  }
}
