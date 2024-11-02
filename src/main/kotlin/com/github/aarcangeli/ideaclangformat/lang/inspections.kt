package com.github.aarcangeli.ideaclangformat.lang

import com.intellij.codeInspection.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import org.jetbrains.yaml.schema.YamlJsonSchemaDeprecationInspection
import org.jetbrains.yaml.schema.YamlJsonSchemaHighlightingInspection

class ClangFormatSchemaHighlightingInspection : YamlJsonSchemaHighlightingInspection()

class ClangFormatSchemaDeprecationInspection : YamlJsonSchemaDeprecationInspection() {
  override fun doBuildVisitor(
    holder: ProblemsHolder,
    session: LocalInspectionToolSession,
    roots: MutableCollection<PsiElement>?,
    schema: JsonSchemaObject?
  ): PsiElementVisitor {
    // Hack to replace all problems with a deprecation highlight
    val wrapped = object : ProblemsHolder(holder.manager, holder.file, holder.isOnTheFly) {
      override fun registerProblem(problemDescriptor: ProblemDescriptor) {
        holder.registerProblem(
          problemDescriptor.psiElement,
          problemDescriptor.descriptionTemplate,
          ProblemHighlightType.LIKE_DEPRECATED
        )
      }
    }

    return super.doBuildVisitor(wrapped, session, roots, schema)
  }
}

// Suppress built-in schema validations because we have our own
val suppressedValidations = listOf("YAMLSchemaValidation", "YAMLSchemaDeprecation")

class ClangFormatInspectionSuppressor : InspectionSuppressor {
  override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
    if (element.containingFile is ClangFormatFile) {
      return toolId in suppressedValidations
    }
    return false
  }

  override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> {
    return arrayOf()
  }
}
