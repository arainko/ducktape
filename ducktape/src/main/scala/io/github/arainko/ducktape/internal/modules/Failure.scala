package io.github.arainko.ducktape.internal.modules

import scala.quoted.*

private[ducktape] sealed trait Failure {
  def position(using Quotes): quotes.reflect.Position =
    quotes.reflect.Position.ofMacroExpansion

  def render(using Quotes): String
}

private[ducktape] object Failure {

  def abort(failure: Failure)(using Quotes): Nothing =
    quotes.reflect.report.errorAndAbort(failure.render, failure.position)

  private given (using quotes: Quotes): quotes.reflect.Printer[quotes.reflect.Tree] =
    quotes.reflect.Printer.TreeShortCode

  private given (using quotes: Quotes): quotes.reflect.Printer[quotes.reflect.TypeRepr] =
    quotes.reflect.Printer.TypeReprShortCode

  enum ConfigType {
    final def name: "field" | "case" | "arg" =
      this match {
        case Field => "field"
        case Case  => "case"
        case Arg   => "arg"
      }

    case Field, Case, Arg
  }

  final case class MirrorMaterialization(mirroredType: Type[?], notFoundTypeMemberName: String) extends Failure {

    override final def render(using Quotes): String = {
      import quotes.reflect.*

      s"""
        |Mirror materialization for ${mirroredType.show} failed. 
        |Member type not found: '$notFoundTypeMemberName'.
        """.stripMargin
    }
  }

  final case class InvalidFieldSelector(
    selector: Expr[Any],
    sourceTpe: Type[?],
    suggestedFields: List[Suggestion]
  ) extends Failure {
    override final def position(using Quotes): quotes.reflect.Position = selector.pos

    override final def render(using Quotes): String =
      s"""
        |'${selector.show}' is not a valid field selector for ${sourceTpe.show}.
        |Try one of these: ${Suggestion.renderAll(suggestedFields)}
        """.stripMargin
  }

  enum InvalidArgSelector extends Failure {
    override final def position(using Quotes): quotes.reflect.Position =
      this match {
        case NotFound(selector, _, _)      => selector.pos
        case NotAnArgSelector(selector, _) => selector.pos
      }

    override final def render(using Quotes): String =
      this match {
        case NotFound(_, argName, suggestedArgs) =>
          s"""
            |'_.$argName' is not a valid argument selector.
            |Try one of these: ${Suggestion.renderAll(suggestedArgs)}
        """.stripMargin
        case NotAnArgSelector(_, suggestedArgs) =>
          s"""
              |Not a valid argument selector.
              |Try one of these: ${Suggestion.renderAll(suggestedArgs)}
        """.stripMargin
      }

    case NotFound(selector: Expr[Any], argumentName: String, suggestedArgs: List[Suggestion])

    case NotAnArgSelector(selector: Expr[Any], suggestedArgs: List[Suggestion])
  }

  final case class UnsupportedConfig(config: Expr[Any], configFor: ConfigType) extends Failure {
    private def fieldOrArgSuggestions(fieldOrArg: Failure.ConfigType.Arg.type | Failure.ConfigType.Field.type) = {
      val capitalized = fieldOrArg.name.capitalize
      Suggestion.all(
        s"""${capitalized}.const(_.${fieldOrArg}Name, "value")""",
        s"""${capitalized}.computed(_.${fieldOrArg}Name, source => source.value)""",
        s"""${capitalized}.renamed(_.${fieldOrArg}Name1, _.${fieldOrArg}Name2)""",
        s"""${capitalized}.default(_.${fieldOrArg}Name)"""
      )
    }

    private val caseSuggestions = Suggestion.all(
      """Case.const[SourceSubtype.type](SourceSubtype.value)""",
      """Case.computed[SourceSubtype.type](source => source.value)"""
    )

    private val suggestions =
      configFor match {
        case field: Failure.ConfigType.Field.type => fieldOrArgSuggestions(field)
        case arg: Failure.ConfigType.Arg.type     => fieldOrArgSuggestions(arg)
        case Failure.ConfigType.Case              => caseSuggestions
      }

    override final def position(using Quotes) = config.pos

    override final def render(using Quotes): String =
      s"""
        |'${config.show}' is not a supported $configFor configuration expression.
        |Try one of these: ${Suggestion.renderAll(suggestions)}
        |
        |Please note that you HAVE to use these directly as variadic arguments (not through a proxy method,
        |not with the splash operator (eg. Seq()*) etc.).
        """.stripMargin
  }

  final case class NoFieldMapping(fieldName: String, sourceType: Type[?]) extends Failure {
    override final def render(using Quotes): String = s"No field named '$fieldName' found in ${sourceType.show}"
  }

  final case class DefaultMissing(fieldName: String, destType: Type[?]) extends Failure {
    override final def render(using Quotes): String = s"No default value for '$fieldName' found in ${destType.show}"
  }

  final case class InvalidDefaultType(defaultField: Field, destType: Type[?]) extends Failure {
    override final def render(using Quotes): String = s"The default value of '${destType.show}.${defaultField.name}' is not a subtype of ${defaultField.tpe.show}"
  }

  final case class NoChildMapping(childName: String, destinationType: Type[?]) extends Failure {
    override final def render(using Quotes): String = s"No child named '$childName' found in ${destinationType.show}"
  }

  final case class CannotMaterializeSingleton(tpe: Type[?]) extends Failure {
    private def suggestions(using Quotes) = Suggestion.all(s"${tpe.show} is not a singleton type")

    override final def render(using Quotes): String =
      s"""
        |Cannot materialize singleton for ${tpe.show}.
        |Possible causes: ${Suggestion.renderAll(suggestions)}
        """.stripMargin
  }

  final case class FieldSourceMatchesNoneOfDestFields(config: Expr[Any], fieldSourceTpe: Type[?], destTpe: Type[?])
      extends Failure {

    override def position(using Quotes): quotes.reflect.Position = config.pos
    override def render(using Quotes): String =
      s"""
      |None of the fields from ${fieldSourceTpe.show} match any of the fields from ${destTpe.show}.""".stripMargin
  }

  final case class TransformerNotFound(sourceField: Field, destField: Field, implicitSearchExplanation: String) extends Failure {
    override def render(using Quotes): String =
      s"""
      |No instance of Transformer[${sourceField.tpe.show}, ${destField.tpe.show}] was found.
      |
      |Compiler supplied explanation (may or may not be helpful):
      |$implicitSearchExplanation
      """.stripMargin
  }

  extension (tpe: Type[?]) {
    private def show(using Quotes): String = quotes.reflect.TypeRepr.of(using tpe).show
  }

  extension (expr: Expr[Any]) {
    private def show(using Quotes): String = quotes.reflect.asTerm(expr).show

    private def pos(using Quotes): quotes.reflect.Position = quotes.reflect.asTerm(expr).pos
  }
}
