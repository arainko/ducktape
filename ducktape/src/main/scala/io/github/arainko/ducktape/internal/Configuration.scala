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
  case FallibleComputed(tpe: Type[?], function: Expr[Any => Any]) extends Configuration[Fallible]
}

private[ducktape] object Configuration {

  given debug: Debug[Configuration[Fallible]] = Debug.derived

  trait ErrorModifier {
    def apply(parent: Plan[Plan.Error, Fallible] | None.type, plan: Plan.Error)(using Quotes): Configuration[Nothing] | plan.type
  }

  object ErrorModifier {
    given Debug[ErrorModifier] = Debug.nonShowable

    val substituteOptionsWithNone = new ErrorModifier:
      def apply(parent: Plan[Plan.Error, Fallible] | None.type, plan: Plan.Error)(using
        Quotes
      ): Configuration[Nothing] | plan.type =
        plan.dest.tpe match {
          case tpe @ '[Option[a]] => Configuration.Const('{ None }, tpe)
          case _                  => plan
        }

    val substituteWithDefaults = new ErrorModifier:
      def apply(parent: Plan[Plan.Error, Fallible] | None.type, plan: Plan.Error)(using
        Quotes
      ): Configuration[Nothing] | plan.type =
        PartialFunction
          .condOpt(parent -> plan.destPath.segments.lastOption) {
            case (Plan.BetweenProducts(_, dest, _), Some(Path.Segment.Field(_, fieldName))) =>
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
      parent: Plan.BetweenProductFunction[Plan.Error, Fallible] | Plan.BetweenProducts[Plan.Error, Fallible],
      field: String,
      plan: Plan[Plan.Error, Fallible]
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
      config: Plan[Plan.Error, Fallible] | None.type => Either[String, Configuration[F]],
      span: Span
    ) extends Instruction[F]

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
  )(using Quotes): List[Instruction[F]] = {
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
      .get // TODO: Make it nicer
      .map(expr => parser.applyOrElse(expr.asTerm, fallback))
      .toList
  }

  private def parseAllMatching(using Quotes)(
    sourceExpr: Expr[Any],
    path: Path,
    fieldSourceTpe: quotes.reflect.TypeRepr,
    span: Span
  ) = {

    Structure
      .fromTypeRepr(fieldSourceTpe, Path.empty(fieldSourceTpe.asType))
      .narrow[Structure.Product]
      .map { sourceStruct =>
        val modifier = new FieldModifier:
          def apply(
            parent: Plan.BetweenProductFunction[Plan.Error, Fallible] | Plan.BetweenProducts[Plan.Error, Fallible],
            field: String,
            plan: Plan[Plan.Error, Fallible]
          )(using Quotes): Configuration[Nothing] | plan.type =
            sourceStruct.fields.get(field).match {
              case Some(struct) if struct.tpe.repr <:< plan.dest.tpe.repr =>
                Configuration.FieldReplacement(sourceExpr, field, struct.tpe)
              case other => plan
            }

        Configuration.Instruction.Bulk(
          path,
          Side.Dest,
          modifier,
          span
        )
      }
      .getOrElse(
        Configuration.Instruction.Failed(
          path,
          Side.Dest,
          "Field source needs to be a product",
          span
        )
      )
  }

  object DeprecatedConfig {
    def unapply(using Quotes)(term: quotes.reflect.Term) = {
      import quotes.reflect.*

      PartialFunction.condOpt(term.asExpr):
        case cfg @ '{
              type sourceSubtype
              type src >: `sourceSubtype`
              Case.const[`sourceSubtype`].apply[`src`, dest]($value)
            } =>
          val path = Path.empty(Type.of[src]).appended(Path.Segment.Case(Type.of[sourceSubtype]))
          Configuration.Instruction.Static(
            path,
            Side.Source,
            Configuration.Const(value, value.asTerm.tpe.asType),
            Span.fromExpr(cfg)
          )

        case cfg @ '{
              type sourceSubtype
              type src >: `sourceSubtype`
              Case.computed[`sourceSubtype`].apply[`src`, dest]($function)
            } =>
          val path = Path.empty(Type.of[src]).appended(Path.Segment.Case(Type.of[sourceSubtype]))
          Configuration.Instruction.Static(
            path,
            Side.Source,
            Configuration.CaseComputed(Type.of[dest], function.asInstanceOf[Expr[Any => Any]]),
            Span.fromExpr(cfg)
          )

        case cfg @ '{ Field.allMatching[a, b, source]($fieldSource) } =>
          parseAllMatching(fieldSource, Path.empty(Type.of[b]), TypeRepr.of[source], Span.fromExpr(cfg))
    }
  }

  private object IdentOfType {
    def unapply(using Quotes)(term: quotes.reflect.Term): Option[Type[?]] = {
      import quotes.reflect.*

      PartialFunction.condOpt(term) {
        case ident: Ident => ident.tpe.asType
      }
    }
  }

  private object AsExpr {
    def unapply(using Quotes)(term: quotes.reflect.Term): Some[Expr[Any]] = {
      Some(term.asExpr)
    }
  }

  private object RegionalConfig {
    def unapply(using Quotes)(term: quotes.reflect.Term): Option[(quotes.reflect.Term, Path)] = {
      import quotes.reflect.*
      PartialFunction.condOpt(term) {
        case Apply(
              TypeApply(
                Apply(
                  TypeApply(Select(Ident("Regional"), "regional"), _),
                  term :: Nil
                ),
                _
              ),
              PathSelector(path) :: Nil
            ) =>
          term -> path
      }
    }
  }
}
