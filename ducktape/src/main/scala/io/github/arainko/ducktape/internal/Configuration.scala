package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.*

import scala.quoted.*

private[ducktape] enum Configuration derives Debug {
  def tpe: Type[?]

  case Const(value: Expr[Any], tpe: Type[?])
  case CaseComputed(tpe: Type[?], function: Expr[Any => Any])
  case FieldComputed(tpe: Type[?], function: Expr[Any => Any])
  case FieldReplacement(source: Expr[Any], name: String, tpe: Type[?])
}

private[ducktape] object Configuration {

  trait ErrorModifier {
    def apply(parent: Plan[Plan.Error] | None.type, plan: Plan.Error)(using Quotes): Configuration | plan.type
  }

  object ErrorModifier {
    given Debug[ErrorModifier] = Debug.nonShowable

    val substituteOptionsWithNone = new ErrorModifier:
      def apply(parent: Plan[Plan.Error] | None.type, plan: Plan.Error)(using Quotes): Configuration | plan.type =
        plan.dest.tpe match {
          case tpe @ '[Option[a]] => Configuration.Const('{ None }, tpe)
          case _                  => plan
        }

    val substituteWithDefaults = new ErrorModifier:
      def apply(parent: Plan[Plan.Error] | None.type, plan: Plan.Error)(using Quotes): Configuration | plan.type =
        PartialFunction
          .condOpt(parent -> plan.destContext.segments.lastOption) {
            case (Plan.BetweenProducts(_, dest, _, _, _), Some(Path.Segment.Field(_, fieldName))) =>
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
      parent: Plan.BetweenProductFunction[Plan.Error] | Plan.BetweenProducts[Plan.Error],
      field: String,
      plan: Plan[Plan.Error]
    )(using Quotes): Configuration | plan.type
  }

  object FieldModifier {
    given Debug[FieldModifier] = Debug.nonShowable
  }

  enum Instruction derives Debug {
    def path: Path
    def side: Side
    def span: Span

    case Static(path: Path, side: Side, config: Configuration, span: Span)

    case Dynamic(path: Path, side: Side, config: Plan[Plan.Error] | None.type => Either[String, Configuration], span: Span)

    case Bulk(
      path: Path,
      side: Side,
      modifier: FieldModifier,
      span: Span
    )

    case Regional(path: Path, side: Side, modifier: ErrorModifier, span: Span)

    case Failed(path: Path, side: Side, message: String, span: Span)
  }

  object Instruction {
    object Failed {
      def from(instruction: Instruction, message: String): Instruction.Failed =
        Failed(instruction.path, instruction.side, message, instruction.span)
    }
  }

  def parse[A: Type, B: Type](
    configs: Expr[Seq[Field[A, B] | Case[A, B]]]
  )(using Quotes): List[Instruction] = {
    import quotes.reflect.*

    Varargs
      .unapply(configs)
      .get // TODO: Make it nicer
      .view
      .map(_.asTerm)
      .map {
        case cfg @ Apply(
              TypeApply(Select(IdentOfType('[Field.type]), "const"), a :: b :: destFieldTpe :: constTpe :: Nil),
              PathSelector(path) :: value :: Nil
            ) =>
          Configuration.Instruction.Static(
            path,
            Side.Dest,
            Configuration.Const(value.asExpr, value.tpe.widen.asType),
            Span.fromPosition(cfg.pos)
          )

        case cfg @ Apply(
              TypeApply(Select(IdentOfType('[Field.type]), "default"), a :: b :: destFieldTpe :: Nil),
              PathSelector(path) :: Nil
            ) =>
          def default(parent: Plan[Plan.Error] | None.type) =
            for {
              selectedField <-
                path.segments.lastOption
                  .flatMap(_.narrow[Path.Segment.Field])
                  .toRight("Selected path's length should be at least 1")
              defaults <-
                PartialFunction
                  .condOpt(parent) { case parent: Plan.BetweenProducts[Plan.Error] => parent.dest.defaults }
                  .toRight("Selected field's parent is not a product")
              defaultValue <-
                defaults
                  .get(selectedField.name)
                  .toRight(s"The field '${selectedField.name}' doesn't have a default value")
            } yield Configuration.Const(defaultValue, defaultValue.asTerm.tpe.asType)

          val span = Span.fromPosition(cfg.pos)

          Configuration.Instruction.Dynamic(path, Side.Dest, default, span)

        case cfg @ Apply(
              TypeApply(Select(IdentOfType('[Field.type]), "computed" | "renamed"), a :: b :: destFieldTpe :: computedTpe :: Nil),
              PathSelector(path) :: function :: Nil
            ) =>
          Configuration.Instruction.Static(
            path,
            Side.Dest,
            Configuration.FieldComputed(computedTpe.tpe.asType, function.asExpr.asInstanceOf[Expr[Any => Any]]),
            Span.fromPosition(cfg.pos)
          )

        case cfg @ Apply(
              TypeApply(Select(IdentOfType('[Field.type]), "allMatching"), a :: b :: destFieldTpe :: fieldSourceTpe :: Nil),
              PathSelector(path) :: fieldSource :: Nil
            ) =>
          parseAllMatching(fieldSource.asExpr, path, fieldSourceTpe.tpe, Span.fromPosition(cfg.pos))

        case cfg @ Apply(
              TypeApply(Select(IdentOfType('[Case.type]), "const"), a :: b :: sourceTpe :: constTpe :: Nil),
              PathSelector(path) :: value :: Nil
            ) =>
          Configuration.Instruction.Static(
            path,
            Side.Source,
            Configuration.Const(value.asExpr, value.tpe.asType),
            Span.fromPosition(cfg.pos)
          )

        case cfg @ Apply(
              TypeApply(Select(IdentOfType('[Case.type]), "computed"), a :: b :: sourceTpe :: computedTpe :: Nil),
              PathSelector(path) :: function :: Nil
            ) =>
          Configuration.Instruction.Static(
            path,
            Side.Source,
            Configuration.CaseComputed(computedTpe.tpe.asType, function.asExpr.asInstanceOf[Expr[Any => Any]]),
            Span.fromPosition(cfg.pos)
          )

        case cfg @ Apply(
              TypeApply(Select(IdentOfType('[Field.type]), "fallbackToNone"), a :: b :: destFieldTpe :: Nil),
              PathSelector(path) :: Nil
            ) =>
          Configuration.Instruction.Regional(path, Side.Dest, ErrorModifier.substituteOptionsWithNone, Span.fromPosition(cfg.pos))

        case cfg @ AsExpr('{ Field.fallbackToNone[a, b] }) =>
          Configuration.Instruction.Regional(
            Path.empty(Type.of[b]),
            Side.Dest,
            ErrorModifier.substituteOptionsWithNone,
            Span.fromPosition(cfg.pos)
          )

        case regionalCfg @ RegionalConfig(AsExpr('{ Field.fallbackToNone[a, b] }), path) =>
          Configuration.Instruction.Regional(
            path,
            Side.Dest,
            ErrorModifier.substituteOptionsWithNone,
            Span.fromPosition(regionalCfg.pos)
          )

        case cfg @ AsExpr('{ Field.fallbackToDefault[a, b] }) =>
          Configuration.Instruction.Regional(
            Path.empty(Type.of[b]),
            Side.Dest,
            ErrorModifier.substituteWithDefaults,
            Span.fromPosition(cfg.pos)
          )

        case cfg @ RegionalConfig(AsExpr('{ Field.fallbackToDefault[a, b] }), path) =>
          Configuration.Instruction.Regional(
            path,
            Side.Dest,
            ErrorModifier.substituteWithDefaults,
            Span.fromPosition(cfg.pos)
          )

        case DeprecatedConfig(configs) => configs

        case oopsie =>
          Configuration.Instruction.Failed(
            Path.empty(Type.of[Nothing]),
            Side.Dest,
            s"Unsupported config expression: ${oopsie.show}",
            Span.fromPosition(oopsie.pos)
          )
      }
      .toList
  }

  private def parseAllMatching(using Quotes)(
    sourceExpr: Expr[Any],
    path: Path,
    fieldSourceTpe: quotes.reflect.TypeRepr,
    span: Span
  ) = {

    Structure
      .fromTypeRepr(fieldSourceTpe)
      .narrow[Structure.Product]
      .map { sourceStruct =>
        val modifier = new FieldModifier:
          def apply(
            parent: Plan.BetweenProductFunction[Plan.Error] | Plan.BetweenProducts[Plan.Error],
            field: String,
            plan: Plan[Plan.Error]
          )(using Quotes): Configuration | plan.type =
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
