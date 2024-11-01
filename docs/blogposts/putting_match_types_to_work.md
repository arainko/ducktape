## It's all matches and ducktape

### ...or in other words: finding

Ever since Scala 3 got released I've been enamored with match types but couldn't find much use for them besides 

```scala mdoc
import io.github.arainko.ducktape.*

extension [A <: Tuple] (self: A) 
  inline def parSequence[F[+x]](using Mode.Accumulating[F]): F[Tuple.InverseMap[A, F]] =
    self.fallibleTo[Tuple.InverseMap[A, F]]

val costam = 1
```
