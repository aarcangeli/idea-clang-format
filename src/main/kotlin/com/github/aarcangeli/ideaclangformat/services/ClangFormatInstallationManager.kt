package com.github.aarcangeli.ideaclangformat.services

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.util.system.CpuArch
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path

private const val url = "https://pypi.org/pypi/clang-format/json"

private val objectMapper = ObjectMapper()
  .registerKotlinModule()
  .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

@Service(Service.Level.APP)
class ClangFormatInstallationManager {
  private val clangFormatDirectory: Path = Path.of(PathManager.getSystemPath(), "clang-format")
  private var releaseList: List<Release>

  init {
    releaseList = downloadReleaseList()
  }

  fun getReleaseList(): List<Release> {
    return releaseList
  }

  private fun downloadReleaseList(): List<Release> {
    // download versions from pypi
    val response = HttpClient.newBuilder()
      .build()
      .send(
        HttpRequest.newBuilder()
          .uri(URI.create(url))
          .build(), HttpResponse.BodyHandlers.ofString()
      )
    val versionsJson = response.body()
    val versions = objectMapper.readTree(versionsJson)
    val releaseList = mutableListOf<Release>()
    for (version in versions["releases"].fields()) {
      val release = findPlatform(version.value) ?: continue
      releaseList.add(
        Release(
          version.key,
          release["filename"].asText(),
          release["url"].asText(),
          release["md5_digest"].asText(),
          release["size"].asInt()
        )
      )
    }
    // sort by version
    releaseList.sortWith(Comparator.comparing(Release::version, NaturalComparator.INSTANCE).reversed())
    return releaseList
  }

  private fun findPlatform(value: JsonNode): JsonNode? {
    val platform = when {
      SystemInfo.isWindows && CpuArch.CURRENT.width == 64 -> "-win_amd64"
      SystemInfo.isWindows && CpuArch.CURRENT.width == 32 -> "-win32"
      SystemInfo.isLinux && CpuArch.CURRENT.width == 64 -> "-manylinux1_x86_64"
      SystemInfo.isLinux && CpuArch.CURRENT.width == 32 -> "-manylinux1_i686"
      SystemInfo.isMac -> "-macosx"
      else -> return null
    }
    for (release in value) {
      if (release["filename"].asText().contains(platform)) {
        return release
      }
    }
    return null
  }
}

data class Release(
  val version: String,
  val filename: String,
  val url: String,
  val md5: String,
  val size: Int
)
