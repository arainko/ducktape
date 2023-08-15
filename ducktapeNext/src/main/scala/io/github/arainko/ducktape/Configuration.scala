package io.github.arainko.ducktape

import scala.quoted.*
import io.github.arainko.ducktape.internal.Debug

enum Configuration derives Debug {
  case Const(value: Expr[Any])
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

  def parse[A: Type, B: Type](configs: Expr[Seq[Config[A, B]]])(using Quotes) =  {
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
              TypeApply(Select(Ident("Case2"), "const"), a :: b :: sourceTpe :: constTpe :: Nil),
              PathSelector(path) :: value :: Nil
            ) =>
          Configuration.At(
            path,
            Target.Source,
            // path.last.tpe here because 'sourceTpe' is always widened (i.e it doesn't work when configuring enum cases)
            Configuration.Const(value.asExpr)
          )
      }
  }
}
