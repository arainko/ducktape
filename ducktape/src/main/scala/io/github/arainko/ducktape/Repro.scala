package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.macros.LiftTransformation

object Repro {
  final case class ToplevelPrimitive(value: ReproPrimitive)
  final case class ToplevelComplex(value: ReproComplex)

  final case class ReproPrimitive(name: String)
  final case class ReproComplex(name: Name)

  final case class Name(value: String)

  given Transformer[String, Name] = str => Name(str + "-LOCAL")

  val primitive: ToplevelPrimitive = ???

  inline def manualTransformer = Transformer.ForProduct.make[ToplevelPrimitive, ToplevelComplex](
    (source: ToplevelPrimitive) =>
      new ToplevelComplex(value = 
        new ReproComplex(
          name = given_Transformer_String_Name.transform(source.value.name)
        )  
      )
  )


  Transformer.Debug.showCode {

  LiftTransformation.run(manualTransformer, primitive)
  }

  Transformer.Debug.showCode {
  primitive.into[ToplevelComplex].transform()  
  }
}
