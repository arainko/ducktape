package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.Transformer

final case class RenamedField(fromField: FieldName, transformer: Transformer[Any, Any])
