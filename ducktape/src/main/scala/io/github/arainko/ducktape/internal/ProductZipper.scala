package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.Mode

import scala.quoted.*

object ProductZipper {
  case class Field(name: String, tpe: Type[?])

  object Field {
    final case class Wrapped[F[+x]](field: Field, value: Expr[F[Any]])
    final case class Unwrapped(field: Field, value: Expr[Any])
  }

  import Field.*

  /**
   * 'Zips' a product using Transformer.Accumulating.Support[F]#product into a nested Tuple2 then
   * unpacks that said tuple and allows the caller to use the unpacked fields to construct a tree of type Dest.
   *
   * @param wrappedFields fields wrapped in F
   * @param unwrappedFields fields that are not wrapped in F (will not be a part of the zipped tuple)
   * @param construct function that allows the caller to create trees with the unpacked values
   */
  def zipAndConstruct[F[+x]: Type, Dest: Type](
    F: Expr[Mode.Accumulating[F]],
    wrappedFields: NonEmptyList[Field.Wrapped[F]],
    unwrappedFields: List[Field.Unwrapped]
  )(construct: ProductConstructor)(using Quotes): Expr[F[Dest]] = {
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
    F: Expr[Mode.Accumulating[F]],
    wrappedFields: NonEmptyList[Field.Wrapped[F]]
  )(using Quotes): Expr[F[Any]] =
    wrappedFields.map(_.value).reduceLeft { (accumulated, current) =>
      (accumulated -> current) match {
        case '{ $accumulated: F[a] } -> '{ $current: F[b] } =>
          '{ $F.product[`b`, `a`]($current, $accumulated) }
      }
    }

  private def unzipAndConstruct[Dest: Type](
    wrappedFields: NonEmptyList[Field.Wrapped[?]],
    unwrappedFields: List[Field.Unwrapped],
    nestedPairs: Expr[Any],
    construct: ProductConstructor
  )(using Quotes) = {
    val unzippedFields = ProductZipper.unzip(nestedPairs, wrappedFields)
    val fields = (unzippedFields ::: unwrappedFields).map(field => field.field.name -> alignOwner(field.value)).toMap
    construct(fields).asExprOf[Dest]
  }

  private def alignOwner(expr: Expr[Any])(using Quotes) = {
    import quotes.reflect.*
    expr.asTerm.changeOwner(Symbol.spliceOwner).asExpr
  }

  private def unzip(
    nestedPairs: Expr[Any],
    _fields: NonEmptyList[Field.Wrapped[?]]
  )(using Quotes) = {
    val fields = _fields.toList.toVector
    val size = fields.size

    if size == 1 then {
      Field.Unwrapped(fields.head.field, nestedPairs) :: Nil
    } else {
      fields.indices.map { idx =>
        // fields: int1: Positive, int2: Positive2, int3: Positive3, int4: Positive4
        // after zipping will become:
        // scala.Tuple2[Positive4, scala.Tuple2[Positive3, scala.Tuple2[Positive2, Positive]]]

        if idx == 0 then Field.Unwrapped(fields(idx).field, unpackRight(nestedPairs, size - 1).asExpr)
        else Field.Unwrapped(fields(idx).field, unpackLeft(unpackRight(nestedPairs, size - 1 - idx).asExpr).asExpr)
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
