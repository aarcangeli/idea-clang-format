package com.github.aarcangeli.ideaclangformat.lang

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapi.project.DumbAware
import org.jetbrains.yaml.schema.YamlJsonSchemaCompletionContributor

class ClangFormatCompletionContributor : YamlJsonSchemaCompletionContributor(), DumbAware {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (result.prefixMatcher.prefix == "") {
      result.restartCompletionOnAnyPrefixChange()
    }

    // case-insensitive completion
    super.fillCompletionVariants(parameters, result.caseInsensitive())
  }
}
