package io.github.arainko.ducktape

import scala.quoted.*
import io.github.arainko.ducktape.internal.modules.*
import scala.collection.IterableFactory
import scala.collection.Factory

enum Plan {
  def sourceTpe: Type[?]
  def destTpe: Type[?]

  case Upcast(tpe: Type[?])
  case FieldAccess(name: String, fieldTpe: Type[?], plan: Plan)
  case CaseBranch(tpe: Type[?], plan: Plan)
  case ProductTransformation(fieldPlans: Map[String, Plan])
  case CoproductTransformation(casePlans: Map[String, Plan])
  case BetweenOptionsTransformation(plan: Plan)
  case WrapInOptionTransformation(dest: Plan)
  case BetweenCollectionsTransformation(dest: Plan)
  case SingletonTransformation(tpe: Type[?], expr: Expr[Any])
  case TransformerTransformation(source: Type[?], dest: Type[?], transformation: Expr[Any])
}
