package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.function.FunctionMirror
import io.github.arainko.ducktape.internal.modules.*
import io.github.arainko.ducktape.internal.util.NonEmptyList
import io.github.arainko.ducktape.{ BuilderConfig, FallibleBuilderConfig }

import scala.annotation.tailrec
import scala.deriving.Mirror
import scala.quoted.*
import scala.util.chaining.*
import io.github.arainko.ducktape.Transformer
import io.github.arainko.ducktape.fallible.FallibleTransformer
import io.github.arainko.ducktape.fallible.Mode

private[ducktape] object AccumulatingProductTransformations {
  export fallibleTransformations.{ transform, transformConfigured, via, viaConfigured }

  private val fallibleTransformations = new FallibleProductTransformations[Mode.Accumulating] {
    override protected def createTransformation[F[+x]: Type, Source: Type, Dest: Type](
      F: Expr[Mode.Accumulating[F]],
      sourceValue: Expr[Source],
      fieldsToTransformInto: List[Field],
      unwrappedFieldsFromConfig: List[Field.Unwrapped],
      wrappedFieldsFromConfig: List[Field.Wrapped[F]]
    )(construct: List[Field.Unwrapped] => Expr[Dest])(using Quotes, Fields.Source) = {
      import quotes.reflect.*

      // Ideally .partition would work but if I deconstruct these two into tuples based on the subtype both of their parts are inferred as the union
      // anyway, hence this thing:
      val wrappedFields = List.newBuilder[Field.Wrapped[F]].addAll(wrappedFieldsFromConfig)
      val unwrappedFields = List.newBuilder[Field.Unwrapped].addAll(unwrappedFieldsFromConfig)

      fieldsToTransformInto.foreach { dest =>
        val source =
          Fields.source
            .get(dest.name)
            .getOrElse(Failure.emit(Failure.NoFieldMapping(dest.name, summon[Type[Source]])))

        source.partialTransformerTo[F, FallibleTransformer](dest).asExpr match {
          case '{ FallibleTransformer.partialFromTotal[F, src, dest](using $total, $support) } =>
            val sourceField = sourceValue.accessField(source).asExprOf[src]
            val transformed = LiftTransformation.liftTransformation[src, dest](total, sourceField)
            unwrappedFields += Field.Unwrapped(dest, transformed)
          case '{ $transformer: FallibleTransformer[F, src, dest] } =>
            val sourceField = sourceValue.accessField(source).asExprOf[src]
            wrappedFields += Field.Wrapped(dest, '{ $transformer.transform($sourceField) })
        }
      }

      wrappedFields
        .mapResult(NonEmptyList.fromList)
        .result()
        .map { transformedFields => ZippedProduct.zipAndConstruct(F, transformedFields, unwrappedFields.result())(construct) }
        .getOrElse('{ $F.pure(${ construct(unwrappedFields.result()) }) })
    }
  }

}
