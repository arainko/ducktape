package io.github.arainko.ducktape.internal.modules

import scala.quoted.*

sealed trait Warning {
  def position(using Quotes): quotes.reflect.Position

  def render(using Quotes): String
}

object Warning {
  def emit(warning: Warning)(using Quotes): Unit = quotes.reflect.report.warning(warning.render, warning.position)

  final case class ConfiguredRepeatedly(config: MaterializedConfiguration, configType: Failure.ConfigType) extends Warning {
    override def position(using Quotes): quotes.reflect.Position = config.pos

    override def render(using Quotes): String = {
      val msg = message

      s"""
      |$msg
      |
      |If this is desired you can ignore this warning with @nowarn(msg=$msg)
      """.stripMargin
    }

    private def message(using Quotes) = s"${configType.name.capitalize} '$name' is configured multiple times"

    private def name(using Quotes) = config match {
      case prod: MaterializedConfiguration.Product                        => prod.destFieldName
      case coprod: MaterializedConfiguration.Coproduct                    => coprod.tpe.fullName
      case fallibeProd: MaterializedConfiguration.FallibleProduct[?]      => fallibeProd.destFieldName
      case fallibleCoprod: MaterializedConfiguration.FallibleCoproduct[?] => fallibleCoprod.tpe.fullName
    }
  }
}
