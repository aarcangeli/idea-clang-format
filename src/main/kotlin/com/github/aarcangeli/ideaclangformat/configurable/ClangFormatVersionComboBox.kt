package com.github.aarcangeli.ideaclangformat.configurable

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.ui.ColoredListCellRenderer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JPanel

private const val url = "https://pypi.org/pypi/clang-format/json"

private val objectMapper = ObjectMapper()
  .registerKotlinModule()
  .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

class ClangFormatVersionComboBox : JPanel() {
  private val comboBox = MyComboBox()

  init {
    comboBox.renderer = MyCellRenderer()
    add(comboBox)
    updateComboBox()

    downloadReleaseList()
  }

  private fun updateComboBox() {
    comboBox.removeAllItems()
    comboBox.addItem("1.0")
    comboBox.addItem("2.0")
    comboBox.addItem("3.0")
  }

  private fun downloadReleaseList() {
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
    versions["releases"].fieldNames().forEachRemaining {
      comboBox.addItem(it)
    }
  }

  private class MyCellRenderer : ColoredListCellRenderer<Any>() {
    override fun customizeCellRenderer(list: JList<out Any>, value: Any?, index: Int, selected: Boolean, hasFocus: Boolean) {
      append("Version $value")
    }
  }

  private class MyComboBox : JComboBox<Any>() {
    override fun setSelectedItem(anObject: Any?) {
      //if (anObject is UnrealEngineInstallation) {
      //  super.setSelectedItem(anObject)
      //}
      //else if (anObject is AddSdkAction) {
      //  addSdk()
      //}
    }
  }
}
