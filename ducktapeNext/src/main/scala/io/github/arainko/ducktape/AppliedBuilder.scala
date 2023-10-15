package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.PlanInterpreter

final class AppliedBuilder[Source, Dest](value: Source) {
  inline def transform(inline config: Field[Source, Dest] | Case[Source, Dest]*): Dest = 
    PlanInterpreter.transformBetween[Source, Dest](value, config*)
}
