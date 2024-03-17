## Coming from 0.1.x

While `ducktape 0.2.x` is not binary-compatible with `ducktape 0.1.x` it tries to be as source-compatible as possible with a few caveats (the following is a non-exhaustive list of source-incompatible changes that have a chance to be visible by the end users):

* instances of `Transformers` and `Transformer.Fallible` are NOT auto-deriveable anymore. Any code that relies on auto derivation of these should switch to `Transformer.Derived` and `Transformer.Fallible.Derived`,
* given definitions inside the companion of `Transformer` and `Transformer.Fallible` (like `Transformer.betweenNonOptionOption` etc) are gone and should be replaced with calls to `Transformer.derive` and `Transformer.Fallible.derive` with appropriate types as the type arguments,
* the signature of `Mode[F]#traverseCollection` has changed from 
```scala
 def traverseCollection[A, B, AColl[x] <: Iterable[x], BColl[x] <: Iterable[x]](collection: AColl[A])(using
    transformer: FallibleTransformer[F, A, B],
    factory: Factory[B, BColl[B]]
  ): F[BColl[B]]
```
to
```scala
def traverseCollection[A, B, AColl <: Iterable[A], BColl <: Iterable[B]](
    collection: AColl,
    transformation: A => F[B]
  )(using factory: Factory[B, BColl]): F[BColl]
```
* `BuilderConfig[A, B]` is replaced by the union of `Field[A, B]` and `Case[A, B]`, while `ArgBuilderConfig[A, B]` is replaced with `Field[A, B]`,
* `FunctionMirror` is gone with no replacement (it was pretty much a leaking impl detail).
