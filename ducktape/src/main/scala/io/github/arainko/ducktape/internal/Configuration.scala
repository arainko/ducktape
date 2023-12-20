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

  enum At derives Debug {
    def path: Path
    def target: Target
    def span: Span

    case Successful(path: Path, target: Target, config: Configuration, span: Span)
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
          Configuration.At.Successful(
            path,
            Target.Dest,
            Configuration.Const(value.asExpr, value.tpe.widen.asType),
            Span.fromPosition(cfg.pos)
          ) :: Nil

        case cfg @ Apply(
              TypeApply(Select(IdentOfType('[Field.type]), "default"), a :: b :: destFieldTpe :: Nil),
              PathSelector(path) :: Nil
            ) =>
          val segments = path.toVector
          val parentTpe =
            segments
              .lift(segments.length - 2) // next to last elem aka the parent of the selected field
              .map(_.tpe)
              .getOrElse(path.root)
          val default =
            for {
              selectedField <-
                path.segments.lastOption
                  .flatMap(_.narrow[Path.Segment.Field])
                  .toRight("Selected path's length should be at least 1")
              structure <-
                Structure
                  .fromTypeRepr(parentTpe.repr)
                  .narrow[Structure.Product]
                  .toRight("Selected field's parent is not a product")
              default <-
                Defaults
                  .of(structure)
                  .get(selectedField.name)
                  .toRight(s"The field '${selectedField.name}' doesn't have a default value")
            } yield Configuration.At.Successful(
              path,
              Target.Dest,
              Configuration.Const(default, default.asTerm.tpe.asType),
              Span.fromPosition(cfg.pos)
            )

          default.left.map(error => Configuration.At.Failed(path, Target.Dest, error, Span.fromPosition(cfg.pos))).merge :: Nil

        case cfg @ Apply(
              TypeApply(Select(IdentOfType('[Field.type]), "computed" | "renamed"), a :: b :: destFieldTpe :: computedTpe :: Nil),
              PathSelector(path) :: function :: Nil
            ) =>
          Configuration.At.Successful(
            path,
            Target.Dest,
            Configuration.FieldComputed(computedTpe.tpe.asType, function.asExpr.asInstanceOf[Expr[Any => Any]]),
            Span.fromPosition(cfg.pos)
          ) :: Nil

        case cfg @ Apply(
              TypeApply(Select(IdentOfType('[Field.type]), "allMatching"), a :: b :: destFieldTpe :: fieldSourceTpe :: Nil),
              PathSelector(path) :: fieldSource :: Nil
            ) =>
          parseAllMatching(fieldSource.asExpr, path, destFieldTpe.tpe, fieldSourceTpe.tpe, Span.fromPosition(cfg.pos))

        case cfg @ Apply(
              TypeApply(Select(IdentOfType('[Case.type]), "const"), a :: b :: sourceTpe :: constTpe :: Nil),
              PathSelector(path) :: value :: Nil
            ) =>
          Configuration.At.Successful(
            path,
            Target.Source,
            Configuration.Const(value.asExpr, value.tpe.asType),
            Span.fromPosition(cfg.pos)
          ) :: Nil

        case cfg @ Apply(
              TypeApply(Select(IdentOfType('[Case.type]), "computed"), a :: b :: sourceTpe :: computedTpe :: Nil),
              PathSelector(path) :: function :: Nil
            ) =>
          Configuration.At.Successful(
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
              plan.destTpe match {
                case tpe @ '[Option[a]] => Configuration.Const('{ None }, tpe)
                case _                  => plan
              }
          Configuration.At.Regional(path, Target.Dest, modifier, Span.fromPosition(cfg.pos)) :: Nil

        case DeprecatedConfig(configs) => configs

        case oopsie =>
          Configuration.At.Failed(
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
              Configuration.At.Successful(
                path.appended(Path.Segment.Field(source.tpe, fieldName)),
                Target.Dest,
                Configuration.FieldReplacement(sourceExpr, fieldName, source.tpe),
                span
              )
          }.toList
        }
        .getOrElse(
          Configuration.At
            .Failed(
              path,
              Target.Dest,
              "Field.allMatching only works when targeting a product or a function and supplying a product",
              span
            ) :: Nil
        )

    result match {
      case Nil =>
        Configuration.At.Failed(path, Target.Dest, "No matching fields found", span) :: Nil // TODO: Better error message
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
          Configuration.At.Successful(
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
          Configuration.At.Successful(
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
