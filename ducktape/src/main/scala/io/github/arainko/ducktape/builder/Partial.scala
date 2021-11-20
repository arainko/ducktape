package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.builder.*
import io.github.arainko.ducktape.internal.*

import scala.compiletime.*

object Partial:

  final class WithFieldConst[
    SpecificBuilder[_, _, _ <: Tuple, _ <: Tuple, _ <: Tuple, _ <: Tuple],
    From,
    To,
    FromSubcases <: Tuple,
    ToSubcases <: Tuple,
    DerivedFromSubcases <: Tuple,
    DerivedToSubcases <: Tuple,
    Label <: String
  ](
    private val builder: Builder[
      SpecificBuilder,
      From,
      To,
      FromSubcases,
      ToSubcases,
      DerivedFromSubcases,
      DerivedToSubcases,
    ]
  ):

    inline def apply[FieldType](const: FieldType): SpecificBuilder[
      From,
      To,
      FromSubcases,
      ToSubcases,
      Field.DropByLabel[Label, DerivedFromSubcases],
      Field.DropByLabel[Label, DerivedToSubcases]
    ] = {
      Macros.verifyFieldExists[Label, ToSubcases, To]
      Macros.verifyTypes[FieldType, Field.TypeForLabel[Label, ToSubcases]]
      builder.construct(constants = builder.constants + (FieldName(constValue[Label]) -> const))
    }

  end WithFieldConst

  final class WithFieldComputed[
    SpecificBuilder[_, _, _ <: Tuple, _ <: Tuple, _ <: Tuple, _ <: Tuple],
    From,
    To,
    FromSubcases <: Tuple,
    ToSubcases <: Tuple,
    DerivedFromSubcases <: Tuple,
    DerivedToSubcases <: Tuple,
    Label <: String
  ](
    private val builder: Builder[
      SpecificBuilder,
      From,
      To,
      FromSubcases,
      ToSubcases,
      DerivedFromSubcases,
      DerivedToSubcases,
    ]
  ):

    inline def apply[FieldType](f: From => FieldType): SpecificBuilder[
      From,
      To,
      FromSubcases,
      ToSubcases,
      Field.DropByLabel[Label, DerivedFromSubcases],
      Field.DropByLabel[Label, DerivedToSubcases]
    ] = {
      Macros.verifyFieldExists[Label, ToSubcases, To]
      Macros.verifyTypes[FieldType, Field.TypeForLabel[Label, ToSubcases]]

      builder.construct(
        computeds = builder.computeds + (FieldName(constValue[Label]) -> f.asInstanceOf[Any => Any])
      )
    }

  end WithFieldComputed

end Partial
