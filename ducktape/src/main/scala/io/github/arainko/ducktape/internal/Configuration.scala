package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.*

import scala.quoted.*

private[ducktape] enum Configuration[+F <: Fallible] {
  def tpe: Type[?]

  case Const(value: Expr[Any], tpe: Type[?]) extends Configuration[Nothing]
  case CaseComputed(tpe: Type[?], function: Expr[Any => Any]) extends Configuration[Nothing]
  case FieldComputed(tpe: Type[?], function: Expr[Any => Any]) extends Configuration[Nothing]
  case FieldReplacement(source: Expr[Any], name: String, tpe: Type[?]) extends Configuration[Nothing]
  case FallibleConst(value: Expr[Any], tpe: Type[?]) extends Configuration[Fallible]
  case FallibleFieldComputed(tpe: Type[?], function: Expr[Any => Any]) extends Configuration[Fallible]
  case FallibleCaseComputed(tpe: Type[?], function: Expr[Any => Any]) extends Configuration[Fallible]
}

private[ducktape] object Configuration {

  given debug: Debug[Configuration[Fallible]] = Debug.derived

  trait ErrorModifier {
    def apply(parent: Plan[Erroneous, Fallible] | None.type, plan: Plan.Error)(using Quotes): Configuration[Nothing] | plan.type
  }

  object ErrorModifier {
    given Debug[ErrorModifier] = Debug.nonShowable

    val substituteOptionsWithNone = new ErrorModifier:
      def apply(parent: Plan[Erroneous, Fallible] | None.type, plan: Plan.Error)(using
        Quotes
      ): Configuration[Nothing] | plan.type =
        plan.dest.tpe match {
          case tpe @ '[Option[a]] => Configuration.Const('{ None }, tpe)
          case _                  => plan
        }

    val substituteWithDefaults = new ErrorModifier:
      def apply(parent: Plan[Erroneous, Fallible] | None.type, plan: Plan.Error)(using
        Quotes
      ): Configuration[Nothing] | plan.type =
        PartialFunction
          .condOpt(parent -> plan.destPath.segments.lastOption) {
            case (Plan.BetweenProducts(_, dest, _), Some(Path.Segment.Field(_, fieldName))) =>
              import quotes.reflect.*

              dest.defaults
                .get(fieldName)
                .collect { case expr if expr.asTerm.tpe <:< plan.dest.tpe.repr => Configuration.Const(expr, plan.dest.tpe) }
            case (Plan.BetweenTupleProduct(_, dest, _), Some(Path.Segment.Field(_, fieldName))) =>
              import quotes.reflect.*

              dest.defaults
                .get(fieldName)
                .collect { case expr if expr.asTerm.tpe <:< plan.dest.tpe.repr => Configuration.Const(expr, plan.dest.tpe) }
          }
          .flatten
          .getOrElse(plan)
  }

  trait FieldModifier {
    def apply(
      parent: Plan.BetweenProductFunction[Erroneous, Fallible] | Plan.BetweenProducts[Erroneous, Fallible] |
        Plan.BetweenTupleProduct[Erroneous, Fallible],
      field: String,
      plan: Plan[Erroneous, Fallible]
    )(using Quotes): Configuration[Nothing] | plan.type
  }

  object FieldModifier {
    given Debug[FieldModifier] = Debug.nonShowable
  }

  enum Instruction[+F <: Fallible] {
    def path: Path
    def side: Side
    def span: Span

    case Static(path: Path, side: Side, config: Configuration[F], span: Span) extends Instruction[F]

    case Dynamic(
      path: Path,
      side: Side,
      config: Plan[Erroneous, Fallible] | None.type => Either[String, Configuration[Nothing]],
      span: Span
    ) extends Instruction[Nothing]

    case Bulk(
      path: Path,
      side: Side,
      modifier: FieldModifier,
      span: Span
    ) extends Instruction[Nothing]

    case Regional(path: Path, side: Side, modifier: ErrorModifier, span: Span) extends Instruction[Nothing]

    case Failed(path: Path, side: Side, message: String, span: Span) extends Instruction[Nothing]
  }

  object Instruction {
    given debug: Debug[Instruction[Fallible]] = Debug.derived

    object Failed {
      def from(instruction: Instruction[Fallible], message: String): Instruction.Failed =
        Failed(instruction.path, instruction.side, message, instruction.span)
    }
  }

  def parse[G[+x], A: Type, B: Type, F <: Fallible](
    configs: Expr[Seq[Field.Fallible[G, A, B] | Case.Fallible[G, A, B]]],
    parsers: NonEmptyList[ConfigParser[F]]
  )(using Quotes, Context): List[Instruction[F]] = {
    import quotes.reflect.*
    def fallback(term: quotes.reflect.Term) =
      Configuration.Instruction.Failed(
        Path.empty(Type.of[Nothing]),
        Side.Dest,
        s"Unsupported config expression: ${term.show}",
        Span.fromPosition(term.pos)
      )
    val parser = ConfigParser.combine(parsers)

    Varargs
      .unapply(configs)
      .getOrElse(report.errorAndAbort("All of the transformation configs need to be inlined", configs))
      .map(expr => parser.applyOrElse(expr.asTerm, fallback))
      .toList
  }
}
