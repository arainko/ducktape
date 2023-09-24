package io.github.arainko.ducktape

import scala.quoted.*
import io.github.arainko.ducktape.internal.Debug

enum Configuration derives Debug {
  case Const(value: Expr[Any])
  case Computed(destTpe: Type[?], function: Expr[Any => Any])
}

object Configuration {
  enum Target derives Debug {
    case Source, Dest

    final def isSource: Boolean =
      this match {
        case Source => true
        case Dest   => false
      }

    final def isDest: Boolean = !isSource
  }

  final case class At(path: Path, target: Target, config: Configuration) derives Debug

  inline def run[A, B, Args <: FunctionArguments](inline configs: Arg2[A, B, Args]*) = ${ parse2('configs) }

  def parse2[A: Type, B: Type, Args <: FunctionArguments](
    configs: Expr[Seq[Arg2[A, B, Args]]]
  )(using Quotes): Expr[Unit] = {
    import quotes.reflect.*
    report.info(Debug.show(parse(configs)))
    '{}
  }

  def parse[A: Type, B: Type, Args <: FunctionArguments](
    configs: Expr[Seq[Field2[A, B] | Case2[A, B] | Arg2[A, B, Args]]]
  )(using Quotes) = {
    import quotes.reflect.*

    Varargs
      .unapply(configs)
      .get // TODO: Make it nicer
      .map(_.asTerm)
      .map {
        case Apply(
              TypeApply(Select(Ident("Field2"), "const"), a :: b :: destFieldTpe :: constTpe :: Nil),
              PathSelector(path) :: value :: Nil
            ) =>
          Configuration.At(
            path,
            Target.Dest,
            Configuration.Const(value.asExpr)
          )

        case Apply(
              TypeApply(Select(Ident("Field2"), "computed"), a :: b :: destFieldTpe :: computedTpe :: Nil),
              PathSelector(path) :: function :: Nil
            ) =>
          Configuration.At(
            path,
            Target.Dest,
            Configuration.Computed(computedTpe.tpe.asType, function.asExpr.asInstanceOf[Expr[Any => Any]])
          )

        case Apply(
              TypeApply(Select(Ident("Field2"), "allMatching"), a :: b :: destFieldTpe :: productTpe :: Nil),
              PathSelector(path) :: function :: Nil
            ) =>
          ???

        case Apply(
              TypeApply(Select(Ident("Case2"), "const"), a :: b :: sourceTpe :: constTpe :: Nil),
              PathSelector(path) :: value :: Nil
            ) =>
          Configuration.At(
            path,
            Target.Source,
            Configuration.Const(value.asExpr)
          )

        case Apply(
              TypeApply(Select(Ident("Arg2"), "const"), a :: b :: args :: destFieldTpe :: constTpe :: Nil),
              PathSelector(path) :: value :: Nil
            ) =>
          Configuration.At(
            path,
            Target.Dest,
            Configuration.Const(value.asExpr)
          )
      }
  }

}
