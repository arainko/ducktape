package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.Debug
import io.github.arainko.ducktape.internal.modules.*

import scala.quoted.*

enum Configuration derives Debug {
  def tpe: Type[?]

  case Const(value: Expr[Any], tpe: Type[?])
  case Computed(tpe: Type[?], function: Expr[Any => Any])
  case FieldReplacement(source: Expr[Any], name: String, tpe: Type[?])
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

  enum At derives Debug {
    def path: Path
    def target: Target

    case Successful(path: Path, target: Target, config: Configuration)
    case Failed(path: Path, target: Target, message: String)
  }

  def parse[A: Type, B: Type, Args <: FunctionArguments](
    configs: Expr[Seq[Field2[A, B] | Case2[A, B] | Arg2[A, B, Args]]]
  )(using Quotes) = {
    import quotes.reflect.*

    Varargs
      .unapply(configs)
      .get // TODO: Make it nicer
      .view
      .map(_.asTerm)
      .flatMap {
        case Apply(
              TypeApply(Select(Ident("Field2"), "const"), a :: b :: destFieldTpe :: constTpe :: Nil),
              PathSelector(path) :: value :: Nil
            ) =>
          Configuration.At.Successful(
            path,
            Target.Dest,
            Configuration.Const(value.asExpr, value.tpe.asType)
          ) :: Nil

        case Apply(
              TypeApply(Select(Ident("Field2"), "computed"), a :: b :: destFieldTpe :: computedTpe :: Nil),
              PathSelector(path) :: function :: Nil
            ) =>
          Configuration.At.Successful(
            path,
            Target.Dest,
            Configuration.Computed(computedTpe.tpe.asType, function.asExpr.asInstanceOf[Expr[Any => Any]])
          ) :: Nil

        case Apply(
              TypeApply(Select(Ident("Field2"), "allMatching"), a :: b :: destFieldTpe :: fieldSourceTpe :: Nil),
              PathSelector(path) :: fieldSource :: Nil
            ) =>
          parseAllMatching(fieldSource.asExpr, path, destFieldTpe.tpe, fieldSourceTpe.tpe)

        case Apply(
              TypeApply(Select(Ident("Case2"), "const"), a :: b :: sourceTpe :: constTpe :: Nil),
              PathSelector(path) :: value :: Nil
            ) =>
          Configuration.At.Successful(
            path,
            Target.Source,
            Configuration.Const(value.asExpr, value.tpe.asType)
          ) :: Nil

        case Apply(
              TypeApply(Select(Ident("Arg2"), "const"), a :: b :: args :: destFieldTpe :: constTpe :: Nil),
              PathSelector(path) :: value :: Nil
            ) =>
          Configuration.At.Successful(
            path,
            Target.Dest,
            Configuration.Const(value.asExpr, value.tpe.asType)
          ) :: Nil

        case oopsie => report.errorAndAbort(oopsie.show(using Printer.TreeStructure), oopsie.pos)
      }
      .toList
  }

  private def parseAllMatching(using Quotes)(
    sourceExpr: Expr[Any],
    path: Path,
    destFieldTpe: quotes.reflect.TypeRepr,
    fieldSourceTpe: quotes.reflect.TypeRepr
  ) = {
    val result =
      Structure
        .fromTypeRepr(destFieldTpe)
        .as[Structure.Product | Structure.Function]
        .zip(Structure.fromTypeRepr(fieldSourceTpe).as[Structure.Product])
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
                Configuration.FieldReplacement(sourceExpr, fieldName, source.tpe)
              )
          }.toList
        }
        .getOrElse(
          Configuration.At
            .Failed(
              path,
              Target.Dest,
              "Field.allMatching only works when targeting a product or a function and supplying a product"
            ) :: Nil
        )

    result match {
      case Nil =>
        Configuration.At.Failed(path, Target.Dest, "No matching fields found") :: Nil // TODO: Better error message
      case configs => configs
    }
  }

}
