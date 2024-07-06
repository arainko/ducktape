package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.Mode

import scala.quoted.*

private[ducktape] object ProductZipper {

  /**
   * 'Zips' a product using Mode.Accumulating[F]#product into a nested Tuple2 then
   * unpacks that said tuple and allows the caller to use the unpacked fields to construct a tree of type Dest.
   *
   * @param wrappedFields fields wrapped in F
   * @param unwrappedFields fields that are not wrapped in F (will not be a part of the zipped tuple)
   * @param construct function that allows the caller to create trees with the unpacked values
   */
  def zipAndConstruct[F[+x]: Type, Dest: Type](
    F: Expr[Mode.Accumulating[F]],
    wrappedFields: NonEmptyList[FieldValue.Wrapped[F]],
    unwrappedFields: List[FieldValue.Unwrapped]
  )(construct: ProductConstructor)(using Quotes): Expr[F[Dest]] = {
    // ducktape 0.2.x changed the zipping and unzipping algorithm, to emulate the 0.1 runtime behavior we need to zip those fields in reverse
    val reorderedFields = wrappedFields.reverse

    zipFields[F](F, reorderedFields) match {
      case '{ $zipped: F[a] } =>
        '{
          $F.map(
            $zipped,
            value => ${ unzipAndConstruct[Dest](reorderedFields, unwrappedFields, 'value, construct) }
          )
        }
    }
  }

  private def zipFields[F[+x]: Type](
    F: Expr[Mode.Accumulating[F]],
    wrappedFields: NonEmptyList[FieldValue.Wrapped[F]]
  )(using Quotes): Expr[F[Any]] =
    wrappedFields.map(_.value).reduceLeft { (accumulated, current) =>
      (accumulated -> current) match {
        case '{ $accumulated: F[a] } -> '{ $current: F[b] } =>
          '{ $F.product[`b`, `a`]($current, $accumulated) }
      }
    }

  private def unzipAndConstruct[Dest: Type](
    wrappedFields: NonEmptyList[FieldValue.Wrapped[?]],
    unwrappedFields: List[FieldValue.Unwrapped],
    nestedPairs: Expr[Any],
    construct: ProductConstructor
  )(using Quotes) = {
    val unzippedFields = ProductZipper.unzip(nestedPairs, wrappedFields)
    val fields = (unzippedFields ::: unwrappedFields).sortBy(_.index).map(field => alignOwner(field.value))
    construct(fields).asExprOf[Dest]
  }

  private def alignOwner(expr: Expr[Any])(using Quotes) = {
    import quotes.reflect.*
    expr.asTerm.changeOwner(Symbol.spliceOwner).asExpr
  }

  private def unzip(
    nestedPairs: Expr[Any],
    _fields: NonEmptyList[FieldValue.Wrapped[?]]
  )(using Quotes) = {
    val fields = _fields.toVector
    val size = fields.size

    if size == 1 then {
      fields.head.unwrapped(nestedPairs) :: Nil
    } else {
      fields.indices.map { idx =>
        if idx == 0 then fields(idx).unwrapped(unpackRight(nestedPairs, size - 1).asExpr)
        else fields(idx).unwrapped(unpackLeft(unpackRight(nestedPairs, size - 1 - idx).asExpr).asExpr)
      }.toList
    }
  }

  private def unpackRight(expr: Expr[Any], times: Int)(using Quotes): quotes.reflect.Term = {
    import quotes.reflect.*

    if (times == 0) expr.asTerm
    else Select.unique(unpackRight(expr, times - 1), "_2")
  }

  private def unpackLeft(expr: Expr[Any])(using Quotes): quotes.reflect.Term = {
    import quotes.reflect.*
    Select.unique(expr.asTerm, "_1")
  }
}
