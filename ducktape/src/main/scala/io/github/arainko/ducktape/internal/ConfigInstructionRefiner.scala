package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.Configuration.*
import io.github.arainko.ducktape.*

private[ducktape] object ConfigInstructionRefiner {

  def run[F <: Fallible](instruction: Configuration.Instruction[F]): Configuration.Instruction[Nothing] | None.type =
    instruction match
      case inst @ Instruction.Static(_, _, config, _) =>
        config match
          case cfg: (Const | CaseComputed | FieldComputed | FieldReplacement)           => inst.copy(config = cfg)
          case fallible: (FallibleConst | FallibleFieldComputed | FallibleCaseComputed) => None
      case inst: (Instruction.Dynamic | Instruction.Bulk | Instruction.Regional | Instruction.Failed) => inst

}

object Common {
  given Transformer.Fallible[Option, Int, String] = a => Some(a.toString)
}

case class Costam1(int: Int)
case class Costam2(int: Option[String])

object test {
  import Common.given
  Mode.FailFast.option.locally {
    Costam1(1).fallibleTo[Costam2]
  }
}
