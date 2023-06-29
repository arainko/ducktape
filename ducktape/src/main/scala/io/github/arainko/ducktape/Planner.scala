package io.github.arainko.ducktape

import scala.quoted.*

object Planner {
  import Structure.*

  inline def print[A, B] = ${ printMacro[A, B] }

  def printMacro[A: Type, B: Type](using Quotes): Expr[Unit] = {
    val plan = createPlan[A, B]
    quotes.reflect.report.info(plan.toString())
    '{}
  }

  def createPlan[Source: Type, Dest: Type](using Quotes): Plan = {
    val src = Structure.of[Source]
    val dest = Structure.of[Dest]
    recurse(src, dest)
  }

  private def recurse(source: Structure, dest: Structure)(using Quotes): Plan =
    (source.force -> dest.force) match {
      case (source, dest) if source.typeRepr <:< dest.typeRepr => 
        Plan.Upcast(dest.tpe)

      case Structure('[Option[srcTpe]], srcName) -> Structure('[Option[destTpe]], destName) => 
        Plan.BetweenOptionsTransformation(createPlan[srcTpe, destTpe])

      case Structure('[a], _) -> Structure('[Option[destTpe]], destName) => 
        Plan.WrapInOptionTransformation(createPlan[a, destTpe])

      case Structure(source @ '[Iterable[srcTpe]], srcName) -> Structure(dest @ '[Iterable[destTpe]], destName) =>
        Plan.BetweenCollectionsTransformation(createPlan[srcTpe, destTpe])

      case (source: Product, dest: Product) => 
        val fieldPlans = dest.fields.map { (destField, destFieldStruct) => 
          val sourceFieldStruct = source.fields(destField)
          destField -> Plan.FieldAccess(destField, sourceFieldStruct.tpe, recurse(sourceFieldStruct, destFieldStruct))
        }
        Plan.ProductTransformation(fieldPlans)

      case (source: Coproduct, dest: Coproduct) => 
        val casePlans = source.children.map { (sourceName, sourceCaseStruct) =>
          val destCaseStruct = dest.children(sourceName)
          sourceName -> Plan.CaseBranch(sourceCaseStruct.tpe, recurse(sourceCaseStruct, destCaseStruct))
        }
        Plan.CoproductTransformation(casePlans)
        
      case (source: Structure.Singleton, dest: Structure.Singleton) if source.name == dest.name => 
        Plan.SingletonTransformation(dest.tpe, dest.value)

      case (source: Ordinary, dest: Ordinary) => ???
      case other => ???
    }
}
