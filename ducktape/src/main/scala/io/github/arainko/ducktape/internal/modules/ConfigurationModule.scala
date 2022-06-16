package io.github.arainko.ducktape.internal.modules

import scala.quoted.*
import io.github.arainko.ducktape.Configuration
import io.github.arainko.ducktape.Configuration.*
import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.Configuration.Product.Const

private[internal] trait ConfigurationModule { self: Module & SelectorModule & MirrorModule & FieldModule =>
  import quotes.reflect.*

  sealed trait MaterializedConfiguration

  object MaterializedConfiguration {
    enum Product extends MaterializedConfiguration {
      val destFieldName: String

      case Const(destFieldName: String, value: Expr[Any])
      case Computed(destFieldName: String, fuction: Expr[Any => Any])
      case Renamed(destFieldName: String, sourceFieldName: String)
    }

    enum Coproduct extends MaterializedConfiguration {
      val tpe: TypeRepr

      case Instance(tpe: TypeRepr, function: Expr[Any => Any])
    }

    def materialize[Source, Dest](config: Seq[Expr[FieldConfig[Source, Dest]]]) =
      config
        .map(materializeSingle)
        .groupBy(_.destFieldName)
        .map((_, fieldConfigs) => fieldConfigs.last) // keep the last applied field config only
        .toList

    private def materializeSingle[Source, Dest](config: Expr[FieldConfig[Source, Dest]]) =
      config match {
        case '{
              const[source, dest, fieldType, actualType](
                ${ FieldSelector(name) },
                $value
              )(using $ev1, $ev2, $ev3)
            } =>
          Product.Const(name, value)

        case '{
              computed[source, dest, fieldType, actualType](
                ${ FieldSelector(name) },
                $function
              )(using $ev1, $ev2, $ev3)
            } =>
          Product.Computed(name, function.asInstanceOf[Expr[Any => Any]])

        case '{
              renamed[source, dest, sourceFieldType, destFieldType](
                ${ FieldSelector(destFieldName) },
                ${ FieldSelector(sourceFieldName) }
              )(using $ev1, $ev2, $ev3)
            } =>
          Product.Renamed(destFieldName, sourceFieldName)

        case other => report.errorAndAbort(s"Unsupported field configuration: ${other.asTerm}")

        // case '{ instance[sourceSubtype][source, dest]($f)(using $ev1, $ev2) } => ???
      }

  }
}
