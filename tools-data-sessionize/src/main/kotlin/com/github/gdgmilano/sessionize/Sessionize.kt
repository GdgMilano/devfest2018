package com.github.gdgmilano.sessionize

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon


private val klaxon = Klaxon()

/**
 * Sessionize
 *
 * GET https://sessionize.com/api/v2/y2kbnktu/view/all
 */
data class Sessionize(
  val sessions: List<Session>,
  val speakers: List<Speaker>,
  val questions: List<Any?>,
  val categories: List<Category>,
  val rooms: List<Room>
) {
  fun toJson() = klaxon.toJsonString(this)

  data class Category (
    val id: Long,
    val title: String,
    val items: List<Room>,
    val sort: Long
  )

  data class Room (
    val id: Long,
    val name: String,
    val sort: Long
  )

  data class Session (
    val id: String,
    val title: String,
    val description: String,
    val startsAt: String,
    val endsAt: String,
    val isServiceSession: Boolean,
    val isPlenumSession: Boolean,
    val speakers: List<String>,
    val categoryItems: List<Long>,
    val questionAnswers: List<Any?>,

    @Json(name = "roomId")
    val roomID: Long
  )

  data class Speaker (
    val id: String,
    val firstName: String,
    val lastName: String,
    val bio: String,
    val tagLine: String,
    val profilePicture: String,
    val isTopSpeaker: Boolean,
    val links: List<Link>,
    val sessions: List<Long>,
    val fullName: String
  )

  data class Link (
    val title: String,
    val url: String,
    val linkType: String
  )

  companion object {
    public fun fromJson(json: String) = klaxon.parse<Sessionize>(json)
  }
}
