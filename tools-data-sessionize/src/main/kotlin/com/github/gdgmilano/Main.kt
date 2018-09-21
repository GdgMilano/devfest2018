package com.github.gdgmilano

import com.github.gdgmilano.hoverboard.Hoverboard
import com.github.gdgmilano.sessionize.Sessionize
import com.github.gdgmilano.utils.makeSlug
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

fun main(args: Array<String>) {
  // Main.sessionizeToHoverboard()
  // Main.buildSocialMessage()
  Main.buildAgenda()
}

object Main {
  // Config

  private const val isFirestoreBackupEnabled = true
  private const val isForceUpdateSessionize = true
  private const val isUpdateSpeakerData = true

  private const val sessionizeUrl = "https://sessionize.com/api/v2/y2kbnktu/view/all"

  private const val backupFolder = "backup/"
  private const val scheduleFilename = "${backupFolder}schedule.json"
  private const val sessionsFilename = "${backupFolder}sessions.json"
  private const val speakersFilename = "${backupFolder}speakers.json"
  private const val sessionizeFilename = "${backupFolder}sessionize.json"

  private val backupPrefix = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())

  private val scheduleFile = File(scheduleFilename)
  private val sessionsFile = File(sessionsFilename)
  private val speakersFile = File(speakersFilename)
  private val sessionizeFile = File(sessionizeFilename)

  private val scheduleOld by lazy {
    check(scheduleFile.exists()) { "Need $scheduleFilename, please run: download_schedule_info.sh" }
    requireNotNull(Hoverboard.Schedule.fromJson(scheduleFile.readText()))
  }
  private val sessionsOld by lazy {
    check(sessionsFile.exists()) { "Need $sessionsFilename, please run: download_schedule_info.sh" }
    requireNotNull(Hoverboard.Sessions.fromJson(sessionsFile.readText()))
  }
  private val speakersOld by lazy {
    check(speakersFile.exists()) { "Need $speakersFilename, please run: download_schedule_info.sh" }
    requireNotNull(Hoverboard.Speakers.fromJson(speakersFile.readText()))
  }
  private val sessionize by lazy {
    if (isForceUpdateSessionize || !sessionizeFile.exists()) {
      sessionizeFile.writeText(
        requireNotNull(
          OkHttpClient()
            .newCall(Request.Builder().url(sessionizeUrl).get().build())
            .execute().body()?.string()
        ) { "Download failed for: $sessionizeUrl" }
      )
    }
    requireNotNull(Sessionize.fromJson(sessionizeFile.readText()))
  }


  fun sessionizeToHoverboard() {

    // Build: Schedule

    val mapExtended = scheduleOld.day1.timeslots.flatMap { it.sessions }.filter { it.items.isNotEmpty() }
      .map { it.items.first() to it.extend }.toMap()

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
    val mapLanguageFlag = mapOf("Italian" to "🇮🇹", "English" to "🇬🇧")
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
          languageFlag = mapLanguageFlag[language],
          description = session.description,
          complexity = level,
          tags = tags,
          speakers = session.speakers.map { mapSpeakerId[it]!! },
          title = session.title
        )
      } else {
        Hoverboard.Session(
          language = language,
          languageFlag = mapLanguageFlag[language],
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
  }

  fun buildSocialMessage() {
    File("${backupFolder}social.txt").writeText(sessionsOld.map {
      val session = it.value
      if (session.language == "English") {
        "DevFest Milano 2018, this year: https://devfest2018.gdgmilano.it/schedule/2018-10-06?sessionId=${makeSlug(
          session.title
        )}\nTalk by ${session.speakers!!.map { speakersOld[it]!!.name }.joinToString()} on ${session.tags?.map {
          "#${it.replace(
            " ",
            ""
          )}"
        }?.joinToString(" ")}\nJoin now - FREE Conference (20+ speaker) #DevFest18"
      } else {
        "DevFest Milano 2018, quest'anno: https://devfest2018.gdgmilano.it/schedule/2018-10-06?sessionId=${makeSlug(
          session.title
        )}\nTalk di ${session.speakers!!.map { speakersOld[it]!!.name }.joinToString()} su ${session.tags?.map {
          "#${it.replace(
            " ",
            ""
          )}"
        }?.joinToString(" ")}\nIscriviti ora - Conferenza gratuita (20+ speaker) #DevFest18"
      }
    }.joinToString("\n\n"))
  }

  fun buildAgenda() {
    File("${backupFolder}agenda-by-tag.txt").run {
      val mainTags = listOf("Android", "Machine Learning", "Firebase")
      val tagMap = (mainTags + "other").associate { it to mutableListOf<String>() }
      val tagOtherSet = mutableSetOf<String>()

      scheduleOld.day1.timeslots.forEach {
        val sessions = it.sessions.filter { it.items.isNotEmpty() }.map { sessionsOld[it.items.first()]!! }
        sessions.forEach {
          if (it.speakers != null) {
            val speakers = it.speakers!!.map { speakersOld[it]!!.name }.joinToString()
            val tags = it.tags!!.map { "#${it.replace(" ", "")}" }.joinToString(" ")
            val talk = "${it.title} (by $speakers)\n    $tags"
            if (mainTags.any { t -> t in it.tags }) {
              mainTags.forEach { t ->
                if (t in it.tags) tagMap[t]!!.add(talk)
              }
            } else {
              tagMap["other"]!!.add(talk)
              tagOtherSet.addAll(it.tags)
            }
          }
        }
      }
      val otherTags = tagOtherSet.map { "#${it.replace(" ", "")}" }.joinToString(" ")
      writeText(mainTags.map {
        val currentTag = "#${it.replace(" ", "")}"
        "Session with $currentTag\n\n${tagMap[it]!!.joinToString("\n")}"
      }.joinToString("\n\n\n") + "\n\n\nSession with: $otherTags\n\n${tagMap["other"]!!.joinToString("\n")}")
    }

    File("${backupFolder}agenda-full.txt").writeText(scheduleOld.day1.timeslots.map {
      val sessions = it.sessions.filter { it.items.isNotEmpty() }.map { sessionsOld[it.items.first()]!! }
      "${it.startTime}\n\n${
      sessions.map {
        if (it.speakers != null) {
          val speakers = it.speakers!!.map { val speaker = speakersOld[it]!!
            "${speaker.name} (${speaker.title} @ ${speaker.company})"
          }.joinToString()
          val tags = it.tags!!.map { "#${it.replace(" ", "")}" }.joinToString(" ")
          val url = "https://devfest2018.gdgmilano.it/schedule/2018-10-06?sessionId=${makeSlug(it.title)}"
          "${it.title}\n    $tags\n    by $speakers\n"
        } else {
          it.title
        }
      }.joinToString("\n")
      }"
    }.joinToString("\n\n"))

    File("${backupFolder}agenda-compat.txt").writeText(scheduleOld.day1.timeslots.map {
      val sessions = it.sessions.filter { it.items.isNotEmpty() }.map { sessionsOld[it.items.first()]!! }
      "${it.startTime}\n${
      sessions.map {
        if (it.speakers != null) {
          val speakers = it.speakers!!.map { speakersOld[it]!!.name }.joinToString()
          val tags = it.tags!!.map { "#${it.replace(" ", "")}" }.joinToString(" ")
          "    ${it.title} (by $speakers)"
        } else {
          "    ${it.title}"
        }
      }.joinToString("\n")
      }"
    }.joinToString("\n"))

    File("${backupFolder}agenda-compat-only-talk.txt").writeText(scheduleOld.day1.timeslots.mapNotNull {
      val sessions = it.sessions.filter { it.items.isNotEmpty() }.map { sessionsOld[it.items.first()]!! }
      if (sessions.any { it.speakers != null }) {
        "${it.startTime}\n${
        sessions.mapNotNull {
          if (it.speakers != null) {
            val speakers = it.speakers!!.map { speakersOld[it]!!.name }.joinToString()
            val tags = it.tags!!.map { "#${it.replace(" ", "")}" }.joinToString(" ")
            "    ${it.title} (by $speakers)"
          } else null
        }.joinToString("\n")
        }"
      } else null
    }.joinToString("\n"))
  }
}

