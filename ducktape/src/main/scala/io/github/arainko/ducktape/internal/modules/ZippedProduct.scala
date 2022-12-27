package io.github.arainko.ducktape.internal.modules

import io.github.arainko.ducktape.Transformer
import io.github.arainko.ducktape.internal.macros.*
import io.github.arainko.ducktape.internal.modules.Field.Unwrapped
import io.github.arainko.ducktape.internal.util.*

import scala.annotation.tailrec
import scala.quoted.*

object ZippedProduct {

  /**
   * 'Zips' a product using Transformer.Accumulating.Support[F]#product into a nested Tuple2 then
   * unpacks that said tuple and allows the caller to use the unpacked fields to construct a tree of type Dest.
   *
   * @param wrappedFields fields wrapped in F
   * @param unwrappedFields fields that are not wrapped in F (will not be a part of the zipped tuple)
   * @param construct function that allows the caller to create trees with the unpacked values
   */
  def zipAndConstruct[F[+x]: Type, Dest: Type](
    F: Expr[Transformer.Accumulating.Support[F]],
    wrappedFields: NonEmptyList[Field.Wrapped[F]],
    unwrappedFields: List[Field.Unwrapped]
  )(construct: List[Field.Unwrapped] => Expr[Dest])(using Quotes): Expr[F[Dest]] = {
    zipFields[F](F, wrappedFields) match {
      case '{ $zipped: F[a] } =>
        '{
          $F.map(
            $zipped,
            value => ${ unzipAndConstruct[Dest](wrappedFields, unwrappedFields, 'value, construct) }
          )
        }
    }
  }

  private def zipFields[F[+x]: Type](
    F: Expr[Transformer.Accumulating.Support[F]],
    wrappedFields: NonEmptyList[Field.Wrapped[F]]
  )(using Quotes): Expr[F[Any]] =
    wrappedFields.map(_.value).reduceLeft { (accumulated, current) =>
      (accumulated -> current) match {
        case '{ $accumulated: F[a] } -> '{ $current: F[b] } =>
          '{ $F.product[`a`, `b`]($accumulated, $current) }
      }
    }

  private def unzipAndConstruct[Dest: Type](
    wrappedFields: NonEmptyList[Field.Wrapped[?]],
    unwrappedFields: List[Field.Unwrapped],
    nestedPairs: Expr[Any],
    construct: List[Field.Unwrapped] => Expr[Dest]
  )(using Quotes) = {
    import quotes.reflect.*

    ZippedProduct.unzip(nestedPairs, wrappedFields) match {
      case (bind: Bind, unzippedFields) =>
        Match(
          nestedPairs.asTerm,
          CaseDef(
            bind,
            None,
            construct(unzippedFields ::: unwrappedFields).asTerm
          ) :: Nil
        ).asExprOf[Dest]

      case (pattern: Unapply, unzippedFields) =>
        // workaround for https://github.com/lampepfl/dotty/issues/16784
        val matchErrorBind = Symbol.newBind(Symbol.spliceOwner, "x", Flags.EmptyFlags, TypeRepr.of[Any])
        val wronglyMatchedReference = Ref(matchErrorBind).asExpr
        val matchErrorCase =
          CaseDef(Bind(matchErrorBind, Wildcard()), None, '{ throw new MatchError($wronglyMatchedReference) }.asTerm)

        Match(
          nestedPairs.asTerm,
          CaseDef(
            pattern,
            None,
            construct(unzippedFields ::: unwrappedFields).asTerm
          ) :: matchErrorCase :: Nil
        ).asExprOf[Dest]
    }
  }

  /**
   * Imagine you have a value of type: Tuple2[Tuple2[Tuple2[Int, Double], Float], String], eg.
   *     val whatever: Tuple2[Tuple2[Tuple2[Int, Double], Float], String] = 1 -> 1d -> 1f -> "4"
   *
   * and fields that were 'zipped' into this shape with `PartialTransformer.Accumulating.Support#product`
   * by applying this operation left to right eg.
   *     (field1: Int).product(field2: Double).product(field3: Float)
   *
   * The fields need to be provided in THE EXACT SAME ORDER they were zipped so for the operation above we'd expect
   *    List(field1, field2, field3, field4).
   *
   * This method will generate a tree that unpacks such an associated tuple, eg. for the example above we'd get a tree that corresponds to
   * a pattern match:
   *      case Tuple2(Tuple2(Tuple(field1, field2), field3), field4)
   *
   * and a list of unwrapped fields that allow you to operate on the bound values of the pattern match.
   */
  private def unzip(
    nestedPairs: Expr[Any],
    fields: NonEmptyList[Field.Wrapped[?]]
  )(using Quotes): (quotes.reflect.Unapply | quotes.reflect.Bind, List[Unwrapped]) = {
    import quotes.reflect.*

    def recurse(
      tpe: Type[?],
      leftoverFields: List[Field.Wrapped[?]]
    )(using Quotes): (quotes.reflect.Unapply | quotes.reflect.Bind, List[Field.Unwrapped]) = {
      import quotes.reflect.*

      (tpe -> leftoverFields) match {
        case ('[Tuple2[first, second]], Field.Wrapped(firstField, _) :: Field.Wrapped(secondField, _) :: Nil) =>
          val firstTpe = TypeRepr.of[second]
          val secondTpe = TypeRepr.of[first]
          val firstBind = Symbol.newBind(Symbol.spliceOwner, firstField.name, Flags.Local, firstTpe)
          val secondBind = Symbol.newBind(Symbol.spliceOwner, secondField.name, Flags.Local, secondTpe)
          val fields =
            List(Field.Unwrapped(secondField, Ref(secondBind).asExpr), Field.Unwrapped(firstField, Ref(firstBind).asExpr))
          val extractor =
            Unapply(Tuple2Extractor(secondTpe, firstTpe), Nil, Bind(secondBind, Wildcard()) :: Bind(firstBind, Wildcard()) :: Nil)
          extractor -> fields

        case ('[tpe], Field.Wrapped(field, _) :: Nil) =>
          val tpe = TypeRepr.of(using field.tpe)
          val bind = Symbol.newBind(Symbol.spliceOwner, field.name, Flags.Local, tpe)
          Bind(bind, Wildcard()) -> (Field.Unwrapped(field, Ref(bind).asExpr) :: Nil)

        case ('[Tuple2[rest, current]], Field.Wrapped(field, _) :: tail) =>
          val restTpe = TypeRepr.of[rest]
          val currentTpe = TypeRepr.of[current]
          val pairExtractor = Tuple2Extractor(restTpe, currentTpe)
          val bind = Symbol.newBind(Symbol.spliceOwner, field.name, Flags.Local, currentTpe)
          val (pattern, unzippedFields) = recurse(summon[Type[rest]], tail)
          val extractor = Unapply(pairExtractor, Nil, pattern :: Bind(bind, Wildcard()) :: Nil)
          val fields = Field.Unwrapped(field, Ref(bind).asExpr) :: unzippedFields
          extractor -> fields

        case (tpe, fields) =>
          val printedType = TypeRepr.of(using tpe).show
          report.errorAndAbort(s"Unexpected state reached while unzipping a product, tpe: $printedType, fields: ${fields}")

      }
    }

    recurse(nestedPairs.asTerm.tpe.asType, fields.reverse.toList)
  }

  private def Tuple2Extractor(using Quotes)(A: quotes.reflect.TypeRepr, B: quotes.reflect.TypeRepr) = {
    import quotes.reflect.*

    Select
      .unique('{ Tuple2 }.asTerm, "unapply")
      .appliedToTypes(A :: B :: Nil)
  }

}
