package com.github.gdgmilano

import com.github.gdgmilano.hoverboard.Hoverboard
import com.github.gdgmilano.sessionize.Sessionize
import com.github.gdgmilano.utils.makeSlug
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Config

const val isFirestoreBackupEnabled = true
const val isForceUpdateSessionize = true
const val isUpdateSpeakerData = true

const val sessionizeUrl = "https://sessionize.com/api/v2/y2kbnktu/view/all"

const val backupFolder = "backup/"
const val scheduleFilename = "${backupFolder}schedule.json"
const val sessionsFilename = "${backupFolder}sessions.json"
const val speakersFilename = "${backupFolder}speakers.json"
const val sessionizeFilename = "${backupFolder}sessionize.json"


// Main app

fun main(args: Array<String>) {
  val backupPrefix = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())

  val scheduleFile = File(scheduleFilename)
  val sessionsFile = File(sessionsFilename)
  val speakersFile = File(speakersFilename)
  val sessionizeFile = File(sessionizeFilename)

  // check requirement

  check(scheduleFile.exists()) { "Need $scheduleFilename, please run: download_schedule_info.sh" }
  check(sessionsFile.exists()) { "Need $sessionsFilename, please run: download_schedule_info.sh" }
  check(speakersFile.exists()) { "Need $speakersFilename, please run: download_schedule_info.sh" }

  if (isForceUpdateSessionize || !sessionizeFile.exists()) {
    sessionizeFile.writeText(
      requireNotNull(
        OkHttpClient()
          .newCall(Request.Builder().url(sessionizeUrl).get().build())
          .execute().body()?.string()
      ) { "Download failed for: $sessionizeUrl" }
    )
  }

  val scheduleOld = requireNotNull(Hoverboard.Schedule.fromJson(scheduleFile.readText()))
  val sessionsOld = requireNotNull(Hoverboard.Sessions.fromJson(sessionsFile.readText()))
  val speakersOld = requireNotNull(Hoverboard.Speakers.fromJson(speakersFile.readText()))
  val sessionize = requireNotNull(Sessionize.fromJson(sessionizeFile.readText()))


  // Build: Schedule

  val mapExtended = scheduleOld.day1.timeslots.flatMap { it.sessions }.filter { it.items.isNotEmpty() }.map { it.items.first() to it.extend }.toMap()

  class ScheduleLite(val startTime: String, val endTime: String, val roomId: Long, val sessionId: String)

  val scheduleLites = sessionize.sessions.map {
    ScheduleLite(
      it.startsAt.dropLast(3).takeLast(5),
      it.endsAt.dropLast(3).takeLast(5),
      it.roomID,
      makeSlug(it.title)
    )
  }
    .sortedBy { it.roomId }
    .groupBy { it.startTime }


  val scheduleNew = scheduleLites.values.map { schedules ->
    Hoverboard.Timeslot(
      startTime = schedules.first().startTime,
      sessions = sessionize.rooms.map { room ->
        schedules
          .firstOrNull { room.id == it.roomId }
          ?.let { Hoverboard.SessionKey(listOf(it.sessionId), mapExtended[it.sessionId]) }
          ?: Hoverboard.SessionKey(emptyList())
      },
      endTime = schedules.minBy { it.endTime }!!.endTime
    )
  }
    .let {
      Hoverboard.Schedule(
        Hoverboard.ScheduleDay(
          tracks = sessionize.rooms.map { Hoverboard.Track(it.name) },
          dateReadable = scheduleOld.day1.dateReadable,
          timeslots = it,
          date = scheduleOld.day1.date
        )
      )
    }

  if (scheduleNew != scheduleOld) {
    if (isFirestoreBackupEnabled) {
      scheduleFile.copyTo(File("$backupFolder/${backupPrefix}_schedule.json"))
    }
    scheduleFile.writeText(scheduleNew.toJson())
  }

  // Build: Sessions

  val mapSpeakerId = sessionize.speakers.map { it.id to makeSlug(it.fullName) }.toMap()
  val mapComplexity = mapOf(
    "Introductory and overview" to "Beginner",
    "Intermediate" to "Intermediate",
    "Advanced" to "Advanced"
  )

  val mapSessionFormat =
    sessionize.categories.first { it.title == "Session format" }.items.map { it.id to it.name }.toMap()
  val mapLevel = sessionize.categories.first { it.title == "Level" }.items.map { it.id to it.name }.toMap()
  val mapLanguage = sessionize.categories.first { it.title == "Language" }.items.map { it.id to it.name }.toMap()
  val mapTags = sessionize.categories.first { it.title == "Tags" }.items.map { it.id to it.name }.toMap()

  val sessionsNew = sessionize.sessions.map { session ->
    val sessionFormat = session.categoryItems.first { mapSessionFormat[it] != null }.let { mapSessionFormat[it]!! }
    val level = session.categoryItems.first { mapLevel[it] != null }.let { mapComplexity[mapLevel[it]]!! }
    val language = session.categoryItems.first { mapLanguage[it] != null }.let { mapLanguage[it]!! }
    val tags = session.categoryItems.mapNotNull { mapTags[it] }

    val sessionOld = sessionsOld.getOrDefault(session.id, null)
    if (sessionOld != null) {
      sessionOld.copy(
        language = language,
        description = session.description,
        complexity = level,
        tags = tags,
        speakers = session.speakers.map { mapSpeakerId[it]!! },
        title = session.title
      )
    } else {
      Hoverboard.Session(
        language = language,
        description = session.description,
        presentation = "",
        complexity = level,
        tags = tags,
        speakers = session.speakers.map { mapSpeakerId[it]!! },
        title = session.title,
        videoID = "",
        extend = null,
        icon = "",
        image = ""
      )
    }
  }
    .map { makeSlug(it.title) to it }
    .toMap()
    .let { Hoverboard.Sessions(it) }

  if (sessionsNew != sessionsOld) {
    if (isFirestoreBackupEnabled) {
      sessionsFile.copyTo(File("$backupFolder/${backupPrefix}_sessions.json"))
    }
    sessionsFile.writeText(sessionsNew.toJson())
  }


  // Build: Speakers

  val mapLinkIcon = mapOf("Twitter" to "twitter", "LinkedIn" to "linkedin")

  val speakersNew = sessionize.speakers.map { speaker ->

    val socialLinks = speaker.links.map { Hoverboard.Social(it.title, mapLinkIcon[it.title] ?: "website", it.url) }

    val slug = makeSlug(speaker.fullName)
    val speakerOld = speakersOld.getOrDefault(slug, null)
    if (speakerOld != null) {
      if (isUpdateSpeakerData) { // update old value
        speakerOld.copy(
          photoURL = speaker.profilePicture,
          name = speaker.fullName,
          photo = speaker.profilePicture,
          bio = speaker.bio,
          socials = socialLinks
        )
      } else speakerOld
    } else {
      Hoverboard.Speaker(
        shortBio = "",
        photoURL = speaker.profilePicture,
        name = speaker.fullName,
        companyLogo = "",
        title = speaker.tagLine,
        photo = speaker.profilePicture,
        order = 5,
        featured = false,
        company = "",
        companyLogoURL = "",
        country = "",
        bio = speaker.bio,
        socials = socialLinks,
        badges = emptyList()
      )
    }
  }
    .map { makeSlug(it.name) to it }
    .toMap()
    .let { Hoverboard.Speakers(it) }

  if (speakersNew != speakersOld) {
    if (isFirestoreBackupEnabled) {
      speakersFile.copyTo(File("$backupFolder/${backupPrefix}_speakers.json"))
    }
    speakersFile.writeText(speakersNew.toJson())
  }


  File("${backupFolder}social.txt").writeText(sessionsNew.map {
    val session = it.value
    if (session.language == "English") {
      "DevFest Milano 2018 - FREE Conference (20+ speaker)\nTalk: ${session.title} (by ${speakersNew[session.speakers!!.first()]!!.name})\nJoin now https://devfest2018.gdgmilano.it\n${session.tags?.map { "#${it.replace(" ", "")}" }?.joinToString(" ")} #DevFest18"
    } else {
      "DevFest Milano 2018 - Conferenza gratuita (20+ speaker)\nTalk: ${session.title} (by ${speakersNew[session.speakers!!.first()]!!.name})\nIscrivi ora su https://devfest2018.gdgmilano.it\n${session.tags?.map { "#${it.replace(" ", "")}" }?.joinToString(" ")} #DevFest18"


    }
  }.joinToString("\n\n"))

}

