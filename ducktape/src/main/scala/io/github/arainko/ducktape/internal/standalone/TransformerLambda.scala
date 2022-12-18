package io.github.arainko.ducktape.internal.standalone

import scala.quoted.*
import scala.quoted.Expr
import io.github.arainko.ducktape.Transformer

enum TransformerLambda {
  final def param(using Quotes): quotes.reflect.ValDef = {
    import quotes.reflect.*

    this match {
      case ForProduct(param, methodCall, methodArgs)        => param.asTerm.asInstanceOf[ValDef]
      case ToAnyVal(param, constructorCall, constructorArg) => param.asTerm.asInstanceOf[ValDef]
      case FromAnyVal(param, fieldName)                     => param.asTerm.asInstanceOf[ValDef]
    }
  }

  case ForProduct(
    param: Expr[Any],
    methodCall: Expr[Any],
    methodArgs: List[Expr[Any]]
  )

  case ToAnyVal(
    param: Expr[Any],
    constructorCall: Expr[Any],
    constructorArg: Expr[Any]
  )

  case FromAnyVal(param: Expr[Any], fieldName: String)
}

object TransformerLambda {

  def fromTransformer(transformer: Expr[Transformer[?, ?]])(using Quotes): Option[TransformerLambda] = {
    import quotes.reflect.*

    transformer match {
      case '{ $t: Transformer.ForProduct[a, b] } =>
        val stripped = StripNoisyNodes(t)
        TransformerLambda.fromForProduct(stripped)
      case '{ $t: Transformer.FromAnyVal[a, b] } =>
        val stripped = StripNoisyNodes(t)
        TransformerLambda.fromFromAnyVal(stripped)
      case '{ $t: Transformer.ToAnyVal[a, b] } =>
        val stripped = StripNoisyNodes(t)
        TransformerLambda.fromToAnyVal(stripped)
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
  )(using Quotes): Option[TransformerLambda.ForProduct] = {
    import quotes.reflect.*

    PartialFunction.condOpt(expr.asTerm) {
      case MakeTransformer(param, Untyped(Apply(method, methodArgs))) =>
        TransformerLambda.ForProduct(param.asExpr, method.asExpr, methodArgs.map(_.asExpr))
    }
  }

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
  )(using Quotes): Option[TransformerLambda.ToAnyVal] = {
    import quotes.reflect.*

    PartialFunction.condOpt(expr.asTerm) {
      case MakeTransformer(param, Untyped(Block(_, Apply(Untyped(constructorCall), List(arg))))) =>
        TransformerLambda.ToAnyVal(param.asExpr, constructorCall.asExpr, arg.asExpr)
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
  )(using Quotes): Option[TransformerLambda.FromAnyVal] = {
    import quotes.reflect.*

    PartialFunction.condOpt(expr.asTerm) {
      case MakeTransformer(param, Untyped(Block(_, Select(Untyped(_: Ident), fieldName)))) =>
        TransformerLambda.FromAnyVal(param.asExpr, fieldName)
    }
  }
}
