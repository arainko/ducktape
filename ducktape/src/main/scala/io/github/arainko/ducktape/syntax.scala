package io.github.arainko.ducktape

import io.github.arainko.ducktape.builder.*
import io.github.arainko.ducktape.function.*
import io.github.arainko.ducktape.internal.macros.*

import scala.annotation.targetName
import scala.deriving.Mirror
import io.github.arainko.ducktape.internal.standalone.LiftTransformation

extension [Source](value: Source) {
  def into[Dest]: AppliedBuilder[Source, Dest] = AppliedBuilder(value)

  // This is supposed to supersede `.to` but the macro barfs errors when the derivation of Transformer fails - it prints the expected message but also
  // yells about a match error that's coming from StripNoisyNodes (I presume a failed derivation inserts an EmptyTree in place of the Transformer
  // and EmptyTrees are not handled by TreeMap) - need to figure out a nice way of handling that error (a try {...} catch { case err: MatchError => ... }
  // works but there's gotta be a better way)
  //
  // On hold until I get to the bottom of this.
  
  inline def transformInto[Dest](using inline transformer: Transformer[Source, Dest]) =
    ${ LiftTransformation.liftTransformation('transformer, 'value) }
   

  def to[Dest](using Transformer[Source, Dest]): Dest = Transformer[Source, Dest].transform(value)

  transparent inline def intoVia[Func](inline function: Func)(using Mirror.ProductOf[Source], FunctionMirror[Func]) =
    AppliedViaBuilder.create(value, function)

  inline def via[Func](inline function: Func)(using
    Func: FunctionMirror[Func],
    Source: Mirror.ProductOf[Source]
  ): Func.Return = ProductTransformerMacros.via(value, function)
}
