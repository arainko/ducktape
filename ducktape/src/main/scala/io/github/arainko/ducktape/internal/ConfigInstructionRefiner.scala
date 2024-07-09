package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.Configuration.Instruction
import io.github.arainko.ducktape.internal.Configuration.*

private[ducktape] object ConfigInstructionRefiner {

  def run[F <: Fallible](instruction: Configuration.Instruction[F]): Configuration.Instruction[Nothing] | None.type =
    instruction match
      case inst @ Instruction.Static(_, _, config, _) =>
        config match
          case cfg: (Const | CaseComputed | FieldComputed | FieldReplacement)           => inst.copy(config = cfg)
          case fallible: (FallibleConst | FallibleFieldComputed | FallibleCaseComputed) => None
      case inst: (Instruction.Dynamic | Instruction.Bulk | Instruction.Regional | Instruction.Failed) => inst

}
