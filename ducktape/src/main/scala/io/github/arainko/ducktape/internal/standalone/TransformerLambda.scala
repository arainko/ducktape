package io.github.arainko.ducktape.internal.standalone

import scala.quoted.*
import scala.quoted.Expr
import io.github.arainko.ducktape.Transformer
import io.github.arainko.ducktape.internal.standalone.TransformerLambda.ForProduct
import io.github.arainko.ducktape.internal.standalone.TransformerLambda.ToAnyVal
import io.github.arainko.ducktape.internal.standalone.TransformerLambda.FromAnyVal

sealed trait TransformerLambda[Q <: Quotes & Singleton] {
  val quotes: Q
  val param: quotes.reflect.ValDef
}

object TransformerLambda {

  final class ForProduct[Q <: Quotes & Singleton](using val quotes: Q)(
    val param: quotes.reflect.ValDef,
    val methodCall: quotes.reflect.Term,
    val methodArgs: List[quotes.reflect.Term]
  ) extends TransformerLambda[Q]

  final class ToAnyVal[Q <: Quotes & Singleton](using val quotes: Q)(
    val param: quotes.reflect.ValDef,
    val constructorCall: quotes.reflect.Term,
    val constructorArg: quotes.reflect.Term
  ) extends TransformerLambda[Q]

  final class FromAnyVal[Q <: Quotes & Singleton](using val quotes: Q)(val param: quotes.reflect.ValDef, val fieldName: String) extends TransformerLambda[Q]

  def fromTransformer(transformer: Expr[Transformer[?, ?]])(using Quotes): Option[TransformerLambda[quotes.type]] = {
    import quotes.reflect.*

    transformer match {
      case '{ $t: Transformer.ForProduct[a, b] } =>
        TransformerLambda.fromForProduct(t)
      case '{ $t: Transformer.FromAnyVal[a, b] } =>
        TransformerLambda.fromFromAnyVal(t)
      case '{ $t: Transformer.ToAnyVal[a, b] } =>
        TransformerLambda.fromToAnyVal(t)
      case other => None
    }
  }

  /**
   * Matches a .make Transformer.ForProduct creation eg.:
   *
   * Transformer.ForProduct.make((p: Person) => new Person2(p.int, p.str))
   *
   * @return the parameter ('p'), a call to eg. a method ('new Person2')
   * and the args of that call ('p.int', 'p.str')
   */
  def fromForProduct(
    expr: Expr[Transformer.ForProduct[?, ?]]
  )(using Quotes): Option[TransformerLambda.ForProduct[quotes.type]] = {
    import quotes.reflect.*

    PartialFunction.condOpt(expr.asTerm) {
      case MakeTransformer(param, Untyped(Apply(method, methodArgs))) =>
        TransformerLambda.ForProduct(param, method, methodArgs)
    }
  }

  // import quotes.reflect.*

  // PartialFunction.condOpt(expr.asTerm) {
  //   case MakeTransformer(param, Untyped(Apply(method, methodArgs))) =>
  //     TransformerLambda.ForProduct(param, method.asExpr, methodArgs.map(_.asExpr))
  // }

  /**
   * Matches a .make Transformer.ToAnyVal creation eg.:
   *
   * final case class Name(value: String) extends AnyVal
   *
   * Transformer.ToAnyVal.make((str: String) => new Name(str))
   *
   * @return the parameter ('str'), the constructor call ('new Name') and the singular arg ('str').
   */
  def fromToAnyVal(
    expr: Expr[Transformer.ToAnyVal[?, ?]]
  )(using Quotes): Option[TransformerLambda.ToAnyVal[quotes.type]] = {
    import quotes.reflect.*

    PartialFunction.condOpt(expr.asTerm) {
      case MakeTransformer(param, Untyped(Block(_, Uninlined(Apply(Untyped(constructorCall), List(arg)))))) =>
        TransformerLambda.ToAnyVal(param, constructorCall, arg)
    }
  }

  /**
   * Matches a .make Transformer.FromAnyVal creation eg.:
   *
   * final case class Name(value: String) extends AnyVal
   *
   * Transformer.FromAnyVal.make((name: Name) => name.value)
   *
   * @return the parameter ('name'), and the field name ('value' from the expression 'name.value')
   */
  def fromFromAnyVal(
    expr: Expr[Transformer.FromAnyVal[?, ?]]
  )(using Quotes): Option[TransformerLambda.FromAnyVal[quotes.type]] = {
    import quotes.reflect.*

    PartialFunction.condOpt(expr.asTerm) {
      case MakeTransformer(param, Untyped(Block(_, Uninlined(Select(Untyped(_: Ident), fieldName))))) =>
        TransformerLambda.FromAnyVal(param, fieldName)
    }
  }
}
