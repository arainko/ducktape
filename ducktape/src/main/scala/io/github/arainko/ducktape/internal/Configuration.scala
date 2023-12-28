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

  trait Modifier {
    def apply(plan: Plan.Error)(using Quotes): Configuration | plan.type
  }

  object Modifier {
    given Debug[Modifier] = new:
      extension (self: Modifier) def show(using Quotes): String = "Modifier(...)"
  }

  trait ContextualModifier {
    def apply(field: String, plan: Plan[Plan.Error])(using Quotes): Configuration | plan.type
  }

  object ContextualModifier {
    given Debug[ContextualModifier] = new:
      extension (self: ContextualModifier) def show(using Quotes): String = "ContextualModifier(...)"
  }

  case class Traversal(trace: Vector[Plan[Plan.Error]], destination: Plan[Plan.Error])

  enum Instruction derives Debug {
    def path: Path
    def target: Target
    def span: Span

    case Static(path: Path, target: Target, config: Configuration, span: Span)

    case Dynamic(path: Path, target: Target, config: Traversal => Either[String, Configuration], span: Span)

    case ContextualProduct(
      path: Path,
      target: Target,
      modifier: ContextualModifier,
      span: Span
    )

    case Regional(path: Path, target: Target, modifier: Modifier, span: Span)

    case Failed(path: Path, target: Target, message: String, span: Span)
  }

  def parse[A: Type, B: Type](
    configs: Expr[Seq[Field[A, B] | Case[A, B]]]
  )(using Quotes) = {
    import quotes.reflect.*

    Varargs
      .unapply(configs)
      .get // TODO: Make it nicer
      .view
      .map(_.asTerm)
      .flatMap {
        case cfg @ Apply(
              TypeApply(Select(IdentOfType('[Field.type]), "const"), a :: b :: destFieldTpe :: constTpe :: Nil),
              PathSelector(path) :: value :: Nil
            ) =>
          Configuration.Instruction.Static(
            path,
            Target.Dest,
            Configuration.Const(value.asExpr, value.tpe.widen.asType),
            Span.fromPosition(cfg.pos)
          ) :: Nil

        case cfg @ Apply(
              TypeApply(Select(IdentOfType('[Field.type]), "default"), a :: b :: destFieldTpe :: Nil),
              PathSelector(path) :: Nil
            ) =>
          def default(traversal: Traversal) =
            for {
              selectedField <-
                path.segments.lastOption
                  .flatMap(_.narrow[Path.Segment.Field])
                  .toRight("Selected path's length should be at least 1")
              defaults <-
                traversal.trace.lastOption
                  .flatMap(_.narrow[Plan.BetweenProducts[Plan.Error]])
                  .map(plan => Defaults.of(plan.dest))
                  .toRight("Selected field's parent is not a product")
              defaultValue <-
                defaults
                  .get(selectedField.name)
                  .toRight(s"The field '${selectedField.name}' doesn't have a default value")
            } yield Configuration.Const(defaultValue, defaultValue.asTerm.tpe.asType)

          val span = Span.fromPosition(cfg.pos)

          Configuration.Instruction.Dynamic(
            path,
            Target.Dest,
            default,
            span
          ) :: Nil

        case cfg @ Apply(
              TypeApply(Select(IdentOfType('[Field.type]), "computed" | "renamed"), a :: b :: destFieldTpe :: computedTpe :: Nil),
              PathSelector(path) :: function :: Nil
            ) =>
          Configuration.Instruction.Static(
            path,
            Target.Dest,
            Configuration.FieldComputed(computedTpe.tpe.asType, function.asExpr.asInstanceOf[Expr[Any => Any]]),
            Span.fromPosition(cfg.pos)
          ) :: Nil

        case cfg @ Apply(
              TypeApply(Select(IdentOfType('[Field.type]), "allMatching"), a :: b :: destFieldTpe :: fieldSourceTpe :: Nil),
              PathSelector(path) :: fieldSource :: Nil
            ) =>

          Structure
            .fromTypeRepr(fieldSource.tpe)
            .narrow[Structure.Product]
            .map { sourceStruct =>
              val modifier = new ContextualModifier:
                def apply(field: String, plan: Plan[Plan.Error])(using Quotes): Configuration | plan.type = 
                  sourceStruct.fields.get(field).match {
                    case Some(struct) if struct.tpe.repr <:< plan.dest.tpe.repr =>
                      Configuration.FieldReplacement(fieldSource.asExpr, field, struct.tpe)
                    case other => plan
                  }

              Configuration.Instruction.ContextualProduct(
                path,
                Target.Dest,
                modifier,
                Span.fromPosition(cfg.pos)
              )
            }
            .getOrElse(
              Configuration.Instruction.Failed(
                path,
                Target.Dest,
                "Field source needs to be a product",
                Span.fromPosition(cfg.pos)
              )
            ) :: Nil
        case cfg @ Apply(
              TypeApply(Select(IdentOfType('[Case.type]), "const"), a :: b :: sourceTpe :: constTpe :: Nil),
              PathSelector(path) :: value :: Nil
            ) =>
          Configuration.Instruction.Static(
            path,
            Target.Source,
            Configuration.Const(value.asExpr, value.tpe.asType),
            Span.fromPosition(cfg.pos)
          ) :: Nil

        case cfg @ Apply(
              TypeApply(Select(IdentOfType('[Case.type]), "computed"), a :: b :: sourceTpe :: computedTpe :: Nil),
              PathSelector(path) :: function :: Nil
            ) =>
          Configuration.Instruction.Static(
            path,
            Target.Source,
            Configuration.CaseComputed(computedTpe.tpe.asType, function.asExpr.asInstanceOf[Expr[Any => Any]]),
            Span.fromPosition(cfg.pos)
          ) :: Nil

        case cfg @ Apply(
              TypeApply(Select(IdentOfType('[Field.type]), "useNones"), a :: b :: destFieldTpe :: Nil),
              PathSelector(path) :: Nil
            ) =>
          val modifier: Modifier = new:
            def apply(plan: Plan.Error)(using Quotes): Configuration | plan.type =
              plan.dest.tpe match {
                case tpe @ '[Option[a]] => Configuration.Const('{ None }, tpe)
                case _                  => plan
              }
          Configuration.Instruction.Regional(path, Target.Dest, modifier, Span.fromPosition(cfg.pos)) :: Nil

        case DeprecatedConfig(configs) => configs

        case oopsie =>
          Configuration.Instruction.Failed(
            Path.empty(Type.of[Nothing]),
            Target.Dest,
            s"Unsupported config expression: ${oopsie.show}",
            Span.fromPosition(oopsie.pos)
          ) :: Nil
      }
      .toList
  }

  private def parseAllMatching(using Quotes)(
    sourceExpr: Expr[Any],
    path: Path,
    destFieldTpe: quotes.reflect.TypeRepr,
    fieldSourceTpe: quotes.reflect.TypeRepr,
    span: Span
  ) = {
    val result =
      Structure
        .fromTypeRepr(destFieldTpe)
        .narrow[Structure.Product | Structure.Function]
        .zip(Structure.fromTypeRepr(fieldSourceTpe).narrow[Structure.Product])
        .map { (destStruct, fieldSourceStruct) =>
          val fields = destStruct match {
            case p: Structure.Product  => p.fields
            case f: Structure.Function => f.args
          }

          fields.collect {
            case (fieldName @ fieldSourceStruct.fields(source), dest) if source.tpe.repr <:< dest.tpe.repr =>
              Configuration.Instruction.Static(
                path.appended(Path.Segment.Field(source.tpe, fieldName)),
                Target.Dest,
                Configuration.FieldReplacement(sourceExpr, fieldName, source.tpe),
                span
              )
          }.toList
        }
        .getOrElse(
          Configuration.Instruction
            .Failed(
              path,
              Target.Dest,
              "Field.allMatching only works when targeting a product or a function and supplying a product",
              span
            ) :: Nil
        )

    result match {
      case Nil =>
        Configuration.Instruction.Failed(path, Target.Dest, "No matching fields found", span) :: Nil // TODO: Better error message
      case configs => configs
    }
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
            Target.Source,
            Configuration.Const(value, value.asTerm.tpe.asType),
            Span.fromExpr(cfg)
          ) :: Nil

        case cfg @ '{
              type sourceSubtype
              type src >: `sourceSubtype`
              Case.computed[`sourceSubtype`].apply[`src`, dest]($function)
            } =>
          val path = Path.empty(Type.of[src]).appended(Path.Segment.Case(Type.of[sourceSubtype]))
          Configuration.Instruction.Static(
            path,
            Target.Source,
            Configuration.CaseComputed(Type.of[dest], function.asInstanceOf[Expr[Any => Any]]),
            Span.fromExpr(cfg)
          ) :: Nil

        case cfg @ '{ Field.allMatching[a, b, source]($fieldSource) } =>
          parseAllMatching(fieldSource, Path.empty(Type.of[b]), TypeRepr.of[b], TypeRepr.of[source], Span.fromExpr(cfg))
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
}
