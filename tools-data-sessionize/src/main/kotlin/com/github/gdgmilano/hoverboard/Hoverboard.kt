package com.github.gdgmilano.hoverboard

// To parse the JSON, install Klaxon and do:
//

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import java.io.StringReader

private val klaxon = Klaxon()

inline fun <reified T> String.asMap(): Map<String, T> {
  val klaxon = Klaxon()
  return klaxon.parseJsonObject(StringReader(this)).let { map ->
    map.keys.map { it to klaxon.parseFromJsonObject<T>(map.obj(it)!!)!! }.toMap()
  }
}

object Hoverboard {
  class Sessions(elements: Map<String, Session>) : HashMap<String, Session>(elements) {
    fun toJson() = klaxon.toJsonString(this)

    companion object {
      fun fromJson(json: String) = Sessions(json.asMap())
    }
  }

  class Speakers(elements: Map<String, Speaker>) : HashMap<String, Speaker>(elements) {
    fun toJson() = klaxon.toJsonString(this)

    companion object {
      fun fromJson(json: String) = Speakers(json.asMap())

    }
  }

  data class Schedule(
    @Json(name = "2018-10-06")
    val day1: ScheduleDay
  ) {
    fun toJson() = klaxon.toJsonString(this)

    companion object {
      fun fromJson(json: String) = klaxon.parse<Schedule>(json)
    }
  }

  data class ScheduleDay(
    val tracks: List<Track>,
    val dateReadable: String,
    val timeslots: List<Timeslot>,
    val date: String
  )

  data class Timeslot(
    val startTime: String,
    val sessions: List<SessionKey>,
    val endTime: String
  )

  data class SessionKey(
    val items: List<String>,
    val extend: Long? = null
  )

  data class Track(
    val title: String
  )

  data class Session(
    val language: String? = null,
    val description: String,
    val presentation: String? = null,
    val complexity: String? = null,
    val tags: List<String>? = null,
    val speakers: List<String>? = null,
    val title: String,

    @Json(name = "videoId")
    val videoID: String? = null,

    val extend: Long? = null,
    val icon: String? = null,
    val image: String? = null
  )

  data class Speaker(
    val shortBio: String,

    @Json(name = "photoUrl")
    val photoURL: String,

    val name: String,
    val companyLogo: String,
    val title: String,
    val photo: String,
    val order: Long,
    val featured: Boolean,
    val company: String,

    @Json(name = "companyLogoUrl")
    val companyLogoURL: String,

    val country: String,
    val bio: String,
    val socials: List<Social>? = null,
    val badges: List<Badge>? = null
  )

  data class Badge(
    val name: String,
    val description: String
  )

  data class Social(
    val name: String,
    val icon: String,
    val link: String
  )
}

