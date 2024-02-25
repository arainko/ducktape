package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.Mode

import scala.annotation.*
import scala.quoted.*
import scala.util.chaining.*

private[ducktape] object ProductBinder {

  def nestFlatMapsAndConstruct[F[+x]: Type, Dest: Type](
    F: Expr[Mode.FailFast[F]],
    unwrappedFields: List[ProductZipper.Field.Unwrapped],
    wrappedFields: List[ProductZipper.Field.Wrapped[F]],
    construct: ProductConstructor
  )(using Quotes): Expr[F[Dest]] = {

    def recurse(
      leftoverFields: List[ProductZipper.Field.Wrapped[F]],
      collectedUnwrappedFields: List[ProductZipper.Field.Unwrapped]
    )(using Quotes): Expr[F[Dest]] =
      leftoverFields match {
        case ProductZipper.Field.Wrapped(field, wrappedValue) :: Nil =>
          field.tpe match {
            case '[destField] =>
              val value = wrappedValue.asExprOf[F[destField]]
              '{
                $F.map[`destField`, Dest](
                  $value,
                  ${
                    generateLambda[[A] =>> A, destField, Dest](
                      field,
                      unwrappedValue =>
                        //TODO: Come back to this at some point, owners of the collected unwrapped defs need to be aligned to Symbol.spliceOwner
                        // so there are Exprs being constructed in a wrong way somewhere (this only occurts when falling back to the non-fallible PlanInterpreter)
                        val fields =
                          ((field.name -> unwrappedValue) :: collectedUnwrappedFields.map(f => f.field.name -> alignOwner(f.value))).toMap
                        construct(fields).asExprOf[Dest]
                    )
                  }
                )
              }
          }

        case ProductZipper.Field.Wrapped(field, wrappedValue) :: next =>
          field.tpe match {
            case '[destField] =>
              val value = wrappedValue.asExprOf[F[destField]]
              '{
                $F.flatMap[`destField`, Dest](
                  $value,
                  ${
                    generateLambda[F, destField, Dest](
                      field,
                      q ?=> unwrappedValue =>
                        recurse(next, ProductZipper.Field.Unwrapped(field, unwrappedValue) :: collectedUnwrappedFields)(using q)
                    )
                  }
                )
              }
          }

        case Nil =>
          val fields = collectedUnwrappedFields.map(f => f.field.name -> f.value).toMap
          def constructedValue(using Quotes) = construct(fields).asExprOf[Dest]
          '{ $F.pure[Dest]($constructedValue) }
      }

    recurse(wrappedFields, unwrappedFields)
  }

  private def alignOwner(expr: Expr[Any])(using Quotes) = {
    import quotes.reflect.*
    expr.asTerm.changeOwner(Symbol.spliceOwner).asExpr
  }

  // this fixes a weird compiler crash where if I use the same name for each of the lambda args the compiler is not able to find a proxy for one of the invocations (?)
  // this probably warrants a crash report?
  @nowarn // todo: use @unchecked?
  private def generateLambda[F[+x]: Type, A: Type, B: Type](
    field: ProductZipper.Field,
    f: (Quotes) ?=> Expr[A] => Expr[F[B]]
  )(using Quotes) = {
    import quotes.reflect.*

    val mtpe = MethodType(List(field.name))(_ => List(TypeRepr.of[A]), _ => TypeRepr.of[F[B]])
    Lambda(
      Symbol.spliceOwner,
      mtpe,
      {
        case (methSym, (arg1: Term) :: Nil) =>
          given Quotes = methSym.asQuotes
          f(arg1.asExprOf[A]).asTerm
      }
    ).asExprOf[A => F[B]]
  }
}
