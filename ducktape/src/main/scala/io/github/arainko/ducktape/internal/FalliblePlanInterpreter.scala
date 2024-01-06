// package io.github.arainko.ducktape.internal

// import scala.quoted.*
// import io.github.arainko.ducktape.Mode
// import scala.annotation.nowarn

// object FalliblePlanInterpreter {
//   def run[F[+x], A](
//     plan: Plan[Nothing],
//     sourceValue: Expr[A],
//     mode: Expr[Mode.Accumulating[F]]
//   )(using Quotes): Expr[F[Any]] = ???

//   @nowarn private def recurse[F[+x]: Type, A: Type](
//     plan: Plan[Nothing],
//     value: Expr[Any],
//     F: Expr[Mode.Accumulating[F]]
//   )(using toplevelValue: Expr[A])(using Quotes): Expr[F[Any]] = {
//     plan match {
//       case Plan.Upcast(_, _) => '{ $F.pure($value) }

//       case Plan.Configured(_, _,  config) => ???

//       case Plan.BetweenProducts(source, dest, fieldPlans) => ???

//       case Plan.BetweenCoproducts(source, dest, casePlans) => ???

//       case Plan.BetweenProductFunction(source, dest, argPlans) => ???

//       case Plan.BetweenOptions(source, dest, plan) =>
//         (source.paramStruct.tpe, dest.paramStruct.tpe) match {
//           case '[src] -> '[dest] =>
//             val source = value.asExprOf[Option[src]]
//             '{
//               $source match
//                 case None        => $F.pure(None)
//                 case Some(value) => $F.map(${ recurse(plan, 'value, F) }, Some.apply)
//             }
//         }

//       case Plan.BetweenNonOptionOption(source, dest, plan) =>
//         source.tpe match {
//           case '[src] =>
//             val source = value.asExprOf[src]
//             '{ $F.map(${ recurse(plan, source, F) }, Some.apply) }
//         }

//       case Plan.BetweenCollections(source, dest, plan) => ???

//       case Plan.BetweenSingletons(source, dest) => '{ $F.pure(${ dest.value }) }

//       case Plan.BetweenWrappedUnwrapped(source, dest, fieldName) => ???

//       case Plan.BetweenUnwrappedWrapped(source, dest) => ???

//       case Plan.UserDefined(source, dest, transformer) => ???

//       case Plan.Derived(source, dest, transformer) => ???
//     }
//   }
// }
