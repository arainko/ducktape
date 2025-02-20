package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.*

import scala.collection.Factory
import scala.collection.immutable.VectorMap
import scala.quoted.*
import scala.reflect.TypeTest

private[ducktape] object Fallible
private[ducktape] type Fallible = Fallible.type

private[ducktape] object Erroneous
private[ducktape] type Erroneous = Erroneous.type

private[ducktape] sealed trait Plan[+E <: Erroneous, +F <: Fallible] {
  def source: Structure

  def dest: Structure

  final def sourcePath: Path = source.path

  final def destPath: Path = dest.path

  final def narrow[A <: Plan[Erroneous, Fallible]](using tt: TypeTest[Plan[Erroneous, Fallible], A]): Option[A] =
    tt.unapply(this)

  final def configureAll[FF >: F <: Fallible](
    configs: List[Configuration.Instruction[FF]]
  )(using Quotes, Context.Of[FF]): Plan.Reconfigured[FF] =
    PlanConfigurer.run(this, configs)

  final def refine: Either[NonEmptyList[Plan.Error], Plan[Nothing, F]] = ErroneousnessRefiner.run(this)
}

private[ducktape] object Plan {

  case class Upcast(
    source: Structure,
    dest: Structure,
    private val alternative: () => Plan[Erroneous, Nothing]
  ) extends Plan[Nothing, Nothing] {
    lazy val alt: Plan[Erroneous, Nothing] = alternative()

    inline def update(inline f: Plan[Erroneous, Nothing] => Plan[Erroneous, Nothing]): Upcast =
      copy(alternative = () => f(alt))
  }

  case class UserDefined[+F <: Fallible](
    source: Structure,
    dest: Structure,
    transformer: Summoner.UserDefined[F]
  ) extends Plan[Nothing, F]

  case class Derived[+F <: Fallible](
    source: Structure,
    dest: Structure,
    transformer: Summoner.Derived[F]
  ) extends Plan[Nothing, F]

  case class Configured[+F <: Fallible] private (
    source: Structure,
    dest: Structure,
    config: Configuration[F],
    span: Span
  ) extends Plan[Nothing, F]

  case class BetweenProductFunction[+E <: Erroneous, +F <: Fallible](
    source: Structure.Product,
    dest: Structure.Function,
    argPlans: VectorMap[String, FieldPlan[E, F]]
  ) extends Plan[E, F] {
    inline def updateEach[EE >: E <: Erroneous, FF >: F <: Fallible](
      inline f: Plan[E, F] => Plan[EE, FF]
    ): BetweenProductFunction[EE, FF] =
      copy(argPlans = argPlans.updateEach(_.update(f)))

    inline def updateOrElse[FF >: F <: Fallible](argName: String)(
      inline f: Plan[E, F] => Plan[Erroneous, FF],
      inline alt: Plan.Error
    ): BetweenProductFunction[Erroneous, FF] = {
      copy(argPlans = argPlans.updatedByName(argName)(f, alt))
    }
  }

  case class BetweenTupleFunction[+E <: Erroneous, +F <: Fallible](
    source: Structure.Tuple,
    dest: Structure.Function,
    argPlans: VectorMap[String, Plan[E, F]]
  ) extends Plan[E, F] {
    inline def updateEach[EE >: E <: Erroneous, FF >: F <: Fallible](
      inline f: Plan[E, F] => Plan[EE, FF]
    ): BetweenTupleFunction[EE, FF] =
      copy(argPlans = argPlans.updateEach(f))

    inline def updateByNameOrElse[EE >: E <: Erroneous, FF >: F <: Fallible](argName: String)(
      inline f: Plan[E, F] => Plan[EE, FF],
      inline alt: Plan.Error
    ): Plan[Erroneous, FF] =
      argPlans
        .updateByName(argName)(f, plans => copy(argPlans = plans))
        .getOrElse(alt)

    inline def updateByIndexOrElse[EE >: E <: Erroneous, FF >: F <: Fallible](argIdx: Int)(
      inline f: Plan[E, F] => Plan[EE, FF],
      inline alt: Plan.Error
    ): Plan[Erroneous, FF] =
      argPlans
        .updateByIndex(argIdx)(f, plans => copy(argPlans = plans))
        .getOrElse(alt)
  }

  case class BetweenUnwrappedWrapped(
    source: Structure,
    dest: Structure.ValueClass
  ) extends Plan[Nothing, Nothing]

  case class BetweenWrappedUnwrapped(
    source: Structure.ValueClass,
    dest: Structure,
    fieldName: String
  ) extends Plan[Nothing, Nothing]

  case class BetweenFallibleNonFallible[+E <: Erroneous](
    source: Structure.Wrapped[?],
    dest: Structure,
    plan: Plan[E, Nothing]
  ) extends Plan[E, Fallible] {
    inline def update[EE >: E <: Erroneous](inline f: Plan[E, Nothing] => Plan[EE, Nothing]): BetweenFallibleNonFallible[EE] =
      copy(plan = f(plan))
  }

  case class BetweenFallibles[+E <: Erroneous](
    source: Structure.Wrapped[?],
    dest: Structure,
    mode: TransformationMode.FailFast[?],
    plan: Plan[E, Fallible]
  ) extends Plan[E, Fallible] {
    inline def update[EE >: E <: Erroneous](inline f: Plan[E, Fallible] => Plan[EE, Fallible]): BetweenFallibles[EE] =
      copy(plan = f(plan))
  }

  case class BetweenSingletons(
    source: Structure.Singleton,
    dest: Structure.Singleton
  ) extends Plan[Nothing, Nothing]

  case class BetweenProducts[+E <: Erroneous, +F <: Fallible](
    source: Structure.Product,
    dest: Structure.Product,
    fieldPlans: VectorMap[String, FieldPlan[E, F]]
  ) extends Plan[E, F] {
    inline def updateEach[EE >: E <: Erroneous, FF >: F <: Fallible](
      inline f: Plan[E, F] => Plan[EE, FF]
    ): BetweenProducts[EE, FF] =
      copy(fieldPlans = fieldPlans.updateEach(_.update(f)))

    inline def updateOrElse[FF >: F <: Fallible](name: String)(
      inline f: Plan[E, F] => Plan[Erroneous, FF],
      inline alt: Plan.Error
    ): BetweenProducts[Erroneous, FF] =
      copy(fieldPlans = fieldPlans.updatedByName(name)(f, alt))
  }

  case class BetweenProductTuple[+E <: Erroneous, +F <: Fallible](
    source: Structure.Product,
    dest: Structure.Tuple,
    plans: Vector[FieldPlan[E, F]]
  ) extends Plan[E, F] {
    inline def updateEach[EE >: E <: Erroneous, FF >: F <: Fallible](
      inline f: Plan[E, F] => Plan[EE, FF]
    ): BetweenProductTuple[EE, FF] =
      copy(plans = plans.map(_.update(f)))

    inline def updateByNameOrElse[FF >: F <: Fallible](name: String)(
      inline f: Plan[E, F] => Plan[Erroneous, FF],
      inline alt: Plan.Error
    ): Plan[Erroneous, FF] =
      plans.zipWithIndex.collectFirst(planWithNameAtIndex(name, f)).getOrElse(alt)

    private def planWithNameAtIndex[FF >: F <: Fallible](
      name: String,
      f: Plan[E, F] => Plan[Erroneous, FF]
    ): PartialFunction[(FieldPlan[E, F], Int), BetweenProductTuple[Erroneous, FF]] = {
      case (plan @ FieldPlan(`name`, _), index) =>
        copy(plans = plans.updated(index, plan.update(f)))
    }

    inline def updateByIndexOrElse[FF >: F <: Fallible](argIdx: Int)(
      inline f: Plan[E, F] => Plan[Erroneous, FF],
      inline alt: Plan.Error
    ): Plan[Erroneous, FF] =
      plans
        .updateByIndex(argIdx)(_.update(f), plans => copy(plans = plans))
        .getOrElse(alt)
  }

  case class BetweenTupleProduct[+E <: Erroneous, +F <: Fallible](
    source: Structure.Tuple,
    dest: Structure.Product,
    plans: VectorMap[String, Plan[E, F]]
  ) extends Plan[E, F] {
    inline def updateEach[EE >: E <: Erroneous, FF >: F <: Fallible](
      inline f: Plan[E, F] => Plan[EE, FF]
    ): BetweenTupleProduct[EE, FF] =
      copy(plans = plans.updateEach(f))

    inline def updateByNameOrElse[FF >: F <: Fallible](argName: String)(
      inline f: Plan[E, F] => Plan[Erroneous, FF],
      inline alt: Plan.Error
    ): Plan[Erroneous, FF] =
      plans.updateByName(argName)(f, plans => copy(plans = plans)).getOrElse(alt)

    inline def updateByIndexOrElse[FF >: F <: Fallible](argIdx: Int)(
      inline f: Plan[E, F] => Plan[Erroneous, FF],
      inline alt: Plan.Error
    ): Plan[Erroneous, FF] =
      plans.updateByIndex(argIdx)(f, plans => copy(plans = plans)).getOrElse(alt)
  }

  case class BetweenTuples[+E <: Erroneous, +F <: Fallible](
    source: Structure.Tuple,
    dest: Structure.Tuple,
    plans: Vector[Plan[E, F]]
  ) extends Plan[E, F] {
    inline def updateEach[EE >: E <: Erroneous, FF >: F <: Fallible](
      inline f: Plan[E, F] => Plan[EE, FF]
    ): BetweenTuples[EE, FF] =
      copy(plans = plans.map(f))

    inline def updateByIndexOrElse[FF >: F <: Fallible](argIdx: Int)(
      inline f: Plan[E, F] => Plan[Erroneous, FF],
      inline alt: Plan.Error
    ): Plan[Erroneous, FF] =
      plans.updateByIndex(argIdx)(f, plans => copy(plans = plans)).getOrElse(alt)
  }

  case class BetweenCoproducts[+E <: Erroneous, +F <: Fallible](
    source: Structure.Coproduct,
    dest: Structure.Coproduct,
    casePlans: Vector[Plan[E, F]]
  ) extends Plan[E, F] {
    inline def updateEach[EE >: E <: Erroneous, FF >: F <: Fallible](
      inline f: Plan[E, F] => Plan[EE, FF]
    ): BetweenCoproducts[EE, FF] = copy(casePlans = casePlans.map(f))
  }

  case class BetweenOptions[+E <: Erroneous, +F <: Fallible](
    source: Structure.Optional,
    dest: Structure.Optional,
    plan: Plan[E, F]
  ) extends Plan[E, F] {
    inline def update[EE >: E <: Erroneous, FF >: F <: Fallible](inline f: Plan[E, F] => Plan[EE, FF]) =
      copy(plan = f(plan))
  }

  case class BetweenNonOptionOption[+E <: Erroneous, +F <: Fallible](
    source: Structure,
    dest: Structure.Optional,
    plan: Plan[E, F]
  ) extends Plan[E, F] {
    inline def update[EE >: E <: Erroneous, FF >: F <: Fallible](
      inline f: Plan[E, F] => Plan[EE, FF]
    ): BetweenNonOptionOption[EE, FF] =
      copy(plan = f(plan))
  }

  case class BetweenCollections[+E <: Erroneous, +F <: Fallible](
    source: Structure.Collection,
    dest: Structure.Collection,
    factory: Expr[Factory[?, ?]],
    plan: Plan[E, F]
  ) extends Plan[E, F] {
    inline def update[EE >: E <: Erroneous, FF >: F <: Fallible](
      inline f: Plan[E, F] => Plan[EE, FF]
    ): BetweenCollections[EE, FF] =
      copy(plan = f(plan))
  }

  case class Error(
    source: Structure,
    dest: Structure,
    message: ErrorMessage,
    suppressed: Option[Plan.Error]
  ) extends Plan[Erroneous, Nothing]

  object Error {
    def from(plan: Plan[Erroneous, Fallible], message: ErrorMessage, suppressed: Option[Plan.Error]): Plan.Error =
      Plan.Error(
        plan.source,
        plan.dest,
        message,
        suppressed
      )
  }

  object Configured {
    def from[F <: Fallible](
      plan: Plan[Erroneous, F],
      conf: Configuration[F],
      instruction: Configuration.Instruction[F]
    )(using
      Quotes,
      Context
    ): Plan.Configured[F] =
      conf.destTpe match {
        case '[confTpe] =>
          // if side is Source then we're operating on either a missing Source case or an override of that,
          // which means the dest types need to be narrowed down to the exact type of this config
          val dest = if instruction.side.isSource then Structure.Lazy.of[confTpe](plan.dest.path) else plan.dest
          Plan.Configured(plan.source, dest, conf, instruction.span)
      }
  }

  given debug: Debug[Plan[Erroneous, Fallible]] = Debug.derived

  final case class Reconfigured[+F <: Fallible](
    errors: List[Plan.Error],
    successes: List[(Path, Side)],
    warnings: List[ConfigWarning],
    result: Plan[Erroneous, F]
  )

  object Reconfigured {
    given debug: Debug[Reconfigured[Fallible]] = Debug.derived
  }

  extension [K, V](self: VectorMap[K, V]) {
    private inline def updateEach[A](inline f: V => A): VectorMap[K, A] = self.transform((_, v) => f(v))
  }

  extension [E <: Erroneous, F <: Fallible](self: VectorMap[String, FieldPlan[E, F]]) {
    private inline def updatedByName[FF >: F <: Fallible, A](
      name: String
    )(inline f: Plan[E, F] => Plan[Erroneous, FF], inline alt: Plan.Error): VectorMap[String, FieldPlan[Erroneous, FF]] =
      if self.isDefinedAt(name) then self.updated(name, self(name).update(f))
      else self.updated(name, FieldPlan.empty(alt))
  }

  extension [E <: Erroneous, F <: Fallible](self: VectorMap[String, Plan[E, F]]) {
    private inline def updateByName[FF >: F <: Fallible, A](
      name: String
    )(inline f: Plan[E, F] => Plan[Erroneous, FF], inline rebuild: VectorMap[String, Plan[Erroneous, FF]] => A): A | None =
      if self.isDefinedAt(name) then rebuild(self.updated(name, f(self(name)))) else None

    private inline def updateByIndex[FF >: F <: Fallible, A](
      idx: Int
    )(inline f: Plan[E, F] => Plan[Erroneous, FF], inline rebuild: VectorMap[String, Plan[Erroneous, FF]] => A): A | None = {
      val asVector = self.toVector
      if asVector.isDefinedAt(idx) then
        val (name, fieldPlan) = asVector(idx)
        rebuild(self.updated(name, f(fieldPlan)))
      else None
    }
  }

  extension [A](self: Vector[A]) {
    private inline def updateByIndex[B >: A, C](
      idx: Int
    )(inline f: A => B, inline rebuild: Vector[B] => C): C | None =
      if self.isDefinedAt(idx) then rebuild(self.updated(idx, f(self(idx)))) else None
  }
}

private[ducktape] case class FieldPlan[+E <: Erroneous, +F <: Fallible](sourceField: String | None.type, plan: Plan[E, F]) {
  inline def update[EE <: Erroneous, FF <: Fallible](inline f: Plan[E, F] => Plan[EE, FF]): FieldPlan[EE, FF] =
    this.copy(plan = f(this.plan))
}

private[ducktape] object FieldPlan {
  def empty[E <: Erroneous, F <: Fallible](plan: Plan[E, F]): FieldPlan[E, F] = FieldPlan(None, plan)
}
