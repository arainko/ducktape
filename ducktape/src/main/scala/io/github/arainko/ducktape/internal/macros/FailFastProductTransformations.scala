package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.internal.modules.Field.{ Unwrapped, Wrapped }
import io.github.arainko.ducktape.internal.modules.*

import scala.annotation.*
import scala.deriving.Mirror
import scala.quoted.*
import scala.util.chaining.*
import io.github.arainko.ducktape.Transformer

private[ducktape] object FailFastProductTransformations {
  export fallibleTransformations.{ transform, transformConfigured, via, viaConfigured }

  private val fallibleTransformations = new FallibleProductTransformations[Transformer.FailFast.Support] {
    override protected def createTransformation[F[+x]: Type, Source: Type, Dest: Type](
      F: Expr[Transformer.FailFast.Support[F]],
      sourceValue: Expr[Source],
      fieldsToTransformInto: List[Field],
      unwrappedFieldsFromConfig: List[Field.Unwrapped],
      wrappedFieldsFromConfig: List[Field.Wrapped[F]]
    )(construct: List[Field.Unwrapped] => Expr[Dest])(using Quotes, Fields.Source) = {
      import quotes.reflect.*

      val transformedFields: List[Wrapped[F] | Unwrapped] =
        fieldsToTransformInto.map[Field.Wrapped[F] | Field.Unwrapped] { dest =>
          val source =
            Fields.source
              .get(dest.name)
              .getOrElse(Failure.emit(Failure.NoFieldMapping(dest.name, summon[Type[Source]])))

          source.partialTransformerTo[F, Transformer.FailFast](dest).asExpr match {
            case '{ Transformer.FailFast.partialFromTotal[F, src, dest](using $total, $support) } =>
              val sourceField = sourceValue.accessField(source).asExprOf[src]
              val lifted = LiftTransformation.liftTransformation[src, dest](total, sourceField)
              Field.Unwrapped(dest, lifted)
            case '{ $transformer: Transformer.FailFast[F, src, dest] } =>
              val sourceField = sourceValue.accessField(source).asExprOf[src]
              Field.Wrapped(dest, '{ $transformer.transform($sourceField) })
          }
        }

      val transformedAndConfiguredFields =
        transformedFields
          .prependedAll(unwrappedFieldsFromConfig)
          .prependedAll(wrappedFieldsFromConfig)

      nestFlatMapsAndConstruct[F, Dest](F, transformedAndConfiguredFields, construct)
    }

    private def nestFlatMapsAndConstruct[F[+x]: Type, Dest: Type](
      F: Expr[Transformer.FailFast.Support[F]],
      fields: List[Field.Wrapped[F] | Field.Unwrapped],
      construct: List[Field.Unwrapped] => Expr[Dest]
    )(using Quotes): Expr[F[Dest]] = {
      def recurse(
        leftoverFields: List[Field.Wrapped[F] | Field.Unwrapped],
        collectedUnwrappedFields: List[Field.Unwrapped]
      )(using Quotes): Expr[F[Dest]] =
        leftoverFields match {
          case Field.Wrapped(field, value) :: Nil =>
            value match {
              case '{ $value: F[destField] } =>
                '{
                  $F.map[`destField`, Dest](
                    $value,
                    ${
                      generateLambda[[A] =>> A, destField, Dest](
                        field,
                        unwrappedValue => construct(Field.Unwrapped(field, unwrappedValue) :: collectedUnwrappedFields)
                      )
                    }
                  )
                }
            }

          case Field.Wrapped(field, value) :: next =>
            value match {
              case '{ $value: F[destField] } =>
                '{
                  $F.flatMap[`destField`, Dest](
                    $value,
                    ${
                      generateLambda[F, destField, Dest](
                        field,
                        unwrappedValue => recurse(next, Field.Unwrapped(field, unwrappedValue) :: collectedUnwrappedFields)
                      )
                    }
                  )
                }
            }

          case (f: Field.Unwrapped) :: next =>
            recurse(next, f :: collectedUnwrappedFields)

          case Nil =>
            val constructedValue = construct(collectedUnwrappedFields)
            '{ $F.pure[Dest]($constructedValue) }
        }

      recurse(fields, Nil)
    }

    // this fixes a weird compiler crash where if I use the same name for each of the lambda args the compiler is not able to find a proxy for one of the invocations (?)
    // this probably warrants a crash report?
    @nowarn // todo: use @unchecked?
    private def generateLambda[F[+x]: Type, A: Type, B: Type](field: Field, f: Expr[A] => Expr[F[B]])(using Quotes) = {
      import quotes.reflect.*

      val mtpe = MethodType(List(field.name))(_ => List(TypeRepr.of[A]), _ => TypeRepr.of[F[B]])
      Lambda(
        Symbol.spliceOwner,
        mtpe,
        { case (methSym, (arg1: Term) :: Nil) => f(arg1.asExprOf[A]).asTerm.changeOwner(methSym) }
      ).asExprOf[A => F[B]]
    }
  }
}
