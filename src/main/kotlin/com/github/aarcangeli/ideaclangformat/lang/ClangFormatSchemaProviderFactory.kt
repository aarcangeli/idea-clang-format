package com.github.aarcangeli.ideaclangformat.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import org.jetbrains.annotations.Nls
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

private val schema = ClangFormatSchemaProviderFactory.readSchema()
private val clangFormatSchemaFile = LightVirtualFile("clangFormat-options.json", schema)

class ClangFormatSchemaProviderFactory : JsonSchemaProviderFactory, DumbAware {
  override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
    return listOf<JsonSchemaFileProvider>(ClangFormatSchemaProvider())
  }

  private class ClangFormatSchemaProvider : JsonSchemaFileProvider {
    override fun isAvailable(file: VirtualFile): Boolean {
      val fileType = file.fileType
      return fileType is LanguageFileType &&
        fileType.language.isKindOf(ClangFormatLanguage.INSTANCE)
    }

    override fun getName(): @Nls String {
      return "Clang Format Schema Provider"
    }

    override fun getSchemaFile(): VirtualFile {
      return clangFormatSchemaFile
    }

    override fun getSchemaType(): SchemaType {
      return SchemaType.embeddedSchema
    }

    override fun isUserVisible(): Boolean {
      return false
    }
  }

  companion object {
    fun readSchema(): String {
      val resourceAsStream =
        ClangFormatSchemaProviderFactory::class.java.getResourceAsStream("/schemas/clangFormat-options.json")
      try {
        resourceAsStream.use {
          return StreamUtil.readText(
            InputStreamReader(
              resourceAsStream!!,
              StandardCharsets.UTF_8
            )
          )
        }
      }
      catch (e: IOException) {
        throw RuntimeException("Cannot read schema", e)
      }
    }
  }
}
