package io.github.arainko.ducktape.internal

import scala.quoted.*
import io.github.arainko.ducktape.*
import Configuration.*

sealed trait ConfigParser[+F <: Fallible] {
  def apply(using Quotes): PartialFunction[quotes.reflect.Term, Instruction[F]]
}

object ConfigParser {
  def combine[F <: Fallible](parsers: NonEmptyList[ConfigParser[F]])(using Quotes):  PartialFunction[quotes.reflect.Term, Instruction[F]] =
    parsers.map(_.apply).reduceLeft(_ orElse _)
    
  object Total extends ConfigParser[Nothing] {
    def apply(using Quotes): PartialFunction[quotes.reflect.Term, Instruction[Nothing]] = {
      import quotes.reflect.*
      {
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
          def default(parent: Plan[Plan.Error, Fallible] | None.type) =
            for {
              selectedField <-
                path.segments.lastOption
                  .flatMap(_.narrow[Path.Segment.Field])
                  .toRight("Selected path's length should be at least 1")
              defaults <-
                PartialFunction
                  .condOpt(parent) { case parent: Plan.BetweenProducts[Plan.Error, Fallible] => parent.dest.defaults }
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
      }
    }
  }

  object Fallible extends ConfigParser[Fallible] {
    def apply(using Quotes): PartialFunction[quotes.reflect.Term, Instruction[Fallible]] = {
      import quotes.reflect.*
      {
        case cfg @ Apply(
              TypeApply(Select(IdentOfType('[Field.type]), "fallibleConst"), f :: a :: b :: destFieldTpe :: constTpe :: Nil),
              PathSelector(path) :: value :: Nil
            ) =>
        Configuration.Instruction.Static(
          path,
          Side.Dest,
          Configuration.FallibleConst(value.asExpr, constTpe.tpe.asType),
          Span.fromPosition(cfg.pos)
        )
        case cfg @ Apply(
              TypeApply(Select(IdentOfType('[Field.type]), "fallibleComputed"), f :: a :: b :: destFieldTpe :: computedTpe :: Nil),
              PathSelector(path) :: function :: Nil
            ) =>
            println(computedTpe.tpe.show)
          Configuration.Instruction.Static(
            path,
            Side.Dest,
            Configuration.FallibleComputed(computedTpe.tpe.asType, function.asExpr.asInstanceOf[Expr[Any => Any]]),
            Span.fromPosition(cfg.pos)
          )
      }
    }
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
