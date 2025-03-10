package com.apollographql.apollo3.compiler.codegen.java

import com.apollographql.apollo3.compiler.CodegenType
import com.apollographql.apollo3.compiler.PackageNameGenerator
import com.apollographql.apollo3.compiler.codegen.CodegenLayout
import com.apollographql.apollo3.compiler.internal.escapeTypeReservedWord
import com.apollographql.apollo3.compiler.internal.escapeJavaReservedWord

internal class JavaCodegenLayout(
    allTypes: List<CodegenType>,
    packageNameGenerator: PackageNameGenerator,
    schemaPackageName: String,
    useSemanticNaming: Boolean,
    decapitalizeFields: Boolean,
) : CodegenLayout(
    allTypes,
    packageNameGenerator,
    schemaPackageName,
    useSemanticNaming,
    decapitalizeFields,
) {
  override fun escapeReservedWord(word: String): String = word.escapeJavaReservedWord()

  // We used to write upper case enum values but the server can define different values with different cases
  // See https://github.com/apollographql/apollo-android/issues/3035
  internal fun enumValueName(name: String) = name.escapeTypeReservedWord() ?: regularIdentifier(name)

  fun builderPackageName(): String = "${typePackageName()}.builder"

  fun utilPackageName() = "$schemaPackageName.util"
}
