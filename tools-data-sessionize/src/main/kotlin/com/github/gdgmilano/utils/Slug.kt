package com.github.gdgmilano.utils

import java.text.Normalizer

private val NONLATIN = "[^\\w-]".toRegex()
private val WHITESPACE = "[\\s]".toRegex()

fun makeSlug(input: String): String {
  val nowhitespace = WHITESPACE.replace(input, "_")
  val normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD)
  return NONLATIN.replace(normalized, "").toLowerCase()
}
