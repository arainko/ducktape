package io.github.arainko.ducktape

final class AppliedBuilder[A, B](value: A) {
  inline def transform(inline config: Field2[A, B] | Case2[A, B]*): B = ???
}
