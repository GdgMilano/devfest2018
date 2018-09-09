#!/usr/bin/env bash
yarn firestore:copy backup/sessions.json sessions
#yarn firestore:copy backup/schedule.json schedule
yarn firestore:copy backup/speakers.json speakers
